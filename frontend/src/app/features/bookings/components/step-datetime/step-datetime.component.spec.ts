import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { StepDatetimeComponent } from './step-datetime.component';
import { ClosedDay, ClosedDaysService } from '../../../availability/closed-days.service';
import { BookingsService } from '../../services/bookings.service';

describe('StepDatetimeComponent', () => {
  let closedDaysService: jasmine.SpyObj<ClosedDaysService>;
  let bookingsService: jasmine.SpyObj<BookingsService>;

  function setup(closedDays: ClosedDay[]): StepDatetimeComponent {
    closedDaysService = jasmine.createSpyObj<ClosedDaysService>('ClosedDaysService', [
      'loadClosedDays',
      'loadPublicClosedDays',
    ]);
    closedDaysService.loadClosedDays.and.returnValue(of(closedDays));

    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', ['getAvailableSlots']);

    TestBed.configureTestingModule({
      imports: [
        StepDatetimeComponent,
        TranslocoTestingModule.forRoot({ langs: { fr: {} }, translocoConfig: { defaultLang: 'fr' } }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: ClosedDaysService, useValue: closedDaysService },
        { provide: BookingsService, useValue: bookingsService },
      ],
    });

    const fixture = TestBed.createComponent(StepDatetimeComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('dateFilter rejects null', () => {
    const cmp = setup([]);
    expect(cmp.dateFilter(null)).toBeFalse();
  });

  it('dateFilter rejects past dates', () => {
    const cmp = setup([]);
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    expect(cmp.dateFilter(yesterday)).toBeFalse();
  });

  it('dateFilter rejects dates listed by the closed-days store', () => {
    const cmp = setup([{ date: '2026-05-01', reason: 'HOLIDAY' }]);
    expect(cmp.dateFilter(new Date(2026, 4, 1))).toBeFalse();
  });

  it('dateFilter accepts future dates not in the closed list', () => {
    const cmp = setup([{ date: '2026-05-01', reason: 'HOLIDAY' }]);
    // Use a date relative to "today" so the test doesn't decay as the
    // hardcoded 2026-05-02 passes into history.
    const future = new Date();
    future.setDate(future.getDate() + 30);
    expect(cmp.dateFilter(future)).toBeTrue();
  });

  it('dateClass returns "closed-holiday" for HOLIDAY dates', () => {
    const cmp = setup([{ date: '2026-05-01', reason: 'HOLIDAY' }]);
    expect(cmp.dateClass(new Date(2026, 4, 1))).toBe('closed-holiday');
  });

  it('dateClass returns empty string for non-HOLIDAY closed dates', () => {
    const cmp = setup([{ date: '2026-05-03', reason: 'WEEKLY_CLOSED' }]);
    expect(cmp.dateClass(new Date(2026, 4, 3))).toBe('');
  });

  it('dateClass returns empty string for open dates', () => {
    const cmp = setup([]);
    expect(cmp.dateClass(new Date(2026, 4, 2))).toBe('');
  });

  it('triggers preload of 6 months on construction', () => {
    setup([]);
    expect(closedDaysService.loadClosedDays).toHaveBeenCalledTimes(6);
  });
});
