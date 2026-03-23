import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AvailabilityStore } from './availability.store';
import {
  DaySlots,
  OpeningHourRequest,
  OpeningHourResponse,
  TimeSlot,
} from './availability.model';

const DEFAULT_SLOT: TimeSlot = { openTime: '09:00', closeTime: '18:00' };

@Component({
  selector: 'app-availability',
  standalone: true,
  imports: [FormsModule, TranslocoPipe, MatSnackBarModule, MatIconModule, MatButtonModule],
  templateUrl: './availability.component.html',
  styleUrl: './availability.component.scss',
  providers: [AvailabilityStore],
})
export class AvailabilityComponent {
  readonly store = inject(AvailabilityStore);
  private snackBar = inject(MatSnackBar);
  private i18n = inject(TranslocoService);

  readonly weekDays = [1, 2, 3, 4, 5, 6, 7];
  readonly week = signal<DaySlots[]>(this.buildEmptyWeek());

  constructor() {
    effect(() => {
      const hours = this.store.hours();
      if (hours) {
        this.syncFromStoreData(hours);
      }
    });
  }

  getDaySlots(dayOfWeek: number): TimeSlot[] {
    return this.week().find((d) => d.dayOfWeek === dayOfWeek)?.slots ?? [];
  }

  isDayClosed(dayOfWeek: number): boolean {
    return this.getDaySlots(dayOfWeek).length === 0;
  }

  openDay(dayOfWeek: number): void {
    this.week.update((w) =>
      w.map((d) =>
        d.dayOfWeek === dayOfWeek ? { ...d, slots: [{ ...DEFAULT_SLOT }] } : d
      )
    );
  }

  addSlot(dayOfWeek: number): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        const lastSlot = d.slots[d.slots.length - 1];
        const newOpen = lastSlot ? lastSlot.closeTime : '09:00';
        return { ...d, slots: [...d.slots, { openTime: newOpen, closeTime: '18:00' }] };
      })
    );
  }

  removeSlot(dayOfWeek: number, index: number): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        return { ...d, slots: d.slots.filter((_, i) => i !== index) };
      })
    );
  }

  updateSlotTime(
    dayOfWeek: number,
    index: number,
    field: 'openTime' | 'closeTime',
    value: string
  ): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        return {
          ...d,
          slots: d.slots.map((s, i) => (i === index ? { ...s, [field]: value } : s)),
        };
      })
    );
  }

  onSave(): void {
    const requests: OpeningHourRequest[] = [];
    for (const day of this.week()) {
      for (const slot of day.slots) {
        requests.push({
          dayOfWeek: day.dayOfWeek,
          openTime: slot.openTime,
          closeTime: slot.closeTime,
        });
      }
    }
    this.store.saveHours(requests);
    this.snackBar.open(this.i18n.translate('pro.availability.saveSuccess'), 'OK', {
      duration: 3000,
    });
  }

  private syncFromStoreData(hours: OpeningHourResponse[]): void {
    const week = this.buildEmptyWeek();
    for (const h of hours) {
      const day = week.find((d) => d.dayOfWeek === h.dayOfWeek);
      if (day) {
        day.slots.push({ openTime: h.openTime, closeTime: h.closeTime });
      }
    }
    this.week.set(week);
  }

  private buildEmptyWeek(): DaySlots[] {
    return this.weekDays.map((d) => ({ dayOfWeek: d, slots: [] }));
  }
}
