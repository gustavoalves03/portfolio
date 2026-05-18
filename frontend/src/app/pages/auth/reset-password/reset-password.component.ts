import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { passwordMatchValidator } from '../../../core/auth/password-match.validator';
import { focusFirstInvalid } from '../../../core/utils/form-focus.util';
import { FormValidationHintComponent } from '../../../shared/uis/form-validation-hint/form-validation-hint.component';

@Component({
  selector: 'app-reset-password',
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
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group(
    {
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: [passwordMatchValidator] },
  );

  token: string | null = null;
  // Signals required for zoneless change detection — plain class fields
  // do not trigger re-render after async callbacks (subscribe next/error).
  readonly isLoading = signal(false);
  readonly resetSuccess = signal(false);
  readonly invalidToken = signal(false);

  get passwordControl() { return this.form.get('password'); }
  get confirmPasswordControl() { return this.form.get('confirmPassword'); }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.invalidToken.set(true);
    }
  }

  onSubmit(): void {
    if (this.form.invalid || !this.token) {
      focusFirstInvalid(this.form);
      return;
    }

    this.isLoading.set(true);
    this.invalidToken.set(false);

    this.authService.resetPassword(this.token, this.form.value.password!).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.resetSuccess.set(true);
      },
      error: (_err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.invalidToken.set(true);
      },
    });
  }
}
