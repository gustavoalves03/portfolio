import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { CaresStore } from '../../features/cares/store/cares.store';

interface CalendarDay {
  date: string;
  dayNumber: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  isPast: boolean;
  isAvailable: boolean;
}

interface CalendarMonth {
  year: number;
  month: number;
  monthName: string;
  weeks: CalendarDay[][];
}

interface BookingState {
  selectedDay?: string;
  selectedTime?: string;
  currentMonth?: Date;
}

@Component({
  selector: 'app-home',
  imports: [MatCardModule, CurrencyPipe, MatIconModule, MatButtonModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  providers: [CaresStore]
})
export class Home {
  readonly caresStore = inject(CaresStore);

  // Track current image index for each care
  currentImageIndex = new Map<number, number>();

  // Track flipped cards
  flippedCards = new Set<number>();

  // Track booking state for each care
  bookingState = new Map<number, BookingState>();

  // Cache calendars for each care
  calendars = new Map<number, CalendarMonth>();

  // Get current image index for a care
  getCurrentImageIndex(careId: number): number {
    return this.currentImageIndex.get(careId) ?? 0;
  }

  // Navigate to next image
  nextImage(careId: number, totalImages: number, event: Event): void {
    event.stopPropagation();
    const currentIndex = this.getCurrentImageIndex(careId);
    const newIndex = (currentIndex + 1) % totalImages;
    this.currentImageIndex.set(careId, newIndex);
  }

  // Navigate to previous image
  previousImage(careId: number, totalImages: number, event: Event): void {
    event.stopPropagation();
    const currentIndex = this.getCurrentImageIndex(careId);
    const newIndex = currentIndex === 0 ? totalImages - 1 : currentIndex - 1;
    this.currentImageIndex.set(careId, newIndex);
  }

  // Touch event handling for swipe
  private touchStartX = 0;
  private touchEndX = 0;

  onTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0].clientX;
  }

  onTouchEnd(event: TouchEvent, careId: number, totalImages: number): void {
    this.touchEndX = event.changedTouches[0].clientX;
    this.handleSwipe(careId, totalImages);
  }

  private handleSwipe(careId: number, totalImages: number): void {
    const swipeThreshold = 50;
    const diff = this.touchStartX - this.touchEndX;

    if (Math.abs(diff) > swipeThreshold) {
      if (diff > 0) {
        const currentIndex = this.getCurrentImageIndex(careId);
        const newIndex = (currentIndex + 1) % totalImages;
        this.currentImageIndex.set(careId, newIndex);
      } else {
        const currentIndex = this.getCurrentImageIndex(careId);
        const newIndex = currentIndex === 0 ? totalImages - 1 : currentIndex - 1;
        this.currentImageIndex.set(careId, newIndex);
      }
    }
  }

  // Booking card flip functionality
  toggleBooking(careId: number): void {
    if (this.flippedCards.has(careId)) {
      this.flippedCards.delete(careId);
      this.bookingState.delete(careId);
      this.calendars.delete(careId);
    } else {
      this.flippedCards.add(careId);
      this.bookingState.set(careId, {});
      // Generate calendar for current month
      this.updateCalendar(careId);
    }
  }

  // Update calendar for a care
  private updateCalendar(careId: number): void {
    const calendar = this.generateCalendar(careId);
    this.calendars.set(careId, calendar);
  }

  isCardFlipped(careId: number): boolean {
    return this.flippedCards.has(careId);
  }

  // Get cached calendar
  getCalendar(careId: number): CalendarMonth {
    if (!this.calendars.has(careId)) {
      this.updateCalendar(careId);
    }
    return this.calendars.get(careId)!;
  }

  // Generate calendar for a specific month
  private generateCalendar(careId: number): CalendarMonth {
    const state = this.bookingState.get(careId);
    const currentMonth = state?.currentMonth || new Date();

    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth();

    const monthNames = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
                        'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Get day of week for first day (0 = Sunday, 1 = Monday, etc.)
    const firstDayOfWeek = firstDay.getDay();
    const daysInMonth = lastDay.getDate();

    // Calculate days from previous month to show
    const daysFromPrevMonth = firstDayOfWeek === 0 ? 6 : firstDayOfWeek - 1;
    const prevMonthLastDay = new Date(year, month, 0).getDate();

    const weeks: CalendarDay[][] = [];
    let currentWeek: CalendarDay[] = [];

    // Add days from previous month
    for (let i = daysFromPrevMonth; i > 0; i--) {
      const dayNumber = prevMonthLastDay - i + 1;
      const date = new Date(year, month - 1, dayNumber);
      currentWeek.push({
        date: date.toISOString().split('T')[0],
        dayNumber,
        isCurrentMonth: false,
        isToday: false,
        isPast: true,
        isAvailable: false
      });
    }

    // Add days of current month
    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(year, month, day);
      const dateString = date.toISOString().split('T')[0];
      const isPast = date < today;
      const isToday = date.getTime() === today.getTime();

      currentWeek.push({
        date: dateString,
        dayNumber: day,
        isCurrentMonth: true,
        isToday,
        isPast,
        isAvailable: !isPast
      });

      // Start new week on Monday (currentWeek.length === 7)
      if (currentWeek.length === 7) {
        weeks.push(currentWeek);
        currentWeek = [];
      }
    }

    // Add days from next month to complete last week
    if (currentWeek.length > 0) {
      let nextMonthDay = 1;
      while (currentWeek.length < 7) {
        const date = new Date(year, month + 1, nextMonthDay);
        currentWeek.push({
          date: date.toISOString().split('T')[0],
          dayNumber: nextMonthDay,
          isCurrentMonth: false,
          isToday: false,
          isPast: false,
          isAvailable: false
        });
        nextMonthDay++;
      }
      weeks.push(currentWeek);
    }

    return {
      year,
      month,
      monthName: monthNames[month],
      weeks
    };
  }

  // Navigate to previous month
  previousMonth(careId: number): void {
    const state = this.bookingState.get(careId) || {};
    const currentMonth = state.currentMonth || new Date();
    const prevMonth = new Date(currentMonth);
    prevMonth.setMonth(prevMonth.getMonth() - 1);
    state.currentMonth = prevMonth;
    this.bookingState.set(careId, state);
    this.updateCalendar(careId);
  }

  // Navigate to next month
  nextMonth(careId: number): void {
    const state = this.bookingState.get(careId) || {};
    const currentMonth = state.currentMonth || new Date();
    const nextMonth = new Date(currentMonth);
    nextMonth.setMonth(nextMonth.getMonth() + 1);
    state.currentMonth = nextMonth;
    this.bookingState.set(careId, state);
    this.updateCalendar(careId);
  }

  // Get available time slots
  getAvailableTimeSlots(): string[] {
    return [
      '09:00', '09:30', '10:00', '10:30', '11:00', '11:30',
      '14:00', '14:30', '15:00', '15:30', '16:00', '16:30', '17:00'
    ];
  }

  // Select a day
  selectDay(careId: number, day: string): void {
    const state = this.bookingState.get(careId) || {};
    state.selectedDay = day;
    this.bookingState.set(careId, state);
  }

  // Select a time
  selectTime(careId: number, time: string): void {
    const state = this.bookingState.get(careId) || {};
    state.selectedTime = time;
    this.bookingState.set(careId, state);
  }

  // Check if day is selected
  isSelectedDay(careId: number, day: string): boolean {
    return this.bookingState.get(careId)?.selectedDay === day;
  }

  // Check if time is selected
  isSelectedTime(careId: number, time: string): boolean {
    return this.bookingState.get(careId)?.selectedTime === time;
  }

  // Check if booking can be confirmed
  canConfirmBooking(careId: number): boolean {
    const state = this.bookingState.get(careId);
    return !!(state?.selectedDay && state?.selectedTime);
  }

  // Check if a day is selected (for mobile two-step flow)
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
}
