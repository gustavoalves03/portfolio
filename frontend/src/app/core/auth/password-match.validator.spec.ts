import { FormBuilder } from '@angular/forms';
import { passwordMatchValidator } from './password-match.validator';

describe('passwordMatchValidator', () => {
  const fb = new FormBuilder();

  it('returns null when both passwords are empty', () => {
    const group = fb.group({ password: '', confirmPassword: '' });
    expect(passwordMatchValidator(group)).toBeNull();
  });

  it('returns null when passwords match', () => {
    const group = fb.group({ password: 'secret123', confirmPassword: 'secret123' });
    expect(passwordMatchValidator(group)).toBeNull();
  });

  it('returns { passwordMismatch: true } when passwords differ', () => {
    const group = fb.group({ password: 'secret123', confirmPassword: 'other' });
    expect(passwordMatchValidator(group)).toEqual({ passwordMismatch: true });
  });

  it('returns null when confirm is still empty (let required handle it)', () => {
    const group = fb.group({ password: 'secret123', confirmPassword: '' });
    expect(passwordMatchValidator(group)).toBeNull();
  });
});
