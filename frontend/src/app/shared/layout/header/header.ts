import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router, NavigationEnd, RouterLink } from '@angular/router';
import { Location } from '@angular/common';
import { filter } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { SidenavService } from '../navigation/sidenav.service';
import { SidenavOverlay } from '../navigation/sidenav-overlay';
import { LoginModalComponent } from '../../modals/login-modal/login-modal.component';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { NotificationsStore } from '../../../features/notifications/store/notifications.store';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, SidenavOverlay, MatMenuModule, MatButtonModule, MatIconModule, TranslocoPipe],
  templateUrl: './header.html',
  styleUrl: './header.scss'
})
export class Header {
  protected readonly sidenavService = inject(SidenavService);
  protected readonly authService = inject(AuthService);
  protected readonly notificationsStore = inject(NotificationsStore);
  protected readonly dialog = inject(MatDialog);
  protected readonly isPro = computed(() => {
    const role = this.authService.user()?.role;
    return role === Role.PRO || role === Role.ADMIN || role === Role.EMPLOYEE;
  });

  protected readonly salonName = signal('');
  protected readonly salonSlug = signal('');
  private readonly salonService = inject(SalonProfileService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  // Detect management pages (no bottom nav, show back arrow on mobile)
  protected readonly isManagePage = signal(false);
  private static readonly MANAGE_PATHS = [
    '/pro/manage', '/pro/settings', '/pro/planning', '/pro/employees',
    '/pro/cares', '/pro/dashboard', '/pro/clients/',
    '/employee/leaves', '/employee/documents',
  ];

  // Salon name shown when visiting /salon/:slug as a client
  protected readonly visitingSalonName = signal('');
  protected readonly visitingSalonSlug = signal('');

  protected readonly headerBrand = computed(() => {
    // PRO/EMPLOYEE/ADMIN: show their own salon name
    if (this.isPro() && this.salonName()) {
      return { name: this.salonName(), slug: this.salonSlug(), isPro: true };
    }
    // Client visiting a salon page: show that salon's name
    if (this.visitingSalonName()) {
      return { name: this.visitingSalonName(), slug: this.visitingSalonSlug(), isPro: false };
    }
    return null;
  });

  constructor() {
    // Load PRO salon info
    effect(() => {
      if (this.isPro() && this.authService.isAuthenticated()) {
        this.salonService.getProfile().subscribe({
          next: (tenant) => {
            this.salonName.set(tenant.name);
            this.salonSlug.set(tenant.slug);
          },
          error: () => {
            this.salonName.set('');
            this.salonSlug.set('');
          },
        });
      } else {
        this.salonName.set('');
        this.salonSlug.set('');
      }
    });

    // Detect current route for manage pages and salon visits
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd)
    ).subscribe((e) => {
      const url = e.urlAfterRedirects;

      // Detect manage pages (show back arrow on mobile)
      this.isManagePage.set(
        Header.MANAGE_PATHS.some(p => url.startsWith(p))
      );

      const match = url.match(/^\/salon\/([^/?]+)/);
      if (match) {
        const slug = match[1];
        this.visitingSalonSlug.set(slug);
        this.salonService.getPublicSalon(slug).subscribe({
          next: (salon) => this.visitingSalonName.set(salon.name),
          error: () => this.visitingSalonName.set(''),
        });
      } else {
        this.visitingSalonName.set('');
        this.visitingSalonSlug.set('');
      }
    });
  }

  protected goBack(): void {
    this.location.back();
  }

  protected toggleSidenav(): void {
    this.sidenavService.toggle();
  }

  protected openLoginModal(): void {
    this.dialog.open(LoginModalComponent, {
      width: '500px',
      disableClose: false
    });
  }

  protected logout(): void {
    this.authService.logout();
  }
}
