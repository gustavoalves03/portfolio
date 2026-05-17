import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router, NavigationEnd, RouterLink } from '@angular/router';
import { Location } from '@angular/common';
import { filter } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { LpLogoComponent } from '../../uis/lp-logo/lp-logo.component';
import { SidenavService } from '../navigation/sidenav.service';
import { SidenavOverlay } from '../navigation/sidenav-overlay';
import { LoginModalComponent } from '../../modals/login-modal/login-modal.component';
import { bottomSheetConfig } from '../../uis/sheet-handle/bottom-sheet.config';
import { AccountSwitcherModalComponent } from './account-switcher-modal/account-switcher-modal.component';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { NotificationsStore } from '../../../features/notifications/store/notifications.store';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, SidenavOverlay, MatMenuModule, MatButtonModule, MatIconModule, TranslocoPipe, LpLogoComponent],
  templateUrl: './header.html',
  styleUrl: './header.scss'
})
export class Header {
  protected readonly sidenavService = inject(SidenavService);
  protected readonly authService = inject(AuthService);
  protected readonly notificationsStore = inject(NotificationsStore);
  protected readonly dialog = inject(MatDialog);
  protected readonly Role = Role;
  protected readonly isPro = computed(() => {
    // Read user() to subscribe the computed to currentUser changes.
    this.authService.user();
    return this.authService.hasRole(Role.PRO, Role.ADMIN, Role.EMPLOYEE);
  });

  // Caret next to the brand is shown only when the user is authenticated and
  // has at least one tenant they could be a member of — i.e. switching
  // between "Mode Client" and a salon is a meaningful action.
  protected readonly canSwitchAccount = computed(
    () => this.authService.isAuthenticated() && this.authService.availableTenants().length >= 1,
  );

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
    // Load PRO salon info. We only fetch once per (pro, authenticated) state
    // change and we never blank the cached name on a transient flicker — the
    // computed user signal can momentarily flip to null between route guards,
    // which used to make the brand fall back to "LuxPretty".
    let lastFetchedFor: string | null = null;
    effect(() => {
      const user = this.authService.user();
      const authed = this.authService.isAuthenticated();
      const pro = this.isPro();
      const key = authed && pro && user ? String(user.id) : null;

      if (key === lastFetchedFor) return;
      lastFetchedFor = key;

      if (key === null) {
        // Real auth-state change away from a pro: drop the cached salon.
        this.salonName.set('');
        this.salonSlug.set('');
        return;
      }

      this.salonService.getProfile().subscribe({
        next: (tenant) => {
          this.salonName.set(tenant.name);
          this.salonSlug.set(tenant.slug);
        },
        // On error, keep whatever name we already had — refusing to overwrite
        // with '' avoids the QA-reported flicker to the brand fallback.
        error: () => {
          if (!this.salonName()) {
            this.salonName.set('');
            this.salonSlug.set('');
          }
        },
      });
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
    this.dialog.open(LoginModalComponent, bottomSheetConfig({
      width: '500px',
      disableClose: false,
    }));
  }

  protected openAccountSwitcher(): void {
    this.dialog.open(AccountSwitcherModalComponent, bottomSheetConfig({
      width: '420px',
      autoFocus: false,
    }));
  }

  protected logout(): void {
    this.authService.logout();
  }
}
