import { Component, EventEmitter, Output, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { DayOfWeek, WeekSlots } from '../availability.model';
import { SlotClickEvent, AddSlotClickEvent } from '../timeline/availability-timeline.component';

@Component({
  selector: 'app-availability-day-list',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  templateUrl: './availability-day-list.component.html',
  styleUrl: './availability-day-list.component.scss',
})
export class AvailabilityDayListComponent {
  readonly week = input.required<WeekSlots>();

  @Output() readonly slotClick = new EventEmitter<SlotClickEvent>();
  @Output() readonly addSlotClick = new EventEmitter<AddSlotClickEvent>();
  @Output() readonly dayToggle = new EventEmitter<DayOfWeek>();

  isClosed(day: { slots: { openTime: string; closeTime: string }[] }): boolean {
    return day.slots.length === 0;
  }

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
