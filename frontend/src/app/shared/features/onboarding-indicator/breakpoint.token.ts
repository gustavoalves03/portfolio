import { InjectionToken, Signal } from '@angular/core';
import { isDesktopSignal } from '../../../core/utils/breakpoint.signal';

/**
 * Indirection token so tests can swap the desktop breakpoint signal
 * without monkey-patching `window.matchMedia`.
 *
 * Default factory invokes `isDesktopSignal()` inside an injection context.
 */
export const ONBOARDING_BREAKPOINT = new InjectionToken<() => Signal<boolean>>(
  'OnboardingBreakpoint',
  { providedIn: 'root', factory: () => isDesktopSignal }
);
