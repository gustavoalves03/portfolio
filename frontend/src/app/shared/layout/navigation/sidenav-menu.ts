import { Component, computed, inject, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import {
  CLIENT_NAVIGATION_ROUTES,
  NAVIGATION_ROUTES,
  NavigationRoute,
  PRO_NAVIGATION_ROUTES,
} from './navigation-routes';
import { LangService } from '../../../i18n/lang.service';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';

@Component({
  selector: 'app-sidenav-menu',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule],
  templateUrl: './sidenav-menu.html',
  styleUrl: './sidenav-menu.scss',
})
export class SidenavMenu {
  protected readonly authService = inject(AuthService);
  protected readonly langService = inject(LangService);
  protected readonly languages = [
    { code: 'fr', label: 'Français', flag: '🇫🇷' },
    { code: 'en', label: 'English', flag: '🇬🇧' },
  ];

  protected readonly routes = computed<NavigationRoute[]>(() => {
    const base = [...NAVIGATION_ROUTES];
    const user = this.authService.user();

    if (user?.role === Role.PRO || user?.role === Role.ADMIN) {
      base.push(...PRO_NAVIGATION_ROUTES);
    }

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
}
