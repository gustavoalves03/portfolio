import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { PublicClosedDaysStore } from './public-closed-days.store';
import { ClosedDay, ClosedDaysService } from './closed-days.service';

describe('PublicClosedDaysStore', () => {
  let service: jasmine.SpyObj<ClosedDaysService>;
  let store: InstanceType<typeof PublicClosedDaysStore>;

  const day = (date: string, reason: ClosedDay['reason'] = 'WEEKLY_CLOSED'): ClosedDay => ({
    date,
    reason,
  });

  beforeEach(() => {
    service = jasmine.createSpyObj<ClosedDaysService>('ClosedDaysService', [
      'loadClosedDays',
      'loadPublicClosedDays',
    ]);
    service.loadPublicClosedDays.and.returnValue(of([]));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        PublicClosedDaysStore,
        { provide: ClosedDaysService, useValue: service },
      ],
    });

    store = TestBed.inject(PublicClosedDaysStore);
  });

  it('loadMonth is a no-op until setSlug is called', () => {
    store.loadMonth({ year: 2026, month: 5 });
    expect(service.loadPublicClosedDays).not.toHaveBeenCalled();
  });

  it('setSlug followed by loadMonth fetches public closed-days for that slug', () => {
    service.loadPublicClosedDays.and.returnValue(of([day('2026-05-01', 'HOLIDAY')]));

    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });

    expect(service.loadPublicClosedDays)
        .toHaveBeenCalledOnceWith('sophie-martin', '2026-05-01', '2026-05-31');
    expect(store.closedDays().has('2026-05-01')).toBeTrue();
    expect(store.holidayDays().has('2026-05-01')).toBeTrue();
  });

  it('setSlug to a different slug clears state', () => {
    service.loadPublicClosedDays.and.returnValue(of([day('2026-05-01')]));
    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });
    expect(store.closedDays().size).toBe(1);

    store.setSlug('camille-dubois');

    expect(store.closedDays().size).toBe(0);
    expect(store.holidayDays().size).toBe(0);
  });

  it('setSlug to the same slug is a no-op (state preserved)', () => {
    service.loadPublicClosedDays.and.returnValue(of([day('2026-05-01')]));
    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });
    expect(store.closedDays().size).toBe(1);

    store.setSlug('sophie-martin');

    expect(store.closedDays().size).toBe(1);
  });

  it('invalidate clears closedDays without resetting slug', () => {
    service.loadPublicClosedDays.and.returnValue(of([day('2026-05-01', 'HOLIDAY')]));
    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });

    store.invalidate();

    expect(store.closedDays().size).toBe(0);
    expect(store.holidayDays().size).toBe(0);
    service.loadPublicClosedDays.calls.reset();
    service.loadPublicClosedDays.and.returnValue(of([day('2026-05-08')]));
    store.loadMonth({ year: 2026, month: 5 });
    expect(service.loadPublicClosedDays)
        .toHaveBeenCalledOnceWith('sophie-martin', '2026-05-01', '2026-05-31');
    expect(store.closedDays().has('2026-05-08')).toBeTrue();
  });
});
