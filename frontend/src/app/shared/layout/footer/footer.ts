import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { LpLogoComponent } from '../../uis/lp-logo/lp-logo.component';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink, TranslocoPipe, LpLogoComponent],
  templateUrl: './footer.html',
  styleUrl: './footer.scss',
})
export class Footer {
  private readonly authService = inject(AuthService);

  readonly year = new Date().getFullYear();

  readonly isPro = computed(() => {
    const role = this.authService.user()?.role;
    return role === Role.PRO || role === Role.ADMIN;
  });

  readonly isLoggedIn = this.authService.isAuthenticated;

  /** Logged-in non-pro user (regular client). Sees the full footer with proSpace link. */
  readonly isClient = computed(() => this.isLoggedIn() && !this.isPro());
}
