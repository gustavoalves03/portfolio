import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { focusFirstInvalid } from '../../../core/utils/form-focus.util';
import { FormValidationHintComponent } from '../../../shared/uis/form-validation-hint/form-validation-hint.component';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoModule,
    FormValidationHintComponent,
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss',
})
export class ForgotPasswordComponent {
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  // Signals required for zoneless change detection — plain class fields
  // do not trigger re-render after async callbacks (subscribe next/error).
  readonly isLoading = signal(false);
  readonly emailSent = signal(false);

  get emailControl() { return this.form.get('email'); }

  onSubmit(): void {
    if (this.form.invalid) {
      focusFirstInvalid(this.form);
      return;
    }

    this.isLoading.set(true);

    this.authService.requestPasswordReset(this.form.value.email!).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.emailSent.set(true);
      },
      error: () => {
        this.isLoading.set(false);
        this.emailSent.set(true);
      },
    });
  }
}
