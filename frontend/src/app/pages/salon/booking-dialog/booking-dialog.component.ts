import { Component, inject, signal } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { provideNativeDateAdapter } from '@angular/material/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { AvailabilityService } from '../../../features/availability/availability.service';
import { PublicCareDto, TimeSlot, ClientBookingRequest } from '../../../features/salon-profile/models/salon-profile.model';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthModalComponent, AuthModalResult } from '../../../shared/modals/auth-modal/auth-modal.component';

export interface BookingDialogData {
  slug: string;
  care: PublicCareDto;
}

@Component({
  selector: 'app-booking-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
  ],
  providers: [provideNativeDateAdapter()],
  templateUrl: './booking-dialog.component.html',
  styleUrl: './booking-dialog.component.scss',
})
export class BookingDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<BookingDialogComponent>);
  private readonly data = inject<BookingDialogData>(MAT_DIALOG_DATA);
  private readonly salonService = inject(SalonProfileService);
  private readonly availabilityService = inject(AvailabilityService);
  private readonly authService = inject(AuthService);
  private readonly matDialog = inject(MatDialog);

  readonly care = this.data.care;
  readonly slug = this.data.slug;

  readonly minDate = new Date();
  readonly openDays = signal<Set<number>>(new Set());
  readonly selectedDate = signal<Date | null>(null);
  readonly slots = signal<TimeSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly selectedSlot = signal<TimeSlot | null>(null);
  readonly submitting = signal(false);
  readonly bookingSuccess = signal(false);
  readonly bookingError = signal<string | null>(null);
  readonly registerJustCompleted = signal(false);

  readonly dateFilter = (date: Date | null): boolean => {
    if (!date) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (date < today) return false;
    const dow = date.getDay() === 0 ? 7 : date.getDay();
    return this.openDays().has(dow);
  };

  constructor() {
    this.loadOpeningHours();
  }

  onDateChange(date: Date | null): void {
    this.selectedDate.set(date);
    this.selectedSlot.set(null);
    if (date) {
      this.loadSlots(date);
    }
  }

  selectSlot(slot: TimeSlot): void {
    this.selectedSlot.set(slot);
  }

  confirm(): void {
    const date = this.selectedDate();
    const slot = this.selectedSlot();
    if (!date || !slot) return;

    if (!this.authService.isAuthenticated()) {
      this.openAuthAndMaybeSubmit();
      return;
    }

    this.submitBooking();
  }

  close(): void {
    this.dialogRef.close();
  }

  formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' \u20AC';
  }

  private loadOpeningHours(): void {
    this.availabilityService.loadPublicHours(this.slug).subscribe({
      next: (hours) => {
        const days = new Set(hours.map((h) => h.dayOfWeek));
        this.openDays.set(days);
      },
      error: () => {
        this.openDays.set(new Set([1, 2, 3, 4, 5, 6, 7]));
      },
    });
  }

  private loadSlots(date: Date): void {
    this.loadingSlots.set(true);
    this.slots.set([]);

    this.salonService.getAvailableSlots(this.slug, this.care.id, this.formatDate(date)).subscribe({
      next: (slots) => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
      },
      error: () => {
        this.slots.set([]);
        this.loadingSlots.set(false);
      },
    });
  }

  private openAuthAndMaybeSubmit(): void {
    const authRef = this.matDialog.open(AuthModalComponent, { width: '480px' });
    authRef.afterClosed().subscribe((result: AuthModalResult) => {
      if (!result?.authenticated) return;
      if (result.action === 'login') {
        this.submitBooking();
      } else {
        this.registerJustCompleted.set(true);
      }
    });
  }

  private submitBooking(): void {
    this.submitting.set(true);
    this.bookingError.set(null);
    this.registerJustCompleted.set(false);

    const request: ClientBookingRequest = {
      careId: this.care.id,
      appointmentDate: this.formatDate(this.selectedDate()!),
      appointmentTime: this.selectedSlot()!.startTime,
    };

    this.salonService.createBooking(this.slug, request).subscribe({
      next: () => {
        this.submitting.set(false);
        this.bookingSuccess.set(true);
      },
      error: (err) => {
        this.submitting.set(false);
        if (err.status === 409) {
          this.bookingError.set('booking.errors.slotTaken');
          this.loadSlots(this.selectedDate()!);
          this.selectedSlot.set(null);
        } else {
          this.bookingError.set('booking.errors.generic');
        }
      },
    });
  }

  private formatDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
