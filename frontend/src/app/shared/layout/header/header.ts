import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SidenavService } from '../navigation/sidenav.service';
import { SidenavOverlay } from '../navigation/sidenav-overlay';
import { LangService } from '../../../i18n/lang.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, SidenavOverlay],
  templateUrl: './header.html',
  styleUrl: './header.scss'
})
export class Header {
  protected readonly sidenavService = inject(SidenavService);
  protected readonly langService = inject(LangService);
  protected readonly languages = [
    { code: 'fr', label: 'Français', flag: '🇫🇷' },
    { code: 'en', label: 'English', flag: '🇬🇧' }
  ];

  protected toggleSidenav(): void {
    this.sidenavService.toggle();
  }

  protected setLanguage(lang: string): void {
    if (this.langService.active() === lang) {
      return;
    }
    this.langService.set(lang);
  }
}
