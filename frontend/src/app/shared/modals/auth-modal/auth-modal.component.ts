import { Component, inject, signal } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';
import { focusFirstInvalid } from '../../../core/utils/form-focus.util';
import { FormValidationHintComponent } from '../../uis/form-validation-hint/form-validation-hint.component';
import { SheetHandleComponent } from '../../uis/sheet-handle/sheet-handle.component';

export interface AuthModalResult {
  authenticated: boolean;
  action: 'login' | 'register';
}

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    ReactiveFormsModule,
    TranslocoPipe,
    SheetHandleComponent,
    FormValidationHintComponent,
  ],
  templateUrl: './auth-modal.component.html',
  styleUrl: './auth-modal.component.scss',
})
export class AuthModalComponent {
  private readonly authService = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<AuthModalComponent>);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly activeTab = signal(0);
  readonly isLoading = signal(false);
  readonly errorKey = signal<string | null>(null);

  readonly loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly registerForm = this.fb.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    consent: [false, Validators.requiredTrue],
  });

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

  onLogin(): void {
    if (this.loginForm.invalid) {
      focusFirstInvalid(this.loginForm);
      return;
    }

    this.isLoading.set(true);
    this.errorKey.set(null);

    const { email, password } = this.loginForm.value;

    this.authService.loginWithCredentials(email!, password!).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.dialogRef.close({ authenticated: true, action: 'login' } as AuthModalResult);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        if (err.status === 423) {
          this.errorKey.set('auth.errors.accountLocked');
        } else if (err.status === 401 || err.status === 403) {
          this.errorKey.set('auth.errors.invalidCredentials');
        } else {
          this.errorKey.set('auth.errors.networkError');
        }
      },
    });
  }

  onRegister(): void {
    if (this.registerForm.invalid) {
      focusFirstInvalid(this.registerForm);
      return;
    }

    this.isLoading.set(true);
    this.errorKey.set(null);

    const { name, email, password } = this.registerForm.value;

    this.authService.registerClient(name!, email!, password!).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.dialogRef.close({ authenticated: true, action: 'register' } as AuthModalResult);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        if (err.status === 409) {
          this.errorKey.set('auth.errors.emailAlreadyInUse');
        } else {
          this.errorKey.set('auth.errors.networkError');
        }
      },
    });
  }

  loginWithGoogle(): void {
    this.dialogRef.close();
    this.authService.loginWithGoogle('client');
  }

  switchToRegister(): void {
    this.activeTab.set(1);
    this.errorKey.set(null);
  }

  switchToLogin(): void {
    this.activeTab.set(0);
    this.errorKey.set(null);
  }

  goToForgotPassword(): void {
    this.dialogRef.close();
    this.router.navigate(['/forgot-password']);
  }

  close(): void {
    this.dialogRef.close();
  }
}
