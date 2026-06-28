import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { focusFirstInvalid } from '../../../core/utils/form-focus.util';
import { FormValidationHintComponent } from '../../../shared/uis/form-validation-hint/form-validation-hint.component';
import { PasswordToggleDirective } from '../../../shared/directives/password-toggle.directive';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    PasswordToggleDirective,
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    TranslocoModule,
    FormValidationHintComponent,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  isLoading = false;
  invalidCredentialsError = false;
  networkError = false;
  accountLockedError = false;
  lockoutMinutes = 0;
  // Read OAuth error passed via router state from OAuth2RedirectComponent
  oauthError: string | null = (() => {
    const nav = this.router.getCurrentNavigation();
    return (nav?.extras?.state?.['oauthError'] as string) ?? null;
  })();

  get emailControl() { return this.form.get('email'); }
  get passwordControl() { return this.form.get('password'); }

  onSubmit(): void {
    if (this.form.invalid) {
      focusFirstInvalid(this.form);
      return;
    }

    this.isLoading = true;
    this.invalidCredentialsError = false;
    this.networkError = false;
    this.accountLockedError = false;
    this.oauthError = null;

    const { email, password } = this.form.value;

    this.authService.loginWithCredentials(email!, password!).subscribe({
      next: () => {
        this.isLoading = false;
        this.authService.navigateByRole();
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        if (err.status === 423) {
          this.accountLockedError = true;
          this.lockoutMinutes = Math.ceil((err.error?.retryAfterSeconds ?? 900) / 60);
        } else if (err.status === 401 || err.status === 403) {
          this.invalidCredentialsError = true;
        } else {
          this.networkError = true;
        }
      },
    });
  }

  loginWithGoogle(): void {
    // Generic login page — the user logs in (or first-time signs up) as a
    // client. Pro sign-up uses /register/pro with its own form.
    this.authService.loginWithGoogle('client');
  }
}
