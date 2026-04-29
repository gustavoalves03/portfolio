import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { PublicClosedDaysStore } from './public-closed-days.store';
import { ClosedDaysService } from './closed-days.service';

describe('PublicClosedDaysStore', () => {
  let service: jasmine.SpyObj<ClosedDaysService>;
  let store: InstanceType<typeof PublicClosedDaysStore>;

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
    service.loadPublicClosedDays.and.returnValue(of(['2026-05-01']));

    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });

    expect(service.loadPublicClosedDays)
        .toHaveBeenCalledOnceWith('sophie-martin', '2026-05-01', '2026-05-31');
    expect(store.closedDays().has('2026-05-01')).toBeTrue();
  });

  it('setSlug to a different slug clears state', () => {
    service.loadPublicClosedDays.and.returnValue(of(['2026-05-01']));
    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });
    expect(store.closedDays().size).toBe(1);

    store.setSlug('camille-dubois');

    expect(store.closedDays().size).toBe(0);
  });

  it('setSlug to the same slug is a no-op (state preserved)', () => {
    service.loadPublicClosedDays.and.returnValue(of(['2026-05-01']));
    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });
    expect(store.closedDays().size).toBe(1);

    store.setSlug('sophie-martin');

    expect(store.closedDays().size).toBe(1);
  });

  it('invalidate clears closedDays without resetting slug', () => {
    service.loadPublicClosedDays.and.returnValue(of(['2026-05-01']));
    store.setSlug('sophie-martin');
    store.loadMonth({ year: 2026, month: 5 });

    store.invalidate();

    expect(store.closedDays().size).toBe(0);
    // Slug preserved → can re-fetch without re-setSlug
    service.loadPublicClosedDays.calls.reset();
    service.loadPublicClosedDays.and.returnValue(of(['2026-05-08']));
    store.loadMonth({ year: 2026, month: 5 });
    expect(service.loadPublicClosedDays)
        .toHaveBeenCalledOnceWith('sophie-martin', '2026-05-01', '2026-05-31');
    expect(store.closedDays().has('2026-05-08')).toBeTrue();
  });
});
