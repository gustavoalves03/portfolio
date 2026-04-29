import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { StepDatetimeComponent } from './step-datetime.component';
import { ClosedDaysService } from '../../../availability/closed-days.service';
import { BookingsService } from '../../services/bookings.service';

describe('StepDatetimeComponent', () => {
  let closedDaysService: jasmine.SpyObj<ClosedDaysService>;
  let bookingsService: jasmine.SpyObj<BookingsService>;

  function setup(closedDates: string[]): StepDatetimeComponent {
    closedDaysService = jasmine.createSpyObj<ClosedDaysService>('ClosedDaysService', [
      'loadClosedDays',
      'loadPublicClosedDays',
    ]);
    closedDaysService.loadClosedDays.and.returnValue(of(closedDates));

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
    const cmp = setup(['2026-05-01']);
    expect(cmp.dateFilter(new Date(2026, 4, 1))).toBeFalse();
  });

  it('dateFilter accepts future dates not in the closed list', () => {
    const cmp = setup(['2026-05-01']);
    expect(cmp.dateFilter(new Date(2026, 4, 2))).toBeTrue();
  });

  it('triggers preload of 6 months on construction', () => {
    setup([]);
    expect(closedDaysService.loadClosedDays).toHaveBeenCalledTimes(6);
  });
});
