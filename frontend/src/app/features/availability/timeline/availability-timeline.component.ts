import { Component, EventEmitter, Output, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { DayOfWeek, WeekSlots } from '../availability.model';
import { positionInRail } from '../time-utils';

export interface SlotClickEvent {
  day: DayOfWeek;
  slotIndex: number;
  anchor: HTMLElement;
}

export interface AddSlotClickEvent {
  day: DayOfWeek;
  anchor: HTMLElement;
}

@Component({
  selector: 'app-availability-timeline',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  templateUrl: './availability-timeline.component.html',
  styleUrl: './availability-timeline.component.scss',
})
export class AvailabilityTimelineComponent {
  readonly week = input.required<WeekSlots>();

  @Output() readonly slotClick = new EventEmitter<SlotClickEvent>();
  @Output() readonly addSlotClick = new EventEmitter<AddSlotClickEvent>();
  @Output() readonly dayToggle = new EventEmitter<DayOfWeek>();

  /** Hours displayed on the axis: 6, 7, ..., 21 (16 entries). */
  readonly hours = Array.from({ length: 16 }, (_, i) => i + 6);

  readonly daysWithDerived = computed(() =>
    this.week().map((d) => ({
      ...d,
      isClosed: d.slots.length === 0,
      blocks: d.slots.map((s) => ({
        slot: s,
        leftPct: positionInRail(s.openTime),
        widthPct: positionInRail(s.closeTime) - positionInRail(s.openTime),
      })),
    })),
  );

  onSlotClick(event: MouseEvent, day: DayOfWeek, slotIndex: number): void {
    event.stopPropagation();
    this.slotClick.emit({
      day,
      slotIndex,
      anchor: event.currentTarget as HTMLElement,
    });
  }

  onAddSlotClick(event: MouseEvent, day: DayOfWeek): void {
    event.stopPropagation();
    this.addSlotClick.emit({
      day,
      anchor: event.currentTarget as HTMLElement,
    });
  }

  onSwitchClick(day: DayOfWeek): void {
    this.dayToggle.emit(day);
  }
}
