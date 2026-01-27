import { Component, input, output, signal, computed, effect } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export interface CalendarDay {
  date: string;
  dayNumber: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  isPast: boolean;
  isAvailable: boolean;
}

export interface CalendarMonth {
  year: number;
  month: number;
  monthName: string;
  weeks: CalendarDay[][];
}

@Component({
  selector: 'app-booking-calendar',
  imports: [MatIconModule],
  templateUrl: './booking-calendar.component.html',
  standalone: true,
  styleUrl: './booking-calendar.component.scss'
})
export class BookingCalendarComponent {
  // Inputs
  selectedDay = input<string>();
  hiddenOnMobile = input(false);

  // Outputs
  daySelected = output<string>();
  backToCalendar = output<void>();

  // Local state
  currentMonth = signal(new Date());
  calendar = computed(() => this.generateCalendar());

  constructor() {
    // Reset to current month when calendar becomes visible
    effect(() => {
      if (!this.hiddenOnMobile()) {
        this.currentMonth.set(new Date());
      }
    });
  }

  isSelectedDay(day: string): boolean {
    return this.selectedDay() === day;
  }

  selectDay(day: string): void {
    this.daySelected.emit(day);
  }

  previousMonth(): void {
    const current = this.currentMonth();
    const prevMonth = new Date(current);
    prevMonth.setMonth(prevMonth.getMonth() - 1);
    this.currentMonth.set(prevMonth);
  }

  nextMonth(): void {
    const current = this.currentMonth();
    const nextMonth = new Date(current);
    nextMonth.setMonth(nextMonth.getMonth() + 1);
    this.currentMonth.set(nextMonth);
  }

  goBack(): void {
    this.backToCalendar.emit();
  }

  private generateCalendar(): CalendarMonth {
    const currentMonth = this.currentMonth();
    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth();

    const monthNames = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
                        'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];

    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const firstDayOfWeek = firstDay.getDay();
    const daysInMonth = lastDay.getDate();

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
}
