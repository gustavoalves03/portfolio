import { Component, inject, OnInit } from '@angular/core';
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
  ],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss',
})
export class ResetPasswordComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', [Validators.required]],
  });

  token: string | null = null;
  isLoading = false;
  resetSuccess = false;
  invalidToken = false;

  get newPasswordControl() { return this.form.get('newPassword'); }
  get confirmPasswordControl() { return this.form.get('confirmPassword'); }
  get passwordsMismatch(): boolean {
    return this.form.value.newPassword !== this.form.value.confirmPassword
      && !!this.confirmPasswordControl?.touched;
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.invalidToken = true;
    }
  }

  onSubmit(): void {
    if (this.form.invalid || this.passwordsMismatch || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.invalidToken = false;

    this.authService.resetPassword(this.token, this.form.value.newPassword!).subscribe({
      next: () => {
        this.isLoading = false;
        this.resetSuccess = true;
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading = false;
        this.invalidToken = true;
      },
    });
  }
}
