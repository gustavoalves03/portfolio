import { Component, computed, inject, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  CLIENT_NAVIGATION_ROUTES,
  EMPLOYEE_NAVIGATION_ROUTES,
  NAVIGATION_ROUTES,
  NavigationRoute,
  PRO_NAVIGATION_ROUTES,
} from './navigation-routes';
import { LangService } from '../../../i18n/lang.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { TenantFeaturesService } from '../../../core/tenant/tenant-features.service';
import { TenantStatusService } from '../../../core/tenant/tenant-status.service';

@Component({
  selector: 'app-sidenav-menu',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule, MatTooltipModule, TranslocoPipe],
  templateUrl: './sidenav-menu.html',
  styleUrl: './sidenav-menu.scss',
})
export class SidenavMenu {
  protected readonly authService = inject(AuthService);
  protected readonly langService = inject(LangService);
  protected readonly featuresService = inject(TenantFeaturesService);
  protected readonly tenantStatus = inject(TenantStatusService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  protected readonly languages = [
    { code: 'fr', label: 'Français', flag: '🇫🇷' },
    { code: 'en', label: 'English', flag: '🇬🇧' },
  ];

  protected readonly routes = computed<NavigationRoute[]>(() => {
    const user = this.authService.user();
    const role = user?.role;
    const isPro = role === Role.PRO || role === Role.ADMIN;
    const isEmployee = role === Role.EMPLOYEE;

    // PRO: only show pro routes (no public Accueil/À propos)
    // Filter out the employees route when the feature is disabled
    if (isPro) {
      const employeesEnabled = this.featuresService.employeesEnabled();
      return PRO_NAVIGATION_ROUTES.filter(
        (r) => r.path !== '/pro/employees' || employeesEnabled
      );
    }

    // EMPLOYEE: only show employee routes
    if (isEmployee) {
      return [...EMPLOYEE_NAVIGATION_ROUTES];
    }

    // Public/Client: show public routes + client routes if authenticated
    const base = [...NAVIGATION_ROUTES];
    if (user) {
      base.push(...CLIENT_NAVIGATION_ROUTES);
    }
    return base;
  });

  readonly linkClicked = output<void>();

  protected onLinkClick(): void {
    this.linkClicked.emit();
  }

  protected setLanguage(lang: string): void {
    if (this.langService.active() === lang) {
      return;
    }
    this.langService.set(lang);
  }

  protected onLockedClick(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (typeof window !== 'undefined' && window.matchMedia('(hover: none)').matches) {
      this.snackBar.open(
        this.transloco.translate('nav.lockedUntilPublished'),
        undefined,
        { duration: 2500 },
      );
    }
  }
}
