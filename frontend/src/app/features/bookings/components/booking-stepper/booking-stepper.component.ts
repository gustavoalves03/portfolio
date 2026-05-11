import { Component, computed, inject, signal } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { BookingsService } from '../../services/bookings.service';
import { AuthService } from '../../../../core/auth/auth.service';
import { CaresStore } from '../../../cares/store/cares.store';
import { UsersStore } from '../../../users/store/users.store';
import { CareBookingStatus } from '../../models/bookings.model';
import { StepCareComponent } from '../step-care/step-care.component';
import { StepDatetimeComponent } from '../step-datetime/step-datetime.component';
import { StepClientComponent } from '../step-client/step-client.component';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';

@Component({
  selector: 'app-booking-stepper',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatIconModule,
    StepCareComponent,
    StepDatetimeComponent,
    StepClientComponent,
    SheetHandleComponent,
  ],
  providers: [CaresStore, UsersStore],
  template: `
    <div data-testid="booking-stepper">
    <app-sheet-handle />
    <!-- Header -->
    <div class="stepper-header">
      <button class="btn-close" data-testid="stepper-close" (click)="dialogRef.close()">
        <mat-icon>close</mat-icon>
      </button>
      <span class="stepper-title">{{ 'booking.stepper.confirm' | transloco }}</span>
      <span class="step-counter">{{ currentStep() }}/3</span>
    </div>

    <!-- Progress bar -->
    <div class="progress-bar">
      <div class="progress-fill" [style.width.%]="progressPercent()"></div>
    </div>

    <!-- Step content -->
    <div class="step-content">
      @switch (currentStep()) {
        @case (1) {
          <app-step-care (careSelected)="onCareSelected($event)" />
        }
        @case (2) {
          <app-step-datetime [careId]="selectedCareId()" (datetimeSelected)="onDatetimeSelected($event)" />
        }
        @case (3) {
          <app-step-client (clientSelected)="onClientSelected($event)" />
        }
      }
    </div>

    <!-- Back button -->
    @if (currentStep() > 1) {
      <button class="btn-back" data-testid="step-back-btn" (click)="goBack()">
        <mat-icon>arrow_back</mat-icon>
        {{ 'booking.stepper.back' | transloco }}
      </button>
    }
    </div>
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        background: var(--pf-paper);
        overflow-y: auto;
        max-height: 80vh;
      }

      @media (min-width: 768px) {
        :host {
          width: 480px;
          min-height: 560px;
          max-height: 85vh;
          overflow: hidden;
        }
        .step-content {
          flex: 1 1 auto;
          min-height: 0;
          overflow-y: auto;
        }
      }

      .stepper-header {
        display: flex;
        align-items: center;
        padding: 12px 16px;
        background: white;
        border-bottom: 1px solid #eee;
        gap: 8px;
      }

      .btn-close {
        background: none;
        border: none;
        cursor: pointer;
        padding: 4px;
        display: flex;
        color: #666;
      }

      .btn-close mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
      }

      .stepper-title {
        flex: 1;
        font-size: 15px;
        font-weight: 600;
        color: #333;
      }

      .step-counter {
        font-size: 12px;
        color: #999;
        font-weight: 500;
      }

      .progress-bar {
        height: 3px;
        background: #e0e0e0;
      }

      .progress-fill {
        height: 100%;
        background: var(--pf-rose);
        transition: width 250ms ease;
      }

      .step-content {
        flex: 1;
        padding: 16px;
        overflow-y: auto;
      }

      .btn-back {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 10px 16px;
        background: white;
        border: none;
        border-top: 1px solid #eee;
        cursor: pointer;
        font-size: 13px;
        color: #666;
        font-weight: 500;
      }

      .btn-back:hover {
        background: #f5f5f5;
      }

      .btn-back mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
    `,
  ],
})
export class BookingStepperComponent {
  readonly dialogRef = inject(MatDialogRef<BookingStepperComponent>);
  private readonly bookingsService = inject(BookingsService);
  private readonly authService = inject(AuthService);

  readonly currentStep = signal(1);
  readonly selectedCareId = signal<number | null>(null);
  readonly selectedEmployeeId = signal<number | null>(null);
  readonly selectedDate = signal<string | null>(null);
  readonly selectedTime = signal<string | null>(null);
  readonly selectedSalonClientId = signal<number | null>(null);
  readonly submitting = signal(false);

  readonly progressPercent = computed(() => (this.currentStep() / 3) * 100);

  onCareSelected(event: { careId: number; employeeId: number | null }): void {
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

    if (careId == null || appointmentDate == null || appointmentTime == null || this.submitting()) {
      return;
    }

    this.submitting.set(true);

    const userId = this.authService.user()?.id;
    if (!userId) return;

    this.bookingsService.create({
      careId,
      userId,
      quantity: 1,
      appointmentDate,
      appointmentTime: appointmentTime + ':00',
      status: CareBookingStatus.PENDING,
      salonClientId: this.selectedSalonClientId() ?? undefined,
      employeeId: this.selectedEmployeeId(),
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => {
        this.submitting.set(false);
      },
    });
  }
}
