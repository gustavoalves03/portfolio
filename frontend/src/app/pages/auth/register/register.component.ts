import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { AuthService } from '../../../core/auth/auth.service';
import { passwordMatchValidator } from '../../../core/auth/password-match.validator';
import { emailMatchValidator } from '../../../core/auth/email-match.validator';
import { suggestEmail } from '../../../core/utils/email-mailcheck.util';
import { focusFirstInvalid } from '../../../core/utils/form-focus.util';
import { FormValidationHintComponent } from '../../../shared/uis/form-validation-hint/form-validation-hint.component';
import { PasswordToggleDirective } from '../../../shared/directives/password-toggle.directive';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    PasswordToggleDirective,
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    TranslocoModule,
    FormValidationHintComponent,
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly form = this.fb.group(
    {
      name: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      emailConfirm: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
      consent: [false, Validators.requiredTrue],
    },
    { validators: [passwordMatchValidator, emailMatchValidator] },
  );

  readonly isLoading = signal(false);
  readonly emailConflictError = signal(false);
  readonly emailSuggestion = signal<string | null>(null);

  get nameControl() { return this.form.get('name'); }
  get emailControl() { return this.form.get('email'); }
  get emailConfirmControl() { return this.form.get('emailConfirm'); }
  get passwordControl() { return this.form.get('password'); }
  get confirmPasswordControl() { return this.form.get('confirmPassword'); }
  get consentControl() { return this.form.get('consent'); }

  constructor() {
    this.emailControl?.valueChanges
      .pipe(debounceTime(500), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.emailSuggestion.set(suggestEmail(value ?? ''));
      });
  }

  applyEmailSuggestion(): void {
    const suggestion = this.emailSuggestion();
    if (!suggestion) return;
    this.emailControl?.setValue(suggestion);
    this.emailSuggestion.set(null);
  }

  onConsentLinkClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    const anchor = target.closest('a');
    if (!anchor) return;
    event.preventDefault();
    event.stopPropagation();
    const href = anchor.getAttribute('href');
    if (href) {
      window.open(href, '_blank', 'noopener,noreferrer');
    }
  }

  onSubmit(): void {
    if (this.form.invalid) {
      focusFirstInvalid(this.form);
      return;
    }

    this.isLoading.set(true);
    this.emailConflictError.set(false);

    const { name, email, password } = this.form.value;

    this.authService.registerClient(name!, email!, password!).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.authService.navigateByRole();
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        if (err.status === 409) {
          this.emailConflictError.set(true);
          this.emailControl?.setErrors({ conflict: true });
          this.emailControl?.markAsTouched();
        }
      },
    });
  }
}
