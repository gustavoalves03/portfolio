import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Cross-field validator for a FormGroup that contains both `password` and
 * `confirmPassword` controls. Returns `{ passwordMismatch: true }` on the
 * group when the values differ. Returns null when either field is empty,
 * letting individual `Validators.required` validators surface their own
 * errors first.
 */
export const passwordMatchValidator: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  if (!password || !confirm) return null;
  return password === confirm ? null : { passwordMismatch: true };
};
