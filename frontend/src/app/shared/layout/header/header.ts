import { Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { SidenavService } from '../navigation/sidenav.service';
import { SidenavOverlay } from '../navigation/sidenav-overlay';
import { BookingsDrawerComponent } from './bookings-drawer/bookings-drawer.component';
import { LoginModalComponent } from '../../modals/login-modal/login-modal.component';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, SidenavOverlay, BookingsDrawerComponent, MatMenuModule, MatButtonModule, TranslocoPipe],
  templateUrl: './header.html',
  styleUrl: './header.scss'
})
export class Header {
  protected readonly sidenavService = inject(SidenavService);
  protected readonly authService = inject(AuthService);
  protected readonly dialog = inject(MatDialog);
  protected readonly bookingsDrawer = viewChild.required(BookingsDrawerComponent);

  protected readonly isPro = computed(() => {
    const role = this.authService.user()?.role;
    return role === Role.PRO || role === Role.ADMIN || role === Role.EMPLOYEE;
  });

  protected readonly salonName = signal('');
  protected readonly salonSlug = signal('');
  private readonly salonService = inject(SalonProfileService);

  constructor() {
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
  }

  protected toggleSidenav(): void {
    this.sidenavService.toggle();
  }

  protected toggleBookingsDrawer(): void {
    this.bookingsDrawer().toggle();
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
