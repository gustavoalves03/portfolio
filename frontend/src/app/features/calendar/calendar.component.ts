import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CalendarStore } from './calendar.store';
import { BlockedSlotRequest, BlockedSlotResponse, CalendarDay } from './calendar.model';
import { OpeningHourResponse } from '../availability/availability.model';

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [
    FormsModule,
    TranslocoPipe,
    MatSnackBarModule,
    MatIconModule,
    MatButtonModule,
    MatSlideToggleModule,
  ],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.scss',
  providers: [CalendarStore],
})
export class CalendarComponent {
  readonly store = inject(CalendarStore);
  private snackBar = inject(MatSnackBar);
  private i18n = inject(TranslocoService);

  readonly currentMonth = signal(new Date());
  readonly selectedDate = signal<Date | null>(null);
  readonly showBlockForm = signal(false);

  // Block form state
  readonly blockFullDay = signal(false);
  readonly blockStartTime = signal('09:00');
  readonly blockEndTime = signal('18:00');
  readonly blockReason = signal('');

  readonly weekDayLabels = ['Lu', 'Ma', 'Me', 'Je', 'Ve', 'Sa', 'Di'];

  readonly monthLabel = computed(() => {
    const d = this.currentMonth();
    return d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  });

  readonly calendarDays = computed<CalendarDay[]>(() => {
    const month = this.currentMonth();
    const year = month.getFullYear();
    const m = month.getMonth();
    const firstDay = new Date(year, m, 1);
    const lastDay = new Date(year, m + 1, 0);

    // Monday=0 adjustment (JS: Sunday=0)
    let startDow = firstDay.getDay() - 1;
    if (startDow < 0) startDow = 6;

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const blockedDates = new Set(this.store.blockedSlots().map((s) => s.date));
    const closedDows = this.getClosedDaysOfWeek();

    const days: CalendarDay[] = [];

    // Previous month padding
    for (let i = startDow - 1; i >= 0; i--) {
      const d = new Date(year, m, -i);
      days.push(this.buildDay(d, false, today, blockedDates, closedDows));
    }

    // Current month
    for (let i = 1; i <= lastDay.getDate(); i++) {
      const d = new Date(year, m, i);
      days.push(this.buildDay(d, true, today, blockedDates, closedDows));
    }

    // Next month padding (fill to 42 = 6 rows)
    const remaining = 42 - days.length;
    for (let i = 1; i <= remaining; i++) {
      const d = new Date(year, m + 1, i);
      days.push(this.buildDay(d, false, today, blockedDates, closedDows));
    }

    return days;
  });

  readonly selectedDayBlocks = computed(() => {
    const sel = this.selectedDate();
    if (!sel) return [];
    const dateStr = this.formatDate(sel);
    return this.store.blockedSlots().filter((s) => s.date === dateStr);
  });

  readonly selectedDayOpeningHours = computed(() => {
    const sel = this.selectedDate();
    if (!sel) return [];
    // JS: Sunday=0, our model: Monday=1...Sunday=7
    let dow = sel.getDay();
    dow = dow === 0 ? 7 : dow;
    return this.store.openingHours().filter((h) => h.dayOfWeek === dow);
  });

  readonly isSelectedDayClosed = computed(() => {
    return this.selectedDayOpeningHours().length === 0;
  });

  readonly holidayMap = computed(() => {
    const map = new Map<string, { date: string; name: string }>();
    for (const h of this.store.holidays()) {
      map.set(h.date, h);
    }
    return map;
  });

  readonly isSelectedDayHoliday = computed(() => {
    const sel = this.selectedDate();
    if (!sel) return false;
    return this.holidayMap().has(this.formatDate(sel));
  });

  readonly selectedDayHolidayName = computed(() => {
    const sel = this.selectedDate();
    if (!sel) return null;
    return this.holidayMap().get(this.formatDate(sel))?.name ?? null;
  });

  prevMonth(): void {
    const d = this.currentMonth();
    this.currentMonth.set(new Date(d.getFullYear(), d.getMonth() - 1, 1));
  }

  nextMonth(): void {
    const d = this.currentMonth();
    this.currentMonth.set(new Date(d.getFullYear(), d.getMonth() + 1, 1));
  }

  selectDay(day: CalendarDay): void {
    if (!day.isCurrentMonth) return;
    this.selectedDate.set(day.date);
    this.showBlockForm.set(false);
  }

  isSelected(day: CalendarDay): boolean {
    const sel = this.selectedDate();
    if (!sel) return false;
    return this.formatDate(day.date) === this.formatDate(sel);
  }

  openBlockForm(): void {
    this.blockFullDay.set(false);
    this.blockStartTime.set('09:00');
    this.blockEndTime.set('18:00');
    this.blockReason.set('');
    this.showBlockForm.set(true);
  }

  confirmBlock(): void {
    const sel = this.selectedDate();
    if (!sel) return;

    const req: BlockedSlotRequest = {
      date: this.formatDate(sel),
      fullDay: this.blockFullDay(),
      startTime: this.blockFullDay() ? undefined : this.blockStartTime(),
      endTime: this.blockFullDay() ? undefined : this.blockEndTime(),
      reason: this.blockReason() || undefined,
    };

    this.store.createBlock(req);
    this.showBlockForm.set(false);
    this.snackBar.open(this.i18n.translate('pro.calendar.blockSuccess'), 'OK', { duration: 3000 });
  }

  onUnblock(block: BlockedSlotResponse): void {
    this.store.deleteBlock(block.id);
    this.snackBar.open(this.i18n.translate('pro.calendar.unblockSuccess'), 'OK', { duration: 3000 });
  }

  private buildDay(
    date: Date,
    isCurrentMonth: boolean,
    today: Date,
    blockedDates: Set<string>,
    closedDows: Set<number>
  ): CalendarDay {
    let dow = date.getDay();
    dow = dow === 0 ? 7 : dow;

    const dateStr = this.formatDate(date);
    const holiday = this.holidayMap().get(dateStr);

    return {
      date,
      dayOfMonth: date.getDate(),
      isCurrentMonth,
      isToday: date.getTime() === today.getTime(),
      isClosed: closedDows.has(dow),
      hasBlocks: blockedDates.has(dateStr),
      isHoliday: !!holiday,
      holidayName: holiday?.name ?? null,
    };
  }

  private getClosedDaysOfWeek(): Set<number> {
    const openDows = new Set(this.store.openingHours().map((h) => h.dayOfWeek));
    const closed = new Set<number>();
    for (let d = 1; d <= 7; d++) {
      if (!openDows.has(d)) closed.add(d);
    }
    return closed;
  }

  private formatDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
