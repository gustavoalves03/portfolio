import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AvailabilityStore } from './availability.store';
import {
  DayOfWeek,
  OpeningHourRequest,
  OpeningHourResponse,
  TimeSlot,
  WeekSlots,
} from './availability.model';
import { DashboardStore } from '../dashboard/store/dashboard.store';
import { isDesktopSignal } from '../../core/utils/breakpoint.signal';
import { AvailabilityTimelineComponent, SlotClickEvent, AddSlotClickEvent } from './timeline/availability-timeline.component';
import { AvailabilityDayListComponent } from './day-list/availability-day-list.component';
import { SlotPopoverService } from './slot-popover/slot-popover.service';
import { SlotPopoverData } from './slot-popover/slot-popover.component';
import { WEEK_PRESETS, WeekPresetKey, applyPreset } from './presets/week-presets';
import { hhmmToMinutes } from './time-utils';

const DEFAULT_SLOT: TimeSlot = { openTime: '09:00', closeTime: '18:00' };

@Component({
  selector: 'app-availability',
  standalone: true,
  imports: [
    FormsModule,
    TranslocoPipe,
    MatSnackBarModule,
    MatIconModule,
    MatButtonModule,
    AvailabilityTimelineComponent,
    AvailabilityDayListComponent,
  ],
  templateUrl: './availability.component.html',
  styleUrl: './availability.component.scss',
  providers: [AvailabilityStore],
})
export class AvailabilityComponent {
  readonly store = inject(AvailabilityStore);
  private readonly dashboardStore = inject(DashboardStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);
  private readonly popover = inject(SlotPopoverService);

  readonly isDesktop = isDesktopSignal();
  readonly weekDays: DayOfWeek[] = [1, 2, 3, 4, 5, 6, 7];
  readonly week = signal<WeekSlots>(this.buildEmptyWeek());
  readonly presets = WEEK_PRESETS;

  constructor() {
    effect(() => {
      const hours = this.store.hours();
      if (hours) {
        this.syncFromStoreData(hours);
      }
    });
    effect(() => {
      if (this.store.saveSuccess()) {
        this.snackBar.open(this.i18n.translate('pro.availability.saveSuccess'), 'OK', {
          duration: 3000,
        });
        this.dashboardStore.loadReadiness();
        this.store.clearSaveSuccess();
      }
    });
  }

  // ============ KPIs ============
  readonly openDaysCount = computed(
    () => this.week().filter((d) => d.slots.length > 0).length,
  );

  readonly totalSlotsCount = computed(
    () => this.week().reduce((acc, d) => acc + d.slots.length, 0),
  );

  readonly weeklyTotalMinutes = computed(() =>
    this.week().reduce((acc, d) => {
      return acc + d.slots.reduce((dAcc, s) => {
        const start = hhmmToMinutes(s.openTime);
        const end = hhmmToMinutes(s.closeTime);
        return end > start ? dAcc + (end - start) : dAcc;
      }, 0);
    }, 0),
  );

  formatDuration(min: number): string {
    if (min <= 0) return '0 h';
    const h = Math.floor(min / 60);
    const m = min % 60;
    if (h === 0) return `${m} min`;
    return m === 0 ? `${h} h` : `${h} h ${m.toString().padStart(2, '0')}`;
  }

  // ============ Presets ============
  applyPreset(key: WeekPresetKey): void {
    if (key === 'closeAll') {
      const ok = window.confirm(
        this.i18n.translate('pro.availability.preset.confirmCloseAll'),
      );
      if (!ok) return;
    }
    this.week.set(applyPreset(key, this.week()));
  }

  // ============ Toggle day ============
  onDayToggle(day: DayOfWeek): void {
    this.week.update((w) =>
      w.map((d) => {
        if (d.dayOfWeek !== day) return d;
        return d.slots.length === 0
          ? { ...d, slots: [{ ...DEFAULT_SLOT }] }
          : { ...d, slots: [] };
      }),
    );
  }

