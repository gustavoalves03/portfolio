import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { suggestEmail } from '../../../core/utils/email-mailcheck.util';
import { PasswordToggleDirective } from '../../directives/password-toggle.directive';

export interface ProSignupModalData {
  tier: 'VITRINE' | 'GESTION' | 'PREMIUM';
  billing: 'MONTHLY' | 'YEARLY';
}

export interface ProSignupModalResult {
  authenticated: boolean;
  switchToLogin?: boolean;
}

@Component({
  selector: 'app-pro-signup-modal',
  standalone: true,
  imports: [
    PasswordToggleDirective,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  templateUrl: './pro-signup-modal.component.html',
  styleUrl: './pro-signup-modal.component.scss',
})
export class ProSignupModalComponent {
  private readonly authService = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<ProSignupModalComponent>);
  private readonly data = inject<ProSignupModalData>(MAT_DIALOG_DATA);

  readonly tier = this.data.tier;
  readonly billing = this.data.billing;

  readonly name = signal('');
  readonly email = signal('');
  readonly emailConfirm = signal('');
  readonly password = signal('');
  readonly consent = signal(false);
  readonly isLoading = signal(false);
  readonly errorKey = signal<string | null>(null);
  readonly emailSuggestion = signal<string | null>(null);
  private emailDebounceHandle: ReturnType<typeof setTimeout> | null = null;

  readonly emailMismatch = computed(
    () =>
      this.email().length > 0 &&
      this.emailConfirm().length > 0 &&
      this.email().trim().toLowerCase() !== this.emailConfirm().trim().toLowerCase(),
  );

  onEmailChange(value: string): void {
    this.email.set(value);
    if (this.emailDebounceHandle) clearTimeout(this.emailDebounceHandle);
    this.emailDebounceHandle = setTimeout(() => {
      this.emailSuggestion.set(suggestEmail(value));
    }, 500);
  }

  applyEmailSuggestion(): void {
    const suggestion = this.emailSuggestion();
    if (!suggestion) return;
    this.email.set(suggestion);
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

  isFormValid(): boolean {
    return this.name().trim().length > 0
      && this.email().includes('@')
      && this.emailConfirm().includes('@')
      && !this.emailMismatch()
      && this.password().length >= 8
      && this.consent();
  }

  submit(): void {
    if (!this.isFormValid()) return;
    if (this.isLoading()) return;

    this.isLoading.set(true);
    this.errorKey.set(null);

    this.authService.registerPro({
      name: this.name().trim(),
      email: this.email().trim(),
      password: this.password(),
      tier: this.tier,
      billing: this.billing,
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.dialogRef.close({ authenticated: true } as ProSignupModalResult);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        if (err.status === 409) {
          this.errorKey.set('proSignup.modal.errors.emailAlreadyInUse');
        } else {
          this.errorKey.set('proSignup.modal.errors.networkError');
        }
      },
    });
  }

  openLogin(): void {
    this.dialogRef.close({ authenticated: false, switchToLogin: true } as ProSignupModalResult);
  }

  close(): void {
    this.dialogRef.close({ authenticated: false } as ProSignupModalResult);
  }
}
