import { DestroyRef, PLATFORM_ID, Signal, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

/**
 * Returns a `Signal<number>` that ramps from 0 to `target` over `durationMs`
 * once `startSignal()` becomes true. Uses RAF + easeOutCubic.
 *
 * Re-triggering: only ramps once. After the first start, subsequent flips
 * of `startSignal` are ignored.
 *
 * SSR-safe: returns a static 0 signal on the server.
 *
 * Must be called inside an injection context.
 */
export function useCountUp(
  target: number,
  durationMs: number,
  startSignal: Signal<boolean>,
): Signal<number> {
  const platformId = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  const value = signal(0);

  if (!isPlatformBrowser(platformId)) {
    return value.asReadonly();
  }

  let started = false;
  let rafId = 0;

  effect(() => {
    if (started) return;
    if (!startSignal()) return;
    started = true;

    const startTime = performance.now();
    const tick = (now: number) => {
      const elapsed = now - startTime;
      const t = Math.min(1, elapsed / durationMs);
      // easeOutCubic
      const eased = 1 - Math.pow(1 - t, 3);
      value.set(target * eased);
      if (t < 1) {
        rafId = requestAnimationFrame(tick);
      } else {
        value.set(target);
      }
    };
    rafId = requestAnimationFrame(tick);
  });

  destroyRef.onDestroy(() => {
    if (rafId) cancelAnimationFrame(rafId);
  });

  return value.asReadonly();
}
