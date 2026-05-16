import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';

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
    // Use a date relative to "today" so the test doesn't decay as 2026-05-02
    // passes into history.
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

  it('preloads 6 months from the public closed-days endpoint with the salon slug', () => {
    setup([]);
    expect(closedDaysService.loadPublicClosedDays).toHaveBeenCalledTimes(6);
    const firstCall = closedDaysService.loadPublicClosedDays.calls.first().args;
    expect(firstCall[0]).toBe('sophie-martin');
  });

  describe('409 typed booking-policy errors', () => {
    let component: BookingDialogComponent;
    let salonSpy: jasmine.SpyObj<SalonProfileService>;
    let snackOpen: jasmine.Spy;
    const selectedSlot = { startTime: '10:00', endTime: '11:00' };

    beforeEach(() => {
      const closedDaysSvc = jasmine.createSpyObj<ClosedDaysService>('ClosedDaysService', [
        'loadPublicClosedDays',
        'loadClosedDays',
      ]);
      closedDaysSvc.loadPublicClosedDays.and.returnValue(of([]));

      salonSpy = jasmine.createSpyObj<SalonProfileService>('SalonProfileService', [
        'getEmployeesForCare',
        'getAvailableSlots',
        'createBooking',
      ]);
      salonSpy.getEmployeesForCare.and.returnValue(of([]));
      salonSpy.getAvailableSlots.and.returnValue(of([selectedSlot]));

      const auth = jasmine.createSpyObj<AuthService>(
        'AuthService',
        ['isAuthenticated', 'isClientMode'],
      );
      auth.isAuthenticated.and.returnValue(true);
      auth.isClientMode.and.returnValue(true);

      const data: BookingDialogData = { slug: 'test-salon', care };

      TestBed.configureTestingModule({
        imports: [
          BookingDialogComponent,
          TranslocoTestingModule.forRoot({
            langs: { fr: {} },
            translocoConfig: { defaultLang: 'fr' },
          }),
        ],
        providers: [
          provideZonelessChangeDetection(),
          provideHttpClient(),
          provideHttpClientTesting(),
          provideNoopAnimations(),
          { provide: MatDialogRef, useValue: { close: () => undefined } },
          { provide: MAT_DIALOG_DATA, useValue: data },
          { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(null) }) } },
          { provide: ClosedDaysService, useValue: closedDaysSvc },
          { provide: SalonProfileService, useValue: salonSpy },
          { provide: AuthService, useValue: auth },
        ],
      });

      const fixture = TestBed.createComponent(BookingDialogComponent);
      fixture.detectChanges();
      component = fixture.componentInstance;

      // The component injects MatSnackBar from its own environment injector (standalone
      // component with MatSnackBarModule in imports). Spy on the actual private instance.
      snackOpen = spyOn((component as any).snackBar, 'open');

      // Set up component state so confirm() can proceed to submitBooking()
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 1);
      component.selectedDate.set(futureDate);
      component.selectedSlot.set(selectedSlot);
    });

    it('shows daily-limit snackbar on 409 BOOKING_LIMIT_DAILY_EXCEEDED', () => {
      salonSpy.createBooking.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 409,
          error: { code: 'BOOKING_LIMIT_DAILY_EXCEEDED', message: 'daily limit', limit: 1, currentCount: 1 },
        })),
      );

      component.confirm();

      expect(snackOpen).toHaveBeenCalledWith(
        // TranslocoTestingModule with empty langs returns the key (possibly lang-prefixed).
        jasmine.stringMatching(/errors\.booking\.limitDaily/),
        undefined,
        jasmine.objectContaining({ duration: 5000 }),
      );
    });

    it('shows new-client weekly-limit snackbar on 409 BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED', () => {
      salonSpy.createBooking.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 409,
          error: { code: 'BOOKING_LIMIT_NEW_CLIENT_WEEKLY_EXCEEDED', message: 'weekly limit', limit: 1, currentCount: 1 },
        })),
      );

      component.confirm();

      expect(snackOpen).toHaveBeenCalledWith(
        // TranslocoTestingModule with empty langs returns the key (possibly lang-prefixed).
        jasmine.stringMatching(/errors\.booking\.limitNewClientWeekly/),
        undefined,
        jasmine.objectContaining({ duration: 5000 }),
      );
    });

    it('preserves selectedSlot after a policy-limit 409', () => {
      salonSpy.createBooking.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 409,
          error: { code: 'BOOKING_LIMIT_DAILY_EXCEEDED', message: 'daily limit', limit: 1, currentCount: 1 },
        })),
      );

      component.confirm();

      expect(component.selectedSlot()).toEqual(selectedSlot);
    });

    it('falls back to inline bookingError for 409 with unknown code', () => {
      salonSpy.createBooking.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 409,
          error: { code: 'SLOT_ALREADY_TAKEN' },
        })),
      );

      component.confirm();

      expect(snackOpen).not.toHaveBeenCalled();
      expect(component.bookingError()).toBe('booking.errors.generic');
    });

    it('falls back to inline bookingError for 5xx errors', () => {
      salonSpy.createBooking.and.returnValue(
        throwError(() => new HttpErrorResponse({
          status: 500,
          error: { error: 'Internal Server Error' },
        })),
      );

      component.confirm();

      expect(snackOpen).not.toHaveBeenCalled();
      expect(component.bookingError()).toBe('Internal Server Error');
    });
  });

  describe('client-mode guard (regression)', () => {
    // A PRO/EMPLOYEE/ADMIN currently in their tenant context
    // (isClientMode() === false) must NOT be able to create a client booking.
    // The button should be unreachable in the UI; even if confirm() is invoked
    // programmatically, it must not call salonService.createBooking().
    function setupWithMode(isClientMode: boolean): {
      cmp: BookingDialogComponent;
      salonSpy: jasmine.SpyObj<SalonProfileService>;
    } {
      const closedDaysSvc = jasmine.createSpyObj<ClosedDaysService>('ClosedDaysService', [
        'loadPublicClosedDays',
        'loadClosedDays',
      ]);
      closedDaysSvc.loadPublicClosedDays.and.returnValue(of([]));

      const salonSpy = jasmine.createSpyObj<SalonProfileService>('SalonProfileService', [
        'getEmployeesForCare',
        'getAvailableSlots',
        'createBooking',
      ]);
      salonSpy.getEmployeesForCare.and.returnValue(of([]));
      salonSpy.createBooking.and.returnValue(of({
        bookingId: 1,
        careName: 'Soin',
        carePrice: 5000,
        careDuration: 60,
        appointmentDate: '2026-05-20',
        appointmentTime: '10:00',
        status: 'PENDING',
        salonName: 'Salon A',
      }));

      const auth = jasmine.createSpyObj<AuthService>(
        'AuthService',
        ['isAuthenticated', 'isClientMode'],
      );
      auth.isAuthenticated.and.returnValue(true);
      auth.isClientMode.and.returnValue(isClientMode);

      const data: BookingDialogData = { slug: 'sophie-martin', care };

      TestBed.configureTestingModule({
        imports: [
          BookingDialogComponent,
          TranslocoTestingModule.forRoot({
            langs: { fr: {} },
            translocoConfig: { defaultLang: 'fr' },
          }),
        ],
        providers: [
          provideZonelessChangeDetection(),
          provideHttpClient(),
          provideHttpClientTesting(),
          provideNoopAnimations(),
          { provide: MatDialogRef, useValue: { close: () => undefined } },
          { provide: MAT_DIALOG_DATA, useValue: data },
          { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(null) }) } },
          { provide: ClosedDaysService, useValue: closedDaysSvc },
          { provide: SalonProfileService, useValue: salonSpy },
          { provide: AuthService, useValue: auth },
        ],
      });

      const fixture = TestBed.createComponent(BookingDialogComponent);
      fixture.detectChanges();
      const cmp = fixture.componentInstance;

      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 1);
      cmp.selectedDate.set(futureDate);
      cmp.selectedSlot.set({ startTime: '10:00', endTime: '11:00' });
      return { cmp, salonSpy };
    }

    it('does NOT call createBooking when the user is in PRO mode (isClientMode=false)', () => {
      const { cmp, salonSpy } = setupWithMode(false);
      cmp.confirm();
      expect(salonSpy.createBooking).not.toHaveBeenCalled();
    });

    it('DOES call createBooking when the user is in client mode (isClientMode=true)', () => {
      const { cmp, salonSpy } = setupWithMode(true);
      cmp.confirm();
      expect(salonSpy.createBooking).toHaveBeenCalledTimes(1);
    });
  });
});
