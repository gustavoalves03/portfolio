import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-verify-email-required',
  standalone: true,
  imports: [CommonModule, MatButtonModule, TranslocoModule],
  templateUrl: './verify-email-required.component.html',
  styleUrl: './verify-email-required.component.scss',
})
export class VerifyEmailRequiredComponent {
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  readonly user = this.auth.user;
  readonly cooldownSec = signal(0);

  resend() {
    this.auth.sendVerification().subscribe({
      next: () => {
        this.snackBar.open(
          this.transloco.translate('verifyEmail.required.resendCooldown'),
          'OK',
          { duration: 4000 },
        );
        this.startCooldown(60);
      },
      error: (err) => {
        if (err.status === 429) {
          this.startCooldown(err.error?.retryAfter ?? 60);
        }
      },
    });
  }

  logout() {
    this.auth.logout();
  }

  private startCooldown(sec: number) {
    this.cooldownSec.set(sec);
    const interval = setInterval(() => {
      const next = this.cooldownSec() - 1;
      if (next <= 0) {
        clearInterval(interval);
        this.cooldownSec.set(0);
      } else {
        this.cooldownSec.set(next);
      }
    }, 1000);
  }
}
