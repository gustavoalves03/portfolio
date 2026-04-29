import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { BookingDialogComponent, BookingDialogData } from './booking-dialog.component';
import { ClosedDay, ClosedDaysService } from '../../../features/availability/closed-days.service';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { AuthService } from '../../../core/auth/auth.service';
import { PublicCareDto } from '../../../features/salon-profile/models/salon-profile.model';

describe('BookingDialogComponent', () => {
  let closedDaysService: jasmine.SpyObj<ClosedDaysService>;
  let salonService: jasmine.SpyObj<SalonProfileService>;

  const care: PublicCareDto = {
    id: 1,
    name: 'Soin visage',
    description: '',
    duration: 60,
    price: 5000,
    imageUrls: [],
  };

  function setup(closedDays: ClosedDay[]): BookingDialogComponent {
    closedDaysService = jasmine.createSpyObj<ClosedDaysService>('ClosedDaysService', [
      'loadClosedDays',
      'loadPublicClosedDays',
    ]);
    closedDaysService.loadPublicClosedDays.and.returnValue(of(closedDays));

    salonService = jasmine.createSpyObj<SalonProfileService>('SalonProfileService', [
      'getEmployeesForCare',
      'getAvailableSlots',
      'createBooking',
    ]);
    salonService.getEmployeesForCare.and.returnValue(of([]));

    const auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated']);

    const data: BookingDialogData = { slug: 'sophie-martin', care };

    TestBed.configureTestingModule({
      imports: [
        BookingDialogComponent,
        TranslocoTestingModule.forRoot({ langs: { fr: {} }, translocoConfig: { defaultLang: 'fr' } }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close: () => undefined } },
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(null) }) } },
        { provide: ClosedDaysService, useValue: closedDaysService },
        { provide: SalonProfileService, useValue: salonService },
        { provide: AuthService, useValue: auth },
      ],
    });

    const fixture = TestBed.createComponent(BookingDialogComponent);
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

  it('dateFilter rejects 1er mai when listed as closed by the public store', () => {
    const cmp = setup([{ date: '2026-05-01', reason: 'HOLIDAY' }]);
    expect(cmp.dateFilter(new Date(2026, 4, 1))).toBeFalse();
  });

  it('dateFilter accepts a future date that is not in the closed list', () => {
    const cmp = setup([{ date: '2026-05-01', reason: 'HOLIDAY' }]);
    expect(cmp.dateFilter(new Date(2026, 4, 2))).toBeTrue();
  });

  it('dateClass returns "closed-holiday" for HOLIDAY dates', () => {
    const cmp = setup([{ date: '2026-05-01', reason: 'HOLIDAY' }]);
    expect(cmp.dateClass(new Date(2026, 4, 1))).toBe('closed-holiday');
  });

  it('dateClass returns empty string for non-HOLIDAY closed dates', () => {
    const cmp = setup([{ date: '2026-05-03', reason: 'WEEKLY_CLOSED' }]);
    expect(cmp.dateClass(new Date(2026, 4, 3))).toBe('');
  });

  it('preloads 6 months from the public closed-days endpoint with the salon slug', () => {
    setup([]);
    expect(closedDaysService.loadPublicClosedDays).toHaveBeenCalledTimes(6);
    const firstCall = closedDaysService.loadPublicClosedDays.calls.first().args;
    expect(firstCall[0]).toBe('sophie-martin');
  });
});
