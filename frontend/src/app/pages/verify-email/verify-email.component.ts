import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoModule } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';

type VerifyState = 'pending' | 'success' | 'expired' | 'invalid';

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

  readonly state = signal<VerifyState>('pending');

  ngOnInit() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('invalid');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
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
