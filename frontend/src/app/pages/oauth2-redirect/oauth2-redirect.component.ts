import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-oauth2-redirect',
  standalone: true,
  template: `
    <div class="flex items-center justify-center min-h-screen">
      <div class="text-center">
        <p class="text-lg">Authentification en cours...</p>
      </div>
    </div>
  `
})
export class OAuth2RedirectComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  ngOnInit(): void {
    // Get token from query params
    const token = this.route.snapshot.queryParamMap.get('token');
    const error = this.route.snapshot.queryParamMap.get('error');

    if (token) {
      // Handle successful authentication
      this.authService.handleOAuth2Callback(token).subscribe({
        next: () => {
          // Redirect to home or previous page
          this.router.navigate(['/']);
        },
        error: (err) => {
          console.error('OAuth2 callback error:', err);
          this.router.navigate(['/'], { queryParams: { authError: 'failed' } });
        }
      });
    } else if (error) {
      // Handle authentication error
      console.error('OAuth2 error:', error);
      this.router.navigate(['/'], { queryParams: { authError: error } });
    } else {
      // No token or error, redirect to home
      this.router.navigate(['/']);
    }
  }
}
