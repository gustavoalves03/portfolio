import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { of, Subject, throwError } from 'rxjs';
import { ClosedDaysStore } from './closed-days.store';
import { ClosedDaysService } from './closed-days.service';

describe('ClosedDaysStore', () => {
  let service: jasmine.SpyObj<ClosedDaysService>;
  let store: InstanceType<typeof ClosedDaysStore>;

  beforeEach(() => {
    service = jasmine.createSpyObj<ClosedDaysService>('ClosedDaysService', [
      'loadClosedDays',
      'loadPublicClosedDays',
    ]);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: ClosedDaysService, useValue: service },
      ],
    });

    store = TestBed.inject(ClosedDaysStore);
  });

  it('loadMonth fetches the month range and stores closed dates', () => {
    service.loadClosedDays.and.returnValue(of(['2026-05-01', '2026-05-09']));

    store.loadMonth({ year: 2026, month: 5 });

    expect(service.loadClosedDays).toHaveBeenCalledOnceWith('2026-05-01', '2026-05-31');
    expect(store.closedDays().has('2026-05-01')).toBeTrue();
    expect(store.closedDays().has('2026-05-09')).toBeTrue();
  });

  it('loadMonth dedupes already-loaded months (no second HTTP call)', () => {
    service.loadClosedDays.and.returnValue(of(['2026-05-01']));

    store.loadMonth({ year: 2026, month: 5 });
    store.loadMonth({ year: 2026, month: 5 });

    expect(service.loadClosedDays).toHaveBeenCalledTimes(1);
  });

  it('loadMonth supports concurrent calls without cancelling each other (mergeMap, not switchMap)', () => {
    const may = new Subject<string[]>();
    const june = new Subject<string[]>();
    service.loadClosedDays.and.callFake((from: string) => {
      if (from.startsWith('2026-05')) return may.asObservable();
      if (from.startsWith('2026-06')) return june.asObservable();
      return of([]);
    });

    store.loadMonth({ year: 2026, month: 5 });
    store.loadMonth({ year: 2026, month: 6 });

    // Now both inflight requests resolve
    june.next(['2026-06-15']);
    june.complete();
    may.next(['2026-05-01']);
    may.complete();

    expect(store.closedDays().has('2026-05-01')).toBeTrue();
    expect(store.closedDays().has('2026-06-15')).toBeTrue();
  });

  it('loadMonth pads single-digit month correctly', () => {
    service.loadClosedDays.and.returnValue(of([]));

    store.loadMonth({ year: 2026, month: 2 });

    expect(service.loadClosedDays).toHaveBeenCalledOnceWith('2026-02-01', '2026-02-28');
  });

  it('loadMonth uses the actual last day of December (31)', () => {
    service.loadClosedDays.and.returnValue(of([]));

    store.loadMonth({ year: 2026, month: 12 });

    expect(service.loadClosedDays).toHaveBeenCalledOnceWith('2026-12-01', '2026-12-31');
  });

  it('invalidate clears closedDays and loadedMonths', () => {
    service.loadClosedDays.and.returnValue(of(['2026-05-01']));
    store.loadMonth({ year: 2026, month: 5 });
    expect(store.closedDays().size).toBe(1);

    store.invalidate();

    expect(store.closedDays().size).toBe(0);
    // Subsequent loadMonth call should re-fetch
    service.loadClosedDays.calls.reset();
    service.loadClosedDays.and.returnValue(of(['2026-05-09']));
    store.loadMonth({ year: 2026, month: 5 });
    expect(service.loadClosedDays).toHaveBeenCalledTimes(1);
    expect(store.closedDays().has('2026-05-09')).toBeTrue();
  });

  it('on HTTP error, the month is rolled back so retry is possible', () => {
    service.loadClosedDays.and.returnValue(throwError(() => new Error('boom')));

    store.loadMonth({ year: 2026, month: 5 });

    // Allow retry on next call (mergeMap doesn't surface the error to the caller)
    service.loadClosedDays.and.returnValue(of(['2026-05-01']));
    store.loadMonth({ year: 2026, month: 5 });

    expect(service.loadClosedDays).toHaveBeenCalledTimes(2);
    expect(store.closedDays().has('2026-05-01')).toBeTrue();
  });
});
