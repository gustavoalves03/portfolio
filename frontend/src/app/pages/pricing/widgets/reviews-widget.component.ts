import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { useCountUp } from '../../../shared/utils/use-count-up';

const TARGET_RATING = 4.9;
const TARGET_COUNT = 184;
const STAR_COUNT = 5;
const STAR_STAGGER_MS = 100;
const QUOTE_KEYS = ['quote1', 'quote2', 'quote3'] as const;
const QUOTE_STAGGER_MS = 180;

@Component({
  selector: 'app-reviews-widget',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './reviews-widget.component.html',
  styleUrl: './reviews-widget.component.scss',
})
export class ReviewsWidgetComponent {
  readonly started = input.required<boolean>();

  private readonly startedSignal = computed(() => this.started());
  protected readonly ratingValue = useCountUp(TARGET_RATING, 1200, this.startedSignal);
  protected readonly displayedRating = computed(() => this.ratingValue().toFixed(1));

  protected readonly count = TARGET_COUNT;
  protected readonly stars = Array.from({ length: STAR_COUNT }).map((_, i) => ({
    index: i,
    delayMs: i * STAR_STAGGER_MS,
  }));

  protected readonly quotes = QUOTE_KEYS.map((key, i) => ({
    key,
    delayMs: i * QUOTE_STAGGER_MS,
  }));
}
