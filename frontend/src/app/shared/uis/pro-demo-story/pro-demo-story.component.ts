import {
  Component,
  DestroyRef,
  ElementRef,
  PLATFORM_ID,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { MockBrowserComponent } from '../mock-browser/mock-browser.component';
import { useIntersection } from '../../utils/use-intersection';

/**
 * Animated dashboard demo — "storytelling séquencé" (D2).
 *
 * Cycles through a 4-step morning narrative every 16s once the component is
 * scrolled into view: notification arrives → calendar slot drops in → revenue
 * bumps → 5-star review lands. Each step spotlights the relevant widget and
 * swaps the caption above.
 *
 * SSR-safe: server renders the final-state of step 0 statically; the
 * client-side cycle starts only once the viewport intersects.
 * Respects `prefers-reduced-motion: reduce` (cycle stays on step 0).
 */
@Component({
  selector: 'app-pro-demo-story',
  standalone: true,
  imports: [TranslocoPipe, MockBrowserComponent],
  templateUrl: './pro-demo-story.component.html',
  styleUrl: './pro-demo-story.component.scss',
})
export class ProDemoStoryComponent {
  protected readonly stage = viewChild.required<ElementRef<HTMLElement>>('stage');
  protected readonly visible = useIntersection(this.stage, 0.25);

  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);

  /** Current step in the loop, 0..3. SSR returns 0 (initial state). */
  protected readonly step = signal(0);

  protected readonly stepCount = 4;
  /** ms between step transitions (16s total / 4 steps = 4s each). */
  private readonly stepInterval = 4000;

  protected readonly captionKey = computed(
    () => `home.v1.demo.captions.${this.step()}` as const,
  );
  protected readonly captionStepKey = computed(
    () => `home.v1.demo.steps.${this.step()}` as const,
  );

  /** Spotlight which widget is "active" at the current step. */
  protected readonly notifActive = computed(() => this.step() === 0);
  protected readonly calActive = computed(() => this.step() === 1);
  protected readonly revActive = computed(() => this.step() === 2);
  protected readonly rvsActive = computed(() => this.step() === 3);

  /** Cumulative reveals — keep showing previous-step results once they appear. */
  protected readonly notifShown = computed(() => this.step() >= 0);
  protected readonly calShown = computed(() => this.step() >= 1);
  protected readonly revShown = computed(() => this.step() >= 2);
  protected readonly rvsShown = computed(() => this.step() >= 3);

  constructor() {
    if (!isPlatformBrowser(this.platformId)) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    let intervalId: number | null = null;

    effect(() => {
      if (!this.visible()) return;
      if (intervalId !== null) return;
      intervalId = window.setInterval(() => {
        this.step.update((s) => (s + 1) % this.stepCount);
      }, this.stepInterval);
    });

    this.destroyRef.onDestroy(() => {
      if (intervalId !== null) {
        clearInterval(intervalId);
        intervalId = null;
      }
    });
  }
}
