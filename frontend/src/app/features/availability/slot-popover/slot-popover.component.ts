import { Component, EventEmitter, Output, computed, effect, input, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { DayOfWeek, TimeSlot } from '../availability.model';
import { HHMM_OPTIONS, hhmmToMinutes, slotsOverlap, snapTo30 } from '../time-utils';

export interface SlotPopoverData {
  mode: 'create' | 'edit';
  dayOfWeek: DayOfWeek;
  initialStart: string; // "09:00"
  initialEnd: string;   // "18:00"
  /** Days the user can copy this slot to. Excludes dayOfWeek. */
  otherDays: DayOfWeek[];
  /** Other slots of the same day, used to detect overlap (excludes the slot being edited). */
  existingSlotsForDay: TimeSlot[];
}

export type SlotPopoverResult =
  | { action: 'save'; start: string; end: string; copyToDays: DayOfWeek[] }
  | { action: 'delete' }
  | { action: 'cancel' };

@Component({
  selector: 'app-slot-popover',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslocoPipe],
  templateUrl: './slot-popover.component.html',
  styleUrl: './slot-popover.component.scss',
})
export class SlotPopoverComponent {
  readonly data = input.required<SlotPopoverData>();
  @Output() readonly confirm = new EventEmitter<SlotPopoverResult>();

  readonly options = HHMM_OPTIONS;

  readonly start = signal<string>('09:00');
  readonly end = signal<string>('18:00');
  readonly copyToDays = signal<DayOfWeek[]>([]);

  constructor() {
    // Sync start/end from input whenever data changes.
    // Using effect() so it runs synchronously in the reactive context (incl. tests).
    effect(() => {
      const d = this.data();
      untracked(() => {
        this.start.set(snapTo30(d.initialStart));
        this.end.set(snapTo30(d.initialEnd));
        this.copyToDays.set([]);
      });
    });
  }

  readonly hasOverlap = computed(() => {
    const candidate: TimeSlot = { openTime: this.start(), closeTime: this.end() };
    return this.data().existingSlotsForDay.some((s) => slotsOverlap(candidate, s));
  });

  readonly canConfirm = computed(() => {
    const startMin = hhmmToMinutes(this.start());
    const endMin = hhmmToMinutes(this.end());
    if (startMin >= endMin) return false;
    if (this.hasOverlap()) return false;
    return true;
  });

  toggleCopyDay(day: DayOfWeek): void {
    this.copyToDays.update((cur) =>
      cur.includes(day) ? cur.filter((d) => d !== day) : [...cur, day],
    );
  }

  toggleAllDays(): void {
    const all = this.data().otherDays;
    const current = this.copyToDays();
    if (current.length === all.length) {
      this.copyToDays.set([]);
    } else {
      this.copyToDays.set([...all]);
    }
  }

  onConfirm(): void {
    if (!this.canConfirm()) return;
    this.confirm.emit({
      action: 'save',
      start: this.start(),
      end: this.end(),
      copyToDays: this.copyToDays(),
    });
  }

  onDelete(): void {
    this.confirm.emit({ action: 'delete' });
  }

  onCancel(): void {
    this.confirm.emit({ action: 'cancel' });
  }
}
