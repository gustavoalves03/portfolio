import { Component, PLATFORM_ID, computed, inject, input } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Hero block that renders a fullscreen <video> on desktop devices with a
 * pointer (hover) and a static <img> poster everywhere else (mobile,
 * SSR, prefers-reduced-motion).
 *
 * Overlay content (branding, search bar, etc.) is projected via <ng-content>.
 *
 * SSR-safe: the server always renders the <img> poster. The browser hydrates
 * with <video> only when matchMedia reports desktop AND hover-capable AND
 * reduced motion is NOT requested.
 */
@Component({
  selector: 'app-hero-video',
  standalone: true,
  templateUrl: './hero-video.component.html',
  styleUrl: './hero-video.component.scss',
})
export class HeroVideoComponent {
  readonly posterUrl = input.required<string>();
  /** MP4 source URL. Optional — when missing, only the poster is rendered. */
  readonly videoUrl = input<string | null>(null);
  /** Optional WebM source URL for browsers that prefer it. */
  readonly videoWebmUrl = input<string | null>(null);

  private readonly platformId = inject(PLATFORM_ID);

  protected readonly showVideo = computed(() => this.shouldShowVideo());

  private shouldShowVideo(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    if (!this.videoUrl()) return false;
    const desktopWithHover = window.matchMedia(
      '(min-width: 768px) and (hover: hover)',
    ).matches;
    const reducedMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches;
    return desktopWithHover && !reducedMotion;
  }
}
