import { Component, OnDestroy, PLATFORM_ID, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { TourService } from '../../../features/onboarding/tour/tour.service';
import { TourBubbleComponent } from './tour-bubble.component';

const RETRY_DELAY_MS = 500;
const MAX_RETRIES = 3;

@Component({
  selector: 'app-tour-overlay',
  standalone: true,
  imports: [TourBubbleComponent],
  templateUrl: './tour-overlay.component.html',
  styleUrl: './tour-overlay.component.scss',
})
export class TourOverlayComponent implements OnDestroy {
  protected readonly tour = inject(TourService);
  protected readonly targetRect = signal<DOMRect | null>(null);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private resizeObs: ResizeObserver | null = null;
  private currentEl: HTMLElement | null = null;
  private readonly scrollHandler = () => this.measureCurrent();

  constructor() {
    effect(() => {
      const step = this.tour.currentStep();
      const active = this.tour.active();
      if (!step || !active) {
        this.cleanup();
        return;
      }
      this.bindToTarget(step.tourStep);
    });
  }

  ngOnDestroy(): void {
    this.cleanup();
  }

  private bindToTarget(tourStep: string, attempt = 0): void {
    if (!this.isBrowser) return;
    const el = document.querySelector<HTMLElement>(`[data-tour-step="${tourStep}"]`);
    if (!el) {
      if (attempt < MAX_RETRIES) {
        setTimeout(() => this.bindToTarget(tourStep, attempt + 1), RETRY_DELAY_MS);
      } else {
        // eslint-disable-next-line no-console
        console.warn(
          `[tour] Target [data-tour-step="${tourStep}"] not found after ${MAX_RETRIES} retries — closing tour.`
        );
        this.tour.stop();
      }
      return;
    }
    this.cleanup();
    this.currentEl = el;
    this.targetRect.set(el.getBoundingClientRect());
    this.resizeObs = new ResizeObserver(() => this.measureCurrent());
    this.resizeObs.observe(el);
    window.addEventListener('scroll', this.scrollHandler, { passive: true });
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  private measureCurrent(): void {
    if (!this.currentEl) return;
    this.targetRect.set(this.currentEl.getBoundingClientRect());
  }

  private cleanup(): void {
    this.resizeObs?.disconnect();
    this.resizeObs = null;
    if (this.isBrowser) {
      window.removeEventListener('scroll', this.scrollHandler);
    }
    this.currentEl = null;
    this.targetRect.set(null);
  }
}
