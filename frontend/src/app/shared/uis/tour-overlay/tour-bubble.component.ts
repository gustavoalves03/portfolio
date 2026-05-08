import { Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { TourStep } from '../../../features/onboarding/tour/tour-step.model';

const BUBBLE_WIDTH = 320;
const BUBBLE_GAP = 14;
const ESTIMATED_HEIGHT = 200;

@Component({
  selector: 'app-tour-bubble',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule],
  templateUrl: './tour-bubble.component.html',
  styleUrl: './tour-bubble.component.scss',
})
export class TourBubbleComponent {
  readonly step = input.required<TourStep>();
  readonly progress = input.required<{ done: number; total: number }>();
  readonly inTransition = input(false);
  readonly targetRect = input.required<DOMRect>();
  readonly close = output<void>();

  protected readonly bubblePosition = computed(() => {
    const r = this.targetRect();
    const fitsBelow = r.bottom + ESTIMATED_HEIGHT + BUBBLE_GAP < window.innerHeight;
    const left = Math.max(16, Math.min(window.innerWidth - BUBBLE_WIDTH - 16, r.left));
    return fitsBelow
      ? { top: r.bottom + BUBBLE_GAP, left, placement: 'below' as const }
      : { top: r.top - ESTIMATED_HEIGHT - BUBBLE_GAP, left, placement: 'above' as const };
  });
}
