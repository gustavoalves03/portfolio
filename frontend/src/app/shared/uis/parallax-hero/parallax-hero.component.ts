import {
  Component,
  DestroyRef,
  ElementRef,
  PLATFORM_ID,
  inject,
  input,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Hero section with a parallax-scrolling background image.
 *
 * The image translates upward at half the page-scroll speed via
 * `transform: translate3d(0, -scrollY*0.5, 0)`, throttled with
 * `requestAnimationFrame`. SSR-safe (no scroll math on the server).
 * `prefers-reduced-motion: reduce` disables the parallax (image stays
 * static).
 *
 * Overlay content (title, CTAs) is projected via <ng-content>.
 */
@Component({
  selector: 'app-parallax-hero',
  standalone: true,
  templateUrl: './parallax-hero.component.html',
  styleUrl: './parallax-hero.component.scss',
})
export class ParallaxHeroComponent {
  readonly imageUrl = input.required<string>();

  private readonly platformId = inject(PLATFORM_ID);
  private readonly hostRef = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly translateY = signal(0);

  constructor() {
    if (!isPlatformBrowser(this.platformId)) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    let rafScheduled = false;
    const onScroll = () => {
      if (rafScheduled) return;
      rafScheduled = true;
      requestAnimationFrame(() => {
        rafScheduled = false;
        const rect = this.hostRef.nativeElement.getBoundingClientRect();
        // Only animate while the hero is in/near viewport.
        const top = rect.top;
        if (top > window.innerHeight || top + rect.height < 0) return;
        // -0.5x scroll relative to the hero's top edge.
        this.translateY.set(-top * 0.5);
      });
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    this.destroyRef.onDestroy(() =>
      window.removeEventListener('scroll', onScroll),
    );
  }
}
