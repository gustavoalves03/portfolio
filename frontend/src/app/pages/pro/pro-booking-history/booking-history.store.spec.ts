import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { BookingHistoryStore } from './booking-history.store';
import { BookingsService } from '../../../features/bookings/services/bookings.service';
import { SalonClientService } from '../../../features/salon-clients/salon-client.service';
import { CareBookingStatus, CareBookingDetailed } from '../../../features/bookings/models/bookings.model';

function makeBooking(id: number): CareBookingDetailed {
  return {
    id,
    user: { id: 100 + id, name: `Client ${id}`, email: `c${id}@x.fr` } as any,
    care: { id: 1, name: 'Soin', duration: 30, price: 3000 } as any,
    quantity: 1,
    appointmentDate: '2026-04-01',
    appointmentTime: '10:00:00',
    status: CareBookingStatus.CONFIRMED,
    createdAt: '2026-03-01T10:00:00',
    employeeId: null,
    employeeName: null,
    salonClientId: null,
    salonClientName: null,
  };
}

describe('BookingHistoryStore', () => {
  let store: InstanceType<typeof BookingHistoryStore>;
  let bookingsService: jasmine.SpyObj<BookingsService>;
  let salonClientService: jasmine.SpyObj<SalonClientService>;

  beforeEach(() => {
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', ['listDetailed']);
    salonClientService = jasmine.createSpyObj<SalonClientService>('SalonClientService', ['search']);

    bookingsService.listDetailed.and.returnValue(of({
      content: [makeBooking(1), makeBooking(2)],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
      first: true,
      last: true,
      numberOfElements: 2,
      empty: false,
    } as any));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        BookingHistoryStore,
        { provide: BookingsService, useValue: bookingsService },
        { provide: SalonClientService, useValue: salonClientService },
      ],
    });
    store = TestBed.inject(BookingHistoryStore);
  });

  it('initial filters: 30 days range, all statuses, no employee, empty search', () => {
    const f = store.filters();
    expect(f.statuses.length).toBe(4);
    expect(f.employeeId).toBeNull();
    expect(f.clientQuery).toBe('');
    expect(new Date(f.from).getTime()).toBeLessThanOrEqual(new Date(f.to).getTime());
  });

  it('updateFilters resets items and page, then reloads', () => {
    store.updateFilters({ statuses: [CareBookingStatus.CANCELLED] });
    expect(store.items().length).toBe(2);
    expect(store.page()).toBe(0);
    expect(bookingsService.listDetailed).toHaveBeenCalled();
  });

  it('loadNextPage is a no-op when hasMore is false', () => {
    bookingsService.listDetailed.calls.reset();
    expect(store.hasMore()).toBeFalse();
    store.loadNextPage();
    expect(bookingsService.listDetailed).not.toHaveBeenCalled();
  });

  it('loadNextPage appends items when hasMore is true', () => {
    bookingsService.listDetailed.and.returnValue(of({
      content: [makeBooking(1), makeBooking(2)],
      totalElements: 4,
      totalPages: 2,
      number: 0,
      size: 2,
      first: true,
      last: false,
      numberOfElements: 2,
      empty: false,
    } as any));

    store.updateFilters({});
    expect(store.hasMore()).toBeTrue();

    bookingsService.listDetailed.and.returnValue(of({
      content: [makeBooking(3), makeBooking(4)],
      totalElements: 4,
      totalPages: 2,
      number: 1,
      size: 2,
      first: false,
      last: true,
      numberOfElements: 2,
      empty: false,
    } as any));

    store.loadNextPage();
    expect(store.items().length).toBe(4);
    expect(store.page()).toBe(1);
    expect(store.hasMore()).toBeFalse();
  });

  it('sends status parameter when exactly 1 status is selected', () => {
    bookingsService.listDetailed.calls.reset();
    store.updateFilters({ statuses: [CareBookingStatus.NO_SHOW] });
    const [filters] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(filters?.status).toBe(CareBookingStatus.NO_SHOW);
  });

  it('omits status parameter when 0 or all 4 statuses selected', () => {
    bookingsService.listDetailed.calls.reset();
    store.updateFilters({ statuses: [] });
    const [f1] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(f1?.status).toBeUndefined();

    store.updateFilters({
      statuses: [
        CareBookingStatus.CONFIRMED,
        CareBookingStatus.PENDING,
        CareBookingStatus.CANCELLED,
        CareBookingStatus.NO_SHOW,
      ],
    });
    const [f2] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(f2?.status).toBeUndefined();
  });

  it('omits status parameter and filters client-side when 2-3 statuses selected', () => {
    bookingsService.listDetailed.and.returnValue(of({
      content: [
        { ...makeBooking(1), status: CareBookingStatus.CONFIRMED },
        { ...makeBooking(2), status: CareBookingStatus.NO_SHOW },
        { ...makeBooking(3), status: CareBookingStatus.CANCELLED },
      ],
      totalElements: 3, totalPages: 1, number: 0, size: 20,
      first: true, last: true, numberOfElements: 3, empty: false,
    } as any));

    bookingsService.listDetailed.calls.reset();
    store.updateFilters({
      statuses: [CareBookingStatus.CONFIRMED, CareBookingStatus.NO_SHOW],
    });
    const [filters] = bookingsService.listDetailed.calls.mostRecent().args;
    expect(filters?.status).toBeUndefined();

    const items = store.items();
    expect(items.every((i: CareBookingDetailed) => i.status === CareBookingStatus.CONFIRMED || i.status === CareBookingStatus.NO_SHOW)).toBeTrue();
    expect(items.length).toBe(2);
  });
});