  // ============ Slot edit / create ============
  onSlotClick(e: SlotClickEvent): void {
    const slot = this.week().find((d) => d.dayOfWeek === e.day)?.slots[e.slotIndex];
    if (!slot) return;
    this.openPopover('edit', e.day, slot.openTime, slot.closeTime, e.slotIndex, e.anchor);
  }

  onAddSlot(e: AddSlotClickEvent): void {
    const day = this.week().find((d) => d.dayOfWeek === e.day);
    if (!day) return;
    const last = day.slots[day.slots.length - 1];
    const start = last ? last.closeTime : '09:00';
    const end = this.bumpHour(start);
    this.openPopover('create', e.day, start, end, null, e.anchor);
  }

  private bumpHour(start: string): string {
    const min = hhmmToMinutes(start);
    const target = Math.min(min + 60, hhmmToMinutes('22:00'));
    const h = Math.floor(target / 60);
    const m = target % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }

  private openPopover(
    mode: 'create' | 'edit',
    day: DayOfWeek,
    initialStart: string,
    initialEnd: string,
    editIndex: number | null,
    anchor: HTMLElement,
  ): void {
    const otherDays = this.weekDays.filter((d) => d !== day);
    const existingSlots = this.week().find((d) => d.dayOfWeek === day)?.slots ?? [];
    const existingForOverlap =
      editIndex == null
        ? existingSlots
        : existingSlots.filter((_, i) => i !== editIndex);

    const data: SlotPopoverData = {
      mode,
      dayOfWeek: day,
      initialStart,
      initialEnd,
      otherDays,
      existingSlotsForDay: existingForOverlap,
    };

    this.popover.open(data, new ElementRef(anchor), this.isDesktop()).subscribe((result) => {
      if (result.action === 'cancel') return;
      if (result.action === 'delete') {
        if (editIndex == null) return;
        this.removeSlot(day, editIndex);
        return;
      }
      // save
      const newSlot: TimeSlot = { openTime: result.start, closeTime: result.end };
      if (editIndex == null) {
        this.appendSlot(day, newSlot);
      } else {
        this.replaceSlot(day, editIndex, newSlot);
      }
      if (result.copyToDays.length > 0) {
        this.copySlotsToDays(day, result.copyToDays);
      }
    });
  }

  private appendSlot(day: DayOfWeek, slot: TimeSlot): void {
    this.week.update((w) =>
      w.map((d) => (d.dayOfWeek === day ? { ...d, slots: [...d.slots, slot] } : d)),
    );
  }

  private replaceSlot(day: DayOfWeek, index: number, slot: TimeSlot): void {
    this.week.update((w) =>
      w.map((d) =>
        d.dayOfWeek === day
          ? { ...d, slots: d.slots.map((s, i) => (i === index ? slot : s)) }
          : d,
      ),
    );
  }

  private removeSlot(day: DayOfWeek, index: number): void {
    this.week.update((w) =>
      w.map((d) =>
        d.dayOfWeek === day
          ? { ...d, slots: d.slots.filter((_, i) => i !== index) }
          : d,
      ),
    );
  }

  /** Override the slots of every target day with the source day's slots. */
  private copySlotsToDays(sourceDay: DayOfWeek, targets: DayOfWeek[]): void {
    const sourceSlots = this.week().find((d) => d.dayOfWeek === sourceDay)?.slots ?? [];
    this.week.update((w) =>
      w.map((d) =>
        targets.includes(d.dayOfWeek as DayOfWeek)
          ? { ...d, slots: sourceSlots.map((s) => ({ ...s })) }
          : d,
      ),
    );
  }

  // ============ Save ============
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
  }

  // ============ Sync from store ============
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

  private buildEmptyWeek(): WeekSlots {
    return this.weekDays.map((d) => ({ dayOfWeek: d, slots: [] }));
  }
}
