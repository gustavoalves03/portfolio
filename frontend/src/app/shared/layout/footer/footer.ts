import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './footer.html',
  styleUrl: './footer.scss'
})
export class Footer {
  private readonly authService = inject(AuthService);
  readonly year = new Date().getFullYear();
  readonly isPro = computed(() => {
    const role = this.authService.user()?.role;
    return role === Role.PRO || role === Role.ADMIN;
  });
  readonly isLoggedIn = this.authService.isAuthenticated;
}
