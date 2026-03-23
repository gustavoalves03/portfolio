import { Component, inject, signal, computed } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { PublicCareDto, TimeSlot } from '../../../features/salon-profile/models/salon-profile.model';

export interface BookingDialogData {
  slug: string;
  care: PublicCareDto;
}

export interface BookingDialogResult {
  careId: number;
  date: string;
  startTime: string;
}

@Component({
  selector: 'app-booking-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  templateUrl: './booking-dialog.component.html',
  styleUrl: './booking-dialog.component.scss',
})
export class BookingDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<BookingDialogComponent>);
  private readonly data = inject<BookingDialogData>(MAT_DIALOG_DATA);
  private readonly salonService = inject(SalonProfileService);

  readonly care = this.data.care;
  readonly slug = this.data.slug;

  readonly currentMonth = signal(new Date());
  readonly selectedDate = signal<Date | null>(null);
  readonly slots = signal<TimeSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly selectedSlot = signal<TimeSlot | null>(null);

  readonly weekDayLabels = ['Lu', 'Ma', 'Me', 'Je', 'Ve', 'Sa', 'Di'];

  readonly monthLabel = computed(() => {
    const d = this.currentMonth();
    return d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  });

  readonly calendarDays = computed(() => {
    const month = this.currentMonth();
    const year = month.getFullYear();
    const m = month.getMonth();
    const firstDay = new Date(year, m, 1);
    const lastDay = new Date(year, m + 1, 0);

    let startDow = firstDay.getDay() - 1;
    if (startDow < 0) startDow = 6;

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const days: { date: Date; dayOfMonth: number; isCurrentMonth: boolean; isToday: boolean; isPast: boolean }[] = [];

    for (let i = startDow - 1; i >= 0; i--) {
      const d = new Date(year, m, -i);
      days.push({ date: d, dayOfMonth: d.getDate(), isCurrentMonth: false, isToday: false, isPast: true });
    }

    for (let i = 1; i <= lastDay.getDate(); i++) {
      const d = new Date(year, m, i);
      d.setHours(0, 0, 0, 0);
      days.push({
        date: d,
        dayOfMonth: i,
        isCurrentMonth: true,
        isToday: d.getTime() === today.getTime(),
        isPast: d < today,
      });
    }

    const remaining = 42 - days.length;
    for (let i = 1; i <= remaining; i++) {
      const d = new Date(year, m + 1, i);
      days.push({ date: d, dayOfMonth: d.getDate(), isCurrentMonth: false, isToday: false, isPast: false });
    }

    return days;
  });

  isSelectedDate(date: Date): boolean {
    const sel = this.selectedDate();
    if (!sel) return false;
    return this.formatDate(date) === this.formatDate(sel);
  }

  prevMonth(): void {
    const d = this.currentMonth();
    this.currentMonth.set(new Date(d.getFullYear(), d.getMonth() - 1, 1));
  }

  nextMonth(): void {
    const d = this.currentMonth();
    this.currentMonth.set(new Date(d.getFullYear(), d.getMonth() + 1, 1));
  }

  selectDay(day: { date: Date; isCurrentMonth: boolean; isPast: boolean }): void {
    if (!day.isCurrentMonth || day.isPast) return;
    this.selectedDate.set(day.date);
    this.selectedSlot.set(null);
    this.loadSlots(day.date);
  }

  selectSlot(slot: TimeSlot): void {
    this.selectedSlot.set(slot);
  }

  confirm(): void {
    const date = this.selectedDate();
    const slot = this.selectedSlot();
    if (!date || !slot) return;

    const result: BookingDialogResult = {
      careId: this.care.id,
      date: this.formatDate(date),
      startTime: slot.startTime,
    };
    this.dialogRef.close(result);
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

  private formatDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}
