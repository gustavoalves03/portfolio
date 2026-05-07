export type WizardStepKey =
  | 'welcome'
  | 'name'
  | 'contact'
  | 'logo'
  | 'categories'
  | 'cares'
  | 'openingHours'
  | 'publish';

export const WIZARD_STEP_ORDER: readonly WizardStepKey[] = [
  'welcome',
  'name',
  'contact',
  'logo',
  'categories',
  'cares',
  'openingHours',
  'publish',
] as const;

/** Map a missing-key from the back to the wizard step that fixes it. */
export const MISSING_KEY_TO_STEP: Readonly<Record<string, WizardStepKey>> = {
  name: 'name',
  hasContact: 'contact',
  hasLogo: 'logo',
  hasCategory: 'categories',
  hasActiveCare: 'cares',
  hasOpeningHours: 'openingHours',
};
