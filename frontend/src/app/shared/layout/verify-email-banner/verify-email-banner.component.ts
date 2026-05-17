import { Component, inject, signal, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-verify-email-banner',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, TranslocoModule],
  templateUrl: './verify-email-banner.component.html',
  styleUrl: './verify-email-banner.component.scss',
})
export class VerifyEmailBannerComponent implements OnDestroy {
  private auth = inject(AuthService);
  private snackBar = inject(MatSnackBar);
  private transloco = inject(TranslocoService);

  user = this.auth.user;
  cooldownSec = signal(0);
  loading = signal(false);

  private intervalId: ReturnType<typeof setInterval> | null = null;

  resend(): void {
    if (this.loading() || this.cooldownSec() > 0) return;
    this.loading.set(true);
    this.auth.sendVerification().subscribe({
      next: () => {
        this.snackBar.open(
          this.transloco.translate('verifyEmail.required.resendCooldown'),
          'OK',
          { duration: 4000 }
        );
        this.startCooldown(60);
        this.loading.set(false);
      },
      error: (err) => {
        if (err.status === 429) {
          this.startCooldown(err.error?.retryAfter ?? 60);
        }
        this.loading.set(false);
      },
    });
  }

  private startCooldown(sec: number): void {
    this.cooldownSec.set(sec);
    if (this.intervalId) clearInterval(this.intervalId);
    this.intervalId = setInterval(() => {
      const next = this.cooldownSec() - 1;
      if (next <= 0) {
        if (this.intervalId) clearInterval(this.intervalId);
        this.intervalId = null;
        this.cooldownSec.set(0);
      } else {
        this.cooldownSec.set(next);
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.intervalId) clearInterval(this.intervalId);
  }
}
