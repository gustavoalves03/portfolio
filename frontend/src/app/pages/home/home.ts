import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { CaresStore } from '../../features/cares/store/cares.store';
import { ServiceCardComponent } from '../../shared/uis/service-card/service-card.component';
import { BookingCalendarComponent } from '../../shared/uis/booking-calendar/booking-calendar.component';
import { BookingTimesComponent } from '../../shared/uis/booking-times/booking-times.component';
import { BookingsService } from '../../features/bookings/services/bookings.service';
import { CareBookingStatus } from '../../features/bookings/models/bookings.model';
import { AuthService } from '../../core/auth/auth.service';
import { AuthModalComponent } from '../../shared/modals/auth-modal/auth-modal.component';

interface BookingState {
  selectedDay?: string;
  selectedTime?: string;
}

@Component({
  selector: 'app-home',
  imports: [
    ServiceCardComponent,
    BookingCalendarComponent,
    BookingTimesComponent,
    MatButtonModule
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  providers: [CaresStore]
})
export class Home {
  readonly caresStore = inject(CaresStore);
  private readonly bookingsService = inject(BookingsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  // Track booking state for each care
  bookingState = new Map<number, BookingState>();

  // Handle booking request from service card
  onBookingRequested(careId: number): void {
    if (!this.bookingState.has(careId)) {
      this.bookingState.set(careId, {});
    }
  }

  // Calendar day selection
  onDaySelected(careId: number, day: string): void {
    const state = this.bookingState.get(careId) || {};
    state.selectedDay = day;
    this.bookingState.set(careId, state);
  }

  // Time selection
  onTimeSelected(careId: number, time: string): void {
    const state = this.bookingState.get(careId) || {};
    state.selectedTime = time;
    this.bookingState.set(careId, state);
  }

  // Get selected day for a care
  getSelectedDay(careId: number): string | undefined {
    return this.bookingState.get(careId)?.selectedDay;
  }

  // Get selected time for a care
  getSelectedTime(careId: number): string | undefined {
    return this.bookingState.get(careId)?.selectedTime;
  }

  // Check if day is selected (for mobile two-step flow)
  hasSelectedDay(careId: number): boolean {
    return !!this.bookingState.get(careId)?.selectedDay;
  }

  // Go back to calendar selection (mobile)
  backToCalendar(careId: number): void {
    const state = this.bookingState.get(careId);
    if (state) {
      state.selectedDay = undefined;
      state.selectedTime = undefined;
      this.bookingState.set(careId, state);
    }
  }

  // Check if booking can be confirmed
  canConfirmBooking(careId: number): boolean {
    const state = this.bookingState.get(careId);
    return !!(state?.selectedDay && state?.selectedTime);
  }

  // Confirm booking
  confirmBooking(careId: number): void {
    const state = this.bookingState.get(careId);
    if (!state?.selectedDay || !state?.selectedTime) {
      return;
    }

    // Check if user is authenticated
    if (!this.authService.isAuthenticated()) {
      // Open authentication modal
      const dialogRef = this.dialog.open(AuthModalComponent, {
        width: '500px',
        disableClose: false
      });

      dialogRef.afterClosed().subscribe(() => {
        // After modal closes, check if user is now authenticated
        if (this.authService.isAuthenticated()) {
          this.createBooking(careId, state.selectedDay!, state.selectedTime!);
        }
      });
      return;
    }

    // User is authenticated, proceed with booking
    this.createBooking(careId, state.selectedDay, state.selectedTime);
  }

  private createBooking(careId: number, appointmentDate: string, appointmentTime: string): void {
    const user = this.authService.user();
    if (!user) {
      return;
    }

    const bookingRequest = {
      userId: user.id,
      careId: careId,
      quantity: 1,
      appointmentDate: appointmentDate,
      appointmentTime: appointmentTime,
      status: CareBookingStatus.PENDING
    };

    this.bookingsService.create(bookingRequest).subscribe({
      next: (booking) => {
        console.log('Booking created successfully:', booking);
        this.snackBar.open('Rendez-vous confirmé !', 'Fermer', {
          duration: 3000,
          horizontalPosition: 'center',
          verticalPosition: 'top',
        });
        // Reset booking state after confirmation
        this.bookingState.delete(careId);
      },
      error: (error) => {
        console.error('Error creating booking:', error);
        this.snackBar.open('Erreur lors de la confirmation du rendez-vous', 'Fermer', {
          duration: 5000,
          panelClass: ['snackbar-error'],
        });
      }
    });
  }
}
