import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { BookingStepperComponent } from './booking-stepper.component';
import { BookingsService } from '../../services/bookings.service';
import { AuthService } from '../../../../core/auth/auth.service';
import { Role, AuthProvider } from '../../../../core/auth/auth.model';
import { CaresStore } from '../../../cares/store/cares.store';
import { UsersStore } from '../../../users/store/users.store';

describe('BookingStepperComponent', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<BookingStepperComponent>>;
  let bookingsService: jasmine.SpyObj<BookingsService>;
  let authService: jasmine.SpyObj<AuthService>;
  let caresStoreStub: any;
  let usersStoreStub: any;

  function setup() {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', [
      'create',
      'getEmployeesForCare',
    ]);
    bookingsService.create.and.returnValue(of({} as any));
    bookingsService.getEmployeesForCare.and.returnValue(of([]));

    caresStoreStub = {
      availableCares: jasmine.createSpy().and.returnValue([]),
    };
    usersStoreStub = {
      users: jasmine.createSpy().and.returnValue([]),
    };

    const fakeUser = {
      id: 99,
      name: 'Pro',
      email: 'pro@x.test',
      provider: AuthProvider.LOCAL,
      role: Role.PRO,
    };
    authService = jasmine.createSpyObj('AuthService', [], {
      user: () => fakeUser,
    });

    return TestBed.configureTestingModule({
      imports: [
        BookingStepperComponent,
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
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: BookingsService, useValue: bookingsService },
        { provide: AuthService, useValue: authService },
      ],
    })
      .overrideComponent(BookingStepperComponent, {
        set: {
          providers: [
            { provide: CaresStore, useValue: caresStoreStub },
            { provide: UsersStore, useValue: usersStoreStub },
          ],
        },
      })
      .compileComponents();
  }

  it('forwards employeeId from step-care to bookingsService.create', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.onCareSelected({ careId: 1, employeeId: 42 });
    component.onDatetimeSelected({ date: '2026-06-01', time: '10:00' });
    component.onClientSelected({ salonClientId: 5 });

    expect(bookingsService.create).toHaveBeenCalledTimes(1);
    const payload = bookingsService.create.calls.mostRecent().args[0];
    expect(payload.employeeId).toBe(42);
  });

  it('forwards employeeId = null (Premier dispo) untouched', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.onCareSelected({ careId: 1, employeeId: null });
    component.onDatetimeSelected({ date: '2026-06-01', time: '10:00' });
    component.onClientSelected({ salonClientId: 5 });

    expect(bookingsService.create).toHaveBeenCalledTimes(1);
    const payload = bookingsService.create.calls.mostRecent().args[0];
    expect(payload.employeeId).toBeNull();
  });

  it('does not render the header back arrow on step 1', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    fixture.detectChanges();
    const arrow = fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]');
    expect(arrow).toBeNull();
  });

  it('renders the header back arrow on step 2 and step 3', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.currentStep.set(2);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]')).not.toBeNull();

    component.currentStep.set(3);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]')).not.toBeNull();
  });

  it('clicking the header back arrow decrements currentStep', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();
    component.currentStep.set(2);
    fixture.detectChanges();

    const arrow = fixture.nativeElement.querySelector('[data-testid="stepper-back-header"]') as HTMLElement;
    arrow.click();
    fixture.detectChanges();

    expect(component.currentStep()).toBe(1);
  });

  it('does not render the bottom Retour button on any step', async () => {
    await setup();
    const fixture = TestBed.createComponent(BookingStepperComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();

    component.currentStep.set(2);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="step-back-btn"]')).toBeNull();

    component.currentStep.set(3);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="step-back-btn"]')).toBeNull();
  });
});
