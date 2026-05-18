import { Component, inject, OnInit, PLATFORM_ID, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';

type VerifyState = 'confirm' | 'pending' | 'success' | 'expired' | 'invalid';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    TranslocoModule,
  ],
  templateUrl: './verify-email.component.html',
  styleUrl: './verify-email.component.scss',
})
export class VerifyEmailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly platformId = inject(PLATFORM_ID);

  readonly state = signal<VerifyState>('confirm');
  private token: string | null = null;

  ngOnInit() {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.state.set('invalid');
    }
  }

  verify() {
    if (!this.token || this.state() === 'pending') return;
    this.state.set('pending');
    this.auth.verifyEmail(this.token).subscribe({
      next: () => {
        this.state.set('success');
        setTimeout(() => this.router.navigate(['/']), 2000);
      },
      error: (err) => {
        const code = err?.error?.error || err?.error?.message;
        if (code === 'TOKEN_EXPIRED' || (typeof code === 'string' && code.includes('expired'))) {
          this.state.set('expired');
        } else {
          this.state.set('invalid');
        }
      },
    });
  }

  resend() {
    this.auth.sendVerification().subscribe();
  }
}
