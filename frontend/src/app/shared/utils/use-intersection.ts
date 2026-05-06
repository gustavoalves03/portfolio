import { DestroyRef, ElementRef, PLATFORM_ID, Signal, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Returns a `Signal<boolean>` that flips `true` once when the host element
 * enters the viewport at the given threshold, and stays true afterwards.
 *
 * SSR-safe: returns a static `false` signal on the server.
 *
 * Must be called inside an injection context (component constructor or
 * field initializer).
 */
export function useIntersection(
  elementRef: Signal<ElementRef<HTMLElement>>,
  threshold = 0.3,
): Signal<boolean> {
  const platformId = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  const visible = signal(false);

  if (!isPlatformBrowser(platformId)) {
    return visible.asReadonly();
  }

  let observer: IntersectionObserver | null = null;
  let observedElement: HTMLElement | null = null;

  effect(() => {
    if (visible()) return; // Already visible; no need to keep observing.

    const el = elementRef()?.nativeElement;
    if (!el || el === observedElement) return;

    if (observer && observedElement) {
      observer.unobserve(observedElement);
    }
    if (!observer) {
      observer = new IntersectionObserver(
        (entries) => {
          if (entries.some((e) => e.isIntersecting)) {
            visible.set(true);
            if (observer && observedElement) {
              observer.unobserve(observedElement);
            }
          }
        },
        { threshold },
      );
    }
    observedElement = el;
    observer.observe(el);
  });

  destroyRef.onDestroy(() => {
    observer?.disconnect();
    observer = null;
    observedElement = null;
  });

  return visible.asReadonly();
}
