import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Cross-field validator for a FormGroup that contains both `email` and
 * `emailConfirm` controls. Returns `{ emailMismatch: true }` on the group
 * when the values differ (case-insensitive comparison). Returns null when
 * either field is empty, letting individual `Validators.required` validators
 * surface their own errors first.
 */
export const emailMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const email = group.get('email')?.value;
  const confirm = group.get('emailConfirm')?.value;
  if (!email || !confirm) return null;
  return String(email).toLowerCase() === String(confirm).toLowerCase()
    ? null
    : { emailMismatch: true };
};
