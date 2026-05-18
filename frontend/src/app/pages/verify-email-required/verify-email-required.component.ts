import { Component, effect, inject, OnDestroy, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
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
export class VerifyEmailRequiredComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly platformId = inject(PLATFORM_ID);

  readonly user = this.auth.user;
  readonly cooldownSec = signal(0);
  private pollHandle: ReturnType<typeof setInterval> | null = null;
  private focusHandler = () => this.auth.refreshCurrentUser();

  constructor() {
    // As soon as the cached user becomes verified (either because this tab
    // refetched /me on focus, or another tab's verifyEmail() success pushed
    // the new state via our signal), bounce the pro to the dashboard.
    effect(() => {
      const u = this.user();
      if (u?.emailVerified) {
        this.router.navigate(['/pro/dashboard']);
      }
    });
  }

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    // Refetch /api/auth/me whenever the tab regains focus — covers the common
    // case where the user clicks the verification link in another tab, gets
    // verified server-side, then comes back to this tab to keep working.
    window.addEventListener('focus', this.focusHandler);

    // Fallback poll every 10s in case the user never refocuses (e.g. left
    // the tab open in background after clicking the email link elsewhere).
    this.pollHandle = setInterval(() => this.auth.refreshCurrentUser(), 10_000);
  }

  ngOnDestroy(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    window.removeEventListener('focus', this.focusHandler);
    if (this.pollHandle) clearInterval(this.pollHandle);
  }

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
