import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { NotificationsStore } from '../../../features/notifications/store/notifications.store';

interface BottomTab {
  id: string;
  labelKey: string;
  path: string;
  icon: string;
  isCamera?: boolean;
}

@Component({
  selector: 'app-bottom-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule, TranslocoPipe],
  template: `
    @if (tabs().length > 0) {
      <nav class="bottom-nav" aria-label="Navigation mobile">
        @for (tab of tabs(); track tab.id) {
          @if (tab.isCamera) {
            <a [routerLink]="tab.path" class="camera-btn" routerLinkActive="active">
              <mat-icon>photo_camera</mat-icon>
            </a>
          } @else {
            <a [routerLink]="tab.path" class="tab" routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: tab.path === '/'}">
              <span class="tab-icon-wrapper">
                <mat-icon>{{ tab.icon }}</mat-icon>
                @if (tab.id.endsWith('-bookings') && notificationsStore.hasUnread()) {
                  <span class="tab-badge">
                    @if (notificationsStore.unreadCount() > 1) {
                      {{ notificationsStore.badgeLabel() }}
                    }
                  </span>
                }
              </span>
              <span class="tab-label">{{ tab.labelKey | transloco }}</span>
            </a>
          }
        }
      </nav>
    }
  `,
  styles: `
    :host {
      display: none;
    }

    @media (max-width: 767px) {
      :host {
        display: block;
        position: fixed;
        bottom: 0;
        left: 0;
        right: 0;
        z-index: 50;
      }
    }

    .bottom-nav {
      display: flex;
      align-items: center;
      justify-content: space-around;
      height: 56px;
      background: #fff;
      border-top: 1px solid #f0f0f0;
      padding-bottom: env(safe-area-inset-bottom);
    }

    .tab {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      text-decoration: none;
      color: #999;
      transition: color 150ms;

      mat-icon {
        font-size: 22px;
        width: 22px;
        height: 22px;
      }

      .tab-label {
        font-size: 9px;
        font-weight: 500;
      }

      &.active {
        color: #c06;

        mat-icon {
          background: #fef2f8;
          border-radius: 12px;
          padding: 2px 12px;
          width: auto;
          height: auto;
        }
      }
    }

    .camera-btn {
      flex: 1;
      display: flex;
      justify-content: center;
      text-decoration: none;

      mat-icon {
        width: 42px;
        height: 42px;
        font-size: 22px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 50%;
        background: linear-gradient(135deg, #a8385d, #c06);
        color: #fff;
        margin-top: -10px;
        box-shadow: 0 3px 10px rgba(192, 0, 102, 0.25);
      }
    }

    .tab-icon-wrapper {
      position: relative;
      display: inline-flex;
    }

    .tab-badge {
      position: absolute;
      top: -4px;
      right: -6px;
      min-width: 12px;
      height: 12px;
      background: #e11d48;
      border-radius: 9px;
      border: 2px solid white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 9px;
      font-weight: 700;
      color: white;
      padding: 0 3px;
      box-shadow: 0 1px 3px rgba(225, 29, 72, 0.4);
    }

    .tab-badge:empty {
      min-width: 12px;
      padding: 0;
    }
  `
})
export class BottomNavComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly notificationsStore = inject(NotificationsStore);
  private readonly salonService = inject(SalonProfileService);
  private readonly currentUrl = signal('');
  private readonly salonSlug = signal('');

  private readonly role = computed(() => this.authService.user()?.role);
  private readonly isAuthenticated = this.authService.isAuthenticated;

  constructor() {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: NavigationEnd) => {
      this.currentUrl.set(event.urlAfterRedirects);
    });

    // Load salon slug for PRO users
    effect(() => {
      const role = this.role();
      if ((role === Role.PRO || role === Role.ADMIN || role === Role.EMPLOYEE) && this.authService.isAuthenticated()) {
        this.salonService.getProfile().subscribe({
          next: (tenant) => this.salonSlug.set(tenant.slug),
          error: () => this.salonSlug.set(''),
        });
      }
    });
  }

  readonly tabs = computed<BottomTab[]>(() => {
    const role = this.role();

    // Hide bottom nav on management/settings pages
    const url = this.currentUrl();
    const managePaths = [
      '/pro/manage',
      '/pro/settings',
      '/pro/planning',
      '/pro/employees',
      '/pro/cares',
      '/pro/dashboard',
      '/pro/clients/',
      '/employee/leaves',
      '/employee/documents',
    ];
    if (managePaths.some(p => url.startsWith(p))) {
      return [];
    }

    // PRO / ADMIN
    if (role === Role.PRO || role === Role.ADMIN) {
      return [
        { id: 'pro-bookings', labelKey: 'bottomNav.pro.bookings', path: '/pro/bookings', icon: 'calendar_today' },
        { id: 'pro-my-posts', labelKey: 'bottomNav.pro.myPosts', path: '/pro/posts', icon: 'photo_library' },
        { id: 'pro-camera', labelKey: '', path: '/pro/camera', icon: 'photo_camera', isCamera: true },
        { id: 'pro-feed', labelKey: 'bottomNav.pro.feed', path: '/feed', icon: 'auto_awesome' },
        { id: 'pro-salon', labelKey: 'bottomNav.pro.salon', path: this.salonSlug() ? '/salon/' + this.salonSlug() : '/pro/salon', icon: 'storefront' },
      ];
    }

    // EMPLOYEE
    if (role === Role.EMPLOYEE) {
      return [
        { id: 'emp-bookings', labelKey: 'bottomNav.employee.bookings', path: '/employee/bookings', icon: 'event' },
        { id: 'emp-leaves', labelKey: 'bottomNav.employee.leaves', path: '/employee/leaves', icon: 'beach_access' },
        { id: 'emp-documents', labelKey: 'bottomNav.employee.documents', path: '/employee/documents', icon: 'folder' },
      ];
    }

    // Client connected
    if (this.isAuthenticated()) {
      return [
        { id: 'client-bookings', labelKey: 'bottomNav.client.bookings', path: '/bookings', icon: 'calendar_today' },
        { id: 'client-salons', labelKey: 'bottomNav.client.salons', path: '/discover', icon: 'search' },
        { id: 'client-posts', labelKey: 'bottomNav.client.posts', path: '/feed', icon: 'auto_awesome' },
        { id: 'client-evolution', labelKey: 'bottomNav.client.evolution', path: '/my-evolution', icon: 'star' },
      ];
    }

    // Prospect (not logged in)
    return [
      { id: 'prospect-home', labelKey: 'bottomNav.prospect.home', path: '/', icon: 'home' },
      { id: 'prospect-salons', labelKey: 'bottomNav.prospect.salons', path: '/discover', icon: 'search' },
      { id: 'prospect-posts', labelKey: 'bottomNav.prospect.posts', path: '/feed', icon: 'auto_awesome' },
    ];
  });
}
