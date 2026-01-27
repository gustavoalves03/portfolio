import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { CaresStore } from '../../features/cares/store/cares.store';
import { ServiceCardComponent } from '../../shared/uis/service-card/service-card.component';
import { BookingCalendarComponent } from '../../shared/uis/booking-calendar/booking-calendar.component';
import { BookingTimesComponent } from '../../shared/uis/booking-times/booking-times.component';

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
    if (state?.selectedDay && state?.selectedTime) {
      console.log('Booking confirmed:', { careId, ...state });
      // TODO: Implement actual booking logic
    }
  }
}
