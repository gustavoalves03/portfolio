/** Identifier matching i18n keys under `pro.dashboard.checklist.*` and `pro.onboarding.*`. */
export type OnboardingStepKey = 'name' | 'cares' | 'openingHours';

/** A single step in the onboarding checklist. */
export interface OnboardingStep {
  /** Stable key, also used as i18n suffix. */
  readonly key: OnboardingStepKey;
  /** True when the underlying readiness flag is satisfied. */
  readonly done: boolean;
  /** Router link the user should follow to act on this step. */
  readonly link: string;
  /** Optional query params to pass when navigating (e.g. `{ openCreate: 'care' }`). */
  readonly queryParams: Record<string, string> | null;
}

/** Aggregate progress derived from a list of steps. */
export interface OnboardingProgress {
  /** Number of steps with `done === true`. */
  readonly done: number;
  /** Total steps. */
  readonly total: number;
  /** First step with `done === false`, or `null` when all are done. */
  readonly nextKey: OnboardingStepKey | null;
  /** Integer 0-100. */
  readonly percent: number;
}
