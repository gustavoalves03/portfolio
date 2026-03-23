import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-oauth2-redirect',
  standalone: true,
  imports: [MatProgressSpinnerModule],
  template: `
    <div class="flex justify-center items-center" style="min-height: 100vh;">
      <mat-spinner diameter="48"></mat-spinner>
    </div>
  `,
})
export class OAuth2RedirectComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    const error = this.route.snapshot.queryParamMap.get('error');

    if (token) {
      this.authService.handleOAuth2Callback(token).subscribe({
        next: () => {
          this.authService.navigateByRole();
        },
        error: () => {
          this.router.navigate(['/login']);
        },
      });
    } else if (error) {
      const decodedError = decodeURIComponent(error);
      this.router.navigate(['/login'], { state: { oauthError: decodedError } });
    } else {
      this.router.navigate(['/login']);
    }
  }
}
