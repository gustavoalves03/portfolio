import { TenantReadiness } from '../../dashboard/models/dashboard.model';

/** Stable identifier for one of the 6 publish conditions, used as i18n suffix and DOM marker. */
export type WizardStepKey =
  | 'name'
  | 'contact'
  | 'logo'
  | 'categories'
  | 'cares'
  | 'openingHours';

/**
 * One step covered by the guided tour. Maps a backend readiness flag to:
 *  - the page that owns the field (`route`)
 *  - the value of `data-tour-step` placed on the field in that page's template
 *  - the i18n keys for the bubble title and description
 */
export interface TourStep {
  readonly key: WizardStepKey;
  readonly readinessFlag: keyof TenantReadiness;
  readonly route: string;
  readonly tourStep: string;
  readonly titleKey: string;
  readonly descKey: string;
}
