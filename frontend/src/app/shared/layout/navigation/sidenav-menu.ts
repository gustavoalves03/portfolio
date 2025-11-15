import { Component, inject, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { NAVIGATION_ROUTES, NavigationRoute } from './navigation-routes';
import { LangService } from '../../../i18n/lang.service';

@Component({
  selector: 'app-sidenav-menu',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule],
  templateUrl: './sidenav-menu.html',
  styleUrl: './sidenav-menu.scss'
})
export class SidenavMenu {
  protected readonly routes = NAVIGATION_ROUTES;
  protected readonly langService = inject(LangService);
  protected readonly languages = [
    { code: 'fr', label: 'Français', flag: '🇫🇷' },
    { code: 'en', label: 'English', flag: '🇬🇧' }
  ];

  // Émet un événement quand on clique sur un lien pour fermer le menu
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
