import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';

import { StepCareComponent } from './step-care.component';
import { BookingsService } from '../../services/bookings.service';
import { CaresStore } from '../../../cares/store/cares.store';
import { CareStatus } from '../../../cares/models/cares.model';
import { EmployeeSlim } from '../../../salon-profile/models/salon-profile.model';

function care(id: number, name = 'Soin') {
  return {
    id,
    name,
    description: '',
    price: 5000,
    duration: 60,
    images: [],
    status: CareStatus.ACTIVE,
    categoryId: 1,
  };
}

function emp(id: number, name = 'Alice'): EmployeeSlim {
  return { id, name, imageUrl: null };
}

describe('StepCareComponent', () => {
  let bookingsService: jasmine.SpyObj<BookingsService>;
  let caresStoreStub: any;

  function setup() {
    bookingsService = jasmine.createSpyObj<BookingsService>('BookingsService', [
      'getEmployeesForCare',
    ]);
    caresStoreStub = {
      availableCares: jasmine.createSpy().and.returnValue([care(1), care(2)]),
    };

    return TestBed.configureTestingModule({
      imports: [
        StepCareComponent,
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
        { provide: BookingsService, useValue: bookingsService },
        { provide: CaresStore, useValue: caresStoreStub },
      ],
    }).compileComponents();
  }

  it('does not fetch employees until a care is selected', async () => {
    await setup();
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    expect(bookingsService.getEmployeesForCare).not.toHaveBeenCalled();
  });

  it('fetches employees after selecting a care and pre-selects "Premier dispo" when >1 employees', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(7), emp(8)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.selectCare(1);
    fixture.detectChanges();

    expect(bookingsService.getEmployeesForCare).toHaveBeenCalledWith(1);
    expect(component.availableEmployees()).toEqual([emp(7), emp(8)]);
    expect(component.selectedEmployeeId()).toBeNull(); // "Premier dispo"
  });

  it('auto-selects and hides the employee list when exactly 1 employee is returned', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(9)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    component.selectCare(1);
    fixture.detectChanges();

    expect(component.availableEmployees()).toEqual([emp(9)]);
    expect(component.selectedEmployeeId()).toBe(9);
    expect(component.shouldShowEmployeeList()).toBeFalse();
  });

  it('emits { careId, employeeId: null } when "Premier dispo" is active', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(7), emp(8)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    let emitted: any = null;
    component.careSelected.subscribe((e: any) => (emitted = e));

    component.selectCare(1);
    fixture.detectChanges();
    component.onNext();

    expect(emitted).toEqual({ careId: 1, employeeId: null });
  });

  it('emits { careId, employeeId: 8 } when the user picks employee 8', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([emp(7), emp(8)]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    let emitted: any = null;
    component.careSelected.subscribe((e: any) => (emitted = e));

    component.selectCare(1);
    fixture.detectChanges();
    component.selectEmployee(8);
    component.onNext();

    expect(emitted).toEqual({ careId: 1, employeeId: 8 });
  });

  it('resets employee selection when the care changes', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValues(
      of([emp(7), emp(8)]),
      of([emp(9), emp(10)])
    );
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.selectCare(1);
    fixture.detectChanges();
    component.selectEmployee(8);
    component.selectCare(2);
    fixture.detectChanges();

    expect(component.selectedEmployeeId()).toBeNull(); // back to "Premier dispo"
    expect(component.availableEmployees()).toEqual([emp(9), emp(10)]);
  });

  it('shows the empty message when no employees can do the care', async () => {
    await setup();
    bookingsService.getEmployeesForCare.and.returnValue(of([]));
    const fixture = TestBed.createComponent(StepCareComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance as any;

    component.selectCare(1);
    fixture.detectChanges();

    expect(component.availableEmployees()).toEqual([]);
    expect(component.shouldShowEmployeeList()).toBeTrue();
    expect(component.shouldShowEmptyState()).toBeTrue();
  });
});
