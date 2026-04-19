import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ClientBookingsStore } from './client-bookings.store';
import { BookingsService } from '../../../bookings/services/bookings.service';
import {
  CareBookingDetailed,
  CareBookingStatus,
} from '../../../bookings/models/bookings.model';

function todayStr(): string {
  return new Date().toISOString().slice(0, 10);
}

function makeBooking(
  id: number,
  appointmentDate: string,
  status: CareBookingStatus = CareBookingStatus.CONFIRMED,
  appointmentTime = '10:00:00'
): CareBookingDetailed {
  return {
    id,
    user: { id: 42, name: 'Claire', email: 'claire@test.fr' },
    care: { id: 1, name: 'Soin visage', duration: 30, price: 5500 },
    quantity: 1,
    appointmentDate,
    appointmentTime,
    status,
    createdAt: '2026-01-01T10:00:00',
    employeeId: null,
    employeeName: null,
    salonClientId: null,
    salonClientName: null,
  };
}

describe('ClientBookingsStore (tracking)', () => {
  let store: InstanceType<typeof ClientBookingsStore>;
  let bookingsService: jasmine.SpyObj<BookingsService>;

  const today = todayStr();
  const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10);
  const yesterday = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);

  beforeEach(() => {
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', [
      'listDetailed',
      'update',
    ]);

    bookingsService.listDetailed.and.returnValue(
      of({
        content: [
          makeBooking(1, today, CareBookingStatus.CONFIRMED, '11:00:00'),
          makeBooking(2, tomorrow, CareBookingStatus.PENDING),
          makeBooking(3, yesterday, CareBookingStatus.CONFIRMED),
          makeBooking(4, yesterday, CareBookingStatus.NO_SHOW, '09:00:00'),
        ],
        totalElements: 4,
        totalPages: 1,
        number: 0,
        size: 100,
        first: true,
        last: true,
        numberOfElements: 4,
        empty: false,
      } as any)
    );

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ClientBookingsStore,
        { provide: BookingsService, useValue: bookingsService },
      ],
    });
    store = TestBed.inject(ClientBookingsStore);
  });

  it('starts with upcoming tab active and empty bookings', () => {
    expect(store.activeTab()).toBe('upcoming');
    expect(store.bookings().length).toBe(0);
    expect(store.upcomingCount()).toBe(0);
    expect(store.pastCount()).toBe(0);
  });

  it('loadBookings partitions into today / upcoming / past buckets', () => {
    store.loadBookings(42);

    expect(bookingsService.listDetailed).toHaveBeenCalledWith(
      { userId: 42 },
      { size: 100, sort: 'appointmentDate,desc' }
    );
    expect(store.todayBookings().length).toBe(1);
    expect(store.todayBookings()[0].id).toBe(1);
    expect(store.upcomingBookings().length).toBe(1);
    expect(store.upcomingBookings()[0].id).toBe(2);
    expect(store.pastBookings().length).toBe(2);
    // upcomingCount = today + future
    expect(store.upcomingCount()).toBe(2);
    expect(store.pastCount()).toBe(2);
  });

  it('setActiveTab switches between upcoming and past', () => {
    store.setActiveTab('past');
    expect(store.activeTab()).toBe('past');
    store.setActiveTab('upcoming');
    expect(store.activeTab()).toBe('upcoming');
  });

  it('markNoShow optimistically flips status then calls update', () => {
    bookingsService.update.and.returnValue(of({} as any));
    store.loadBookings(42);
    const target = store.bookings().find((b) => b.id === 3)!;

    store.markNoShow(target);

    // Optimistic update applied immediately (before update resolves).
    const afterOptimistic = store.bookings().find((b) => b.id === 3)!;
    expect(afterOptimistic.status).toBe(CareBookingStatus.NO_SHOW);
    expect(bookingsService.update).toHaveBeenCalledWith(
      3,
      jasmine.objectContaining({
        status: CareBookingStatus.NO_SHOW,
        careId: 1,
        userId: 42,
      })
    );
  });

  it('markNoShow rolls back to CONFIRMED when the backend update fails', () => {
    store.loadBookings(42);
    bookingsService.update.and.returnValue(
      throwError(() => new Error('boom'))
    );
    const target = store.bookings().find((b) => b.id === 3)!;

    store.markNoShow(target);

    // After rollback, the status is back to CONFIRMED and error was recorded.
    const rolledBack = store.bookings().find((b) => b.id === 3)!;
    expect(rolledBack.status).toBe(CareBookingStatus.CONFIRMED);
  });

  it('past bookings are sorted most-recent first', () => {
    const past1 = new Date(Date.now() - 2 * 86_400_000)
      .toISOString()
      .slice(0, 10);
    const past2 = new Date(Date.now() - 5 * 86_400_000)
      .toISOString()
      .slice(0, 10);

    bookingsService.listDetailed.and.returnValue(
      of({
        content: [
          makeBooking(10, past2, CareBookingStatus.CONFIRMED, '09:00:00'),
          makeBooking(11, past1, CareBookingStatus.CONFIRMED, '10:00:00'),
        ],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 100,
        first: true,
        last: true,
        numberOfElements: 2,
        empty: false,
      } as any)
    );

    store.loadBookings(42);
    const past = store.pastBookings();
    expect(past.length).toBe(2);
    expect(past[0].id).toBe(11); // most recent past first
    expect(past[1].id).toBe(10);
  });
});
