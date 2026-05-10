import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { SheetHandleComponent } from '../../uis/sheet-handle/sheet-handle.component';

@Component({
  selector: 'app-login-modal',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    TranslocoPipe,
    SheetHandleComponent,
  ],
  templateUrl: './login-modal.component.html',
  styleUrl: './login-modal.component.scss'
})
export class LoginModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<LoginModalComponent>);
  private readonly router = inject(Router);

  loading = signal(false);
  error = signal<string | null>(null);

  loginForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  login(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    const { email, password } = this.loginForm.value;

    this.authService.loginWithCredentials(email!, password!).subscribe({
      next: () => {
        this.loading.set(false);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'auth.errors.invalidCredentials');
      }
    });
  }

  goToRegister(): void {
    this.dialogRef.close();
    this.router.navigateByUrl('/register');
  }

  loginWithGoogle(): void {
    // The Google flow is a full-page redirect, so closing the dialog before
    // leaving keeps the snackbar/dialog stack clean if the browser caches
    // the route after the OAuth round-trip. Pass 'client' explicitly: the
    // header login modal must never silently provision a salon tenant; pros
    // sign up via the dedicated /register/pro flow.
    this.dialogRef.close();
    this.authService.loginWithGoogle('client');
  }

  close(): void {
    this.dialogRef.close();
  }
}
