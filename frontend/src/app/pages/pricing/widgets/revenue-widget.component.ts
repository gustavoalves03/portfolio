import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { useCountUp } from '../../../shared/utils/use-count-up';

const TARGET_REVENUE = 12450;
const TARGET_TREND = 18;
// Sparkline: 8 monthly data points, last is the current month.
// Inline polyline coordinates inside a 0..100 viewBox.
const SPARKLINE_PATH = 'M 0,70 L 14,60 L 28,55 L 42,52 L 56,42 L 70,38 L 84,28 L 100,18';

@Component({
  selector: 'app-revenue-widget',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './revenue-widget.component.html',
  styleUrl: './revenue-widget.component.scss',
})
export class RevenueWidgetComponent {
  readonly started = input.required<boolean>();

  // Bound to a startSignal-shaped accessor for useCountUp.
  private readonly startedSignal = computed(() => this.started());

  protected readonly revenue = useCountUp(TARGET_REVENUE, 1200, this.startedSignal);
  protected readonly trend = useCountUp(TARGET_TREND, 1200, this.startedSignal);

  protected readonly sparklinePath = SPARKLINE_PATH;
  protected readonly sparklinePathLength = 250; // approximate length, stable enough for stroke-dashoffset

  protected readonly displayedRevenue = computed(() => Math.round(this.revenue()));
  protected readonly displayedTrend = computed(() => Math.round(this.trend()));
}
