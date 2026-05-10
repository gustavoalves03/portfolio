import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { BookingPolicyService } from './booking-policy.service';
import { BookingPolicyStore } from './booking-policy.store';

describe('BookingPolicyStore', () => {
  let store: InstanceType<typeof BookingPolicyStore>;
  let service: jasmine.SpyObj<BookingPolicyService>;

  beforeEach(() => {
    service = jasmine.createSpyObj<BookingPolicyService>('BookingPolicyService', ['getCurrent', 'update']);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: BookingPolicyService, useValue: service },
        BookingPolicyStore,
      ],
    });
    store = TestBed.inject(BookingPolicyStore);
  });

  it('load() puts policy into state and marks fulfilled', () => {
    service.getCurrent.and.returnValue(
      of({
        maxBookingsPerDayPerClient: 2,
        maxBookingsPerWeekForNewClient: 1,
        updatedAt: '2026-05-11T10:00:00',
      }),
    );
    store.load();
    expect(store.policy()).toEqual(jasmine.objectContaining({ maxBookingsPerDayPerClient: 2 }));
    expect(store.isFulfilled()).toBeTrue();
  });

  it('update() patches state with the response', () => {
    service.update.and.returnValue(
      of({
        maxBookingsPerDayPerClient: 4,
        maxBookingsPerWeekForNewClient: 3,
        updatedAt: '2026-05-11T10:00:00',
      }),
    );
    store.update({ maxBookingsPerDayPerClient: 4, maxBookingsPerWeekForNewClient: 3 });
    expect(store.policy()?.maxBookingsPerDayPerClient).toBe(4);
    expect(store.policy()?.maxBookingsPerWeekForNewClient).toBe(3);
  });

  it('load() error sets error and leaves policy null', () => {
    service.getCurrent.and.returnValue(throwError(() => new Error('boom')));
    store.load();
    expect(store.policy()).toBeNull();
    expect(store.error()).toBeTruthy();
  });

  it('update() error preserves previous policy', () => {
    service.getCurrent.and.returnValue(
      of({
        maxBookingsPerDayPerClient: 1,
        maxBookingsPerWeekForNewClient: 1,
        updatedAt: 'x',
      }),
    );
    store.load();
    service.update.and.returnValue(throwError(() => new Error('nope')));
    store.update({ maxBookingsPerDayPerClient: 5, maxBookingsPerWeekForNewClient: 5 });
    expect(store.policy()?.maxBookingsPerDayPerClient).toBe(1);
    expect(store.error()).toBeTruthy();
  });
});
