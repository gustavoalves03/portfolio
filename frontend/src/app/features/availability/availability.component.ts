import { Component, computed, effect, inject, signal } from '@angular/core';
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
import { DashboardStore } from '../dashboard/store/dashboard.store';

const DEFAULT_SLOT: TimeSlot = { openTime: '09:00', closeTime: '18:00' };

/**
 * Returns a closeTime that is strictly after `openTime`. Defaults to "openTime
 * + 1h", capped at "23:59" so we never wrap around midnight. Falls back to
 * "23:59" itself when openTime is already 23:00 or later. Pure helper, exported
 * for unit testing.
 */
export function nextValidClose(openTime: string): string {
  if (!openTime || !openTime.includes(':')) return '18:00';
  const parts = openTime.split(':');
  const h = Number(parts[0]);
  const m = Number(parts[1]);
  if (!Number.isFinite(h) || !Number.isFinite(m) || parts[1] === '') return '18:00';
  const target = h + 1;
  if (target >= 24) return '23:59';
  return `${String(target).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

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
  private dashboardStore = inject(DashboardStore);
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
    // Refresh tenant readiness after a successful save so the guided tour
    // (which reacts to readiness flags) auto-advances.
    effect(() => {
      if (this.store.saveSuccess()) {
        this.dashboardStore.loadReadiness();
        this.store.clearSaveSuccess();
      }
    });
  }

  getDaySlots(dayOfWeek: number): TimeSlot[] {
    return this.week().find((d) => d.dayOfWeek === dayOfWeek)?.slots ?? [];
  }

  isDayClosed(dayOfWeek: number): boolean {
    return this.getDaySlots(dayOfWeek).length === 0;
  }

  /** A slot is invalid if its closeTime is not strictly after openTime. */
  isSlotInvalid(slot: TimeSlot): boolean {
    if (!slot.openTime || !slot.closeTime) return false;
    return slot.closeTime <= slot.openTime;
  }

  /**
   * Whether the user can still append a slot to a given day. Refuses once
   * the last slot already runs to the end of the day, so the "+ Add slot"
   * button doesn't produce degenerate 23:59 → 23:59 entries.
   */
  canAddSlot(dayOfWeek: number): boolean {
    const slots = this.getDaySlots(dayOfWeek);
    if (slots.length === 0) return true;
    const last = slots[slots.length - 1];
    return last.closeTime < '23:59';
  }

  /** Disables the Save button as soon as any slot is invalid. */
  readonly hasInvalidSlots = computed(() =>
    this.week().some((d) => d.slots.some((s) => this.isSlotInvalid(s)))
  );

  openDay(dayOfWeek: number): void {
    this.week.update((w) =>
      w.map((d) =>
        d.dayOfWeek === dayOfWeek ? { ...d, slots: [{ ...DEFAULT_SLOT }] } : d
      )
    );
  }

  addSlot(dayOfWeek: number): void {
    // Refuse to append a degenerate slot once the day already covers up to 23:59.
    if (!this.canAddSlot(dayOfWeek)) return;
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== dayOfWeek) return d;
        const lastSlot = d.slots[d.slots.length - 1];
        const newOpen = lastSlot ? lastSlot.closeTime : '09:00';
        // Ensure the newly-added slot is valid out of the box: pick a close
        // strictly after newOpen, capped at the end of the day.
        const newClose = nextValidClose(newOpen);
        return { ...d, slots: [...d.slots, { openTime: newOpen, closeTime: newClose }] };
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
    if (this.hasInvalidSlots()) {
      this.snackBar.open(
        this.i18n.translate('pro.availability.invalidTime'),
        'OK',
        { duration: 4000, panelClass: 'snackbar-error' }
      );
      return;
    }
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
