import { DestroyRef, PLATFORM_ID, Signal, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const DESKTOP_QUERY = '(min-width: 768px)';

/**
 * Reactive boolean signal that tracks whether the viewport is desktop-sized.
 *
 * Returns a signal that is `false` during SSR and on viewports < 768px,
 * `true` otherwise. The signal is updated via `matchMedia` change events
 * and the listener is cleaned up automatically when the injection context
 * is destroyed.
 *
 * Must be called inside an injection context (component, directive, service).
 */
export function isDesktopSignal(): Signal<boolean> {
  const platformId = inject(PLATFORM_ID);
  const destroyRef = inject(DestroyRef);

  if (!isPlatformBrowser(platformId)) {
    return signal(false).asReadonly();
  }

  const mql = window.matchMedia(DESKTOP_QUERY);
  const result = signal(mql.matches);
  const handler = (e: MediaQueryListEvent) => result.set(e.matches);
  mql.addEventListener('change', handler);
  destroyRef.onDestroy(() => mql.removeEventListener('change', handler));

  return result.asReadonly();
}
