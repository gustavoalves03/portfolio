import { Component, computed, inject, signal } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { BookingsStore } from '../../store/bookings.store';
import { CaresStore } from '../../../cares/store/cares.store';
import { UsersStore } from '../../../users/store/users.store';
import { CareBookingStatus } from '../../models/bookings.model';
import { StepCareComponent } from '../step-care/step-care.component';
import { StepDatetimeComponent } from '../step-datetime/step-datetime.component';
import { StepClientComponent } from '../step-client/step-client.component';

@Component({
  selector: 'app-booking-stepper',
  standalone: true,
  imports: [TranslocoPipe, StepCareComponent, StepDatetimeComponent, StepClientComponent],
  providers: [BookingsStore, CaresStore, UsersStore],
  template: `
    <!-- Progress indicator -->
    <div class="stepper-progress">
      @for (step of steps; track step.number) {
        <div class="step-indicator" [class.active]="currentStep() === step.number"
             [class.completed]="currentStep() > step.number">
          <div class="step-circle">{{ step.number }}</div>
          <span class="step-label">{{ step.labelKey | transloco }}</span>
        </div>
        @if (!$last) {
          <div class="step-connector" [class.completed]="currentStep() > step.number"></div>
        }
      }
    </div>

    <!-- Step content -->
    <div class="step-content">
      @switch (currentStep()) {
        @case (1) {
          <app-step-care (careSelected)="onCareSelected($event)" />
        }
        @case (2) {
          <app-step-datetime (datetimeSelected)="onDatetimeSelected($event)" />
        }
        @case (3) {
          <app-step-client (clientSelected)="onClientSelected($event)" />
        }
      }
    </div>

    <!-- Navigation buttons -->
    <div class="stepper-actions">
      @if (currentStep() > 1) {
        <button class="btn btn-back" (click)="goBack()">
          {{ 'booking.stepper.back' | transloco }}
        </button>
      }
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        background: #f5f4f2;
        padding: 24px;
        min-width: 600px;
        min-height: 400px;
      }

      .stepper-progress {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        margin-bottom: 32px;
      }

      .step-indicator {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 6px;
      }

      .step-circle {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 600;
        font-size: 14px;
        background: #e0e0e0;
        color: #888;
        transition: background 200ms ease, color 200ms ease;
      }

      .step-indicator.active .step-circle,
      .step-indicator.completed .step-circle {
        background: #c06;
        color: white;
      }

      .step-label {
        font-size: 12px;
        color: #888;
        white-space: nowrap;
        transition: color 200ms ease;
      }

      .step-indicator.active .step-label {
        color: #c06;
        font-weight: 600;
      }

      .step-indicator.completed .step-label {
        color: #333;
      }

      .step-connector {
        width: 48px;
        height: 2px;
        background: #e0e0e0;
        margin-bottom: 20px;
        transition: background 200ms ease;
      }

      .step-connector.completed {
        background: #c06;
      }

      .step-content {
        flex: 1;
        min-height: 250px;
      }

      .stepper-actions {
        display: flex;
        justify-content: flex-start;
        padding-top: 16px;
        border-top: 1px solid #e0e0e0;
        margin-top: 16px;
      }

      .btn {
        padding: 8px 20px;
        border-radius: 8px;
        border: none;
        cursor: pointer;
        font-size: 14px;
        font-weight: 500;
        transition: background 200ms ease;
      }

      .btn-back {
        background: #e0e0e0;
        color: #333;
      }

      .btn-back:hover {
        background: #d0d0d0;
      }
    `,
  ],
})
export class BookingStepperComponent {
  private readonly dialogRef = inject(MatDialogRef<BookingStepperComponent>);
  private readonly bookingsStore = inject(BookingsStore);

  readonly currentStep = signal(1);
  readonly selectedCareId = signal<number | null>(null);
  readonly selectedEmployeeId = signal<number | null>(null);
  readonly selectedDate = signal<string | null>(null);
  readonly selectedTime = signal<string | null>(null);
  readonly selectedSalonClientId = signal<number | null>(null);

  readonly steps = [
    { number: 1, labelKey: 'booking.stepper.step1' },
    { number: 2, labelKey: 'booking.stepper.step2' },
    { number: 3, labelKey: 'booking.stepper.step3' },
  ];

  readonly canGoBack = computed(() => this.currentStep() > 1);

  onCareSelected(event: { careId: number; employeeId: number }): void {
    this.selectedCareId.set(event.careId);
    this.selectedEmployeeId.set(event.employeeId);
    this.currentStep.set(2);
  }

  onDatetimeSelected(event: { date: string; time: string }): void {
    this.selectedDate.set(event.date);
    this.selectedTime.set(event.time);
    this.currentStep.set(3);
  }

  onClientSelected(event: { salonClientId: number }): void {
    this.selectedSalonClientId.set(event.salonClientId);
    this.confirmBooking();
  }

  goBack(): void {
    if (this.currentStep() > 1) {
      this.currentStep.update((step) => step - 1);
    }
  }

  private confirmBooking(): void {
    const careId = this.selectedCareId();
    const appointmentDate = this.selectedDate();
    const appointmentTime = this.selectedTime();

    if (careId == null || appointmentDate == null || appointmentTime == null) {
      return;
    }

    this.bookingsStore.createBooking({
      careId,
      userId: this.selectedEmployeeId() ?? 0,
      quantity: 1,
      appointmentDate,
      appointmentTime,
      status: CareBookingStatus.PENDING,
    });

    this.dialogRef.close({
      careId,
      employeeId: this.selectedEmployeeId(),
      appointmentDate,
      appointmentTime,
      salonClientId: this.selectedSalonClientId(),
    });
  }
}
