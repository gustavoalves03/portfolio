import { Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

interface Cell {
  readonly index: number;
  readonly filled: boolean;
  readonly delayMs: number;
}

// Deterministic pattern for filled cells: 26 of 42 (~62%).
// Hand-picked indices to look like a realistic week — denser midweek.
const FILLED_INDICES = new Set([
  1, 2, 3, 5, 6,
  8, 9, 11, 12, 13,
  15, 17, 19, 20,
  22, 23, 24, 25, 27,
  29, 30, 31, 33,
  36, 37, 39,
]);

const CELL_COUNT = 42;
const STAGGER_MS = 30;

@Component({
  selector: 'app-calendar-widget',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './calendar-widget.component.html',
  styleUrl: './calendar-widget.component.scss',
})
export class CalendarWidgetComponent {
  readonly started = input.required<boolean>();

  protected readonly cells: readonly Cell[] = (() => {
    const filledList = Array.from(FILLED_INDICES);
    return Array.from({ length: CELL_COUNT }).map((_, index) => {
      const filled = FILLED_INDICES.has(index);
      const filledIndex = filled ? filledList.indexOf(index) : 0;
      return { index, filled, delayMs: filledIndex * STAGGER_MS };
    });
  })();
}
