import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { TranslocoService } from '@jsverse/transloco';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import { LANG_TO_LOCALE } from './locale.config';

@Injectable({ providedIn: 'root' })
export class LangService {
  private readonly i18n = inject(TranslocoService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly locale = inject(TranslocoLocaleService);
  readonly active = signal<string>(this.i18n.getActiveLang() || 'fr');

  init() {
    if (isPlatformBrowser(this.platformId)) {
      const saved = localStorage.getItem('lang');
      const detected = navigator.language?.toLowerCase().startsWith('fr') ? 'fr' : 'en';
      this.set(saved || detected || 'fr');
      document.documentElement.lang = this.active();
    } else {
      // Keep default on server; can be extended to read headers if needed
      this.set(this.active());
    }
  }

  set(lang: string) {
    this.i18n.setActiveLang(lang);
    this.active.set(lang);
    const l = LANG_TO_LOCALE[lang] ?? lang;
    this.locale.setLocale(l);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem('lang', lang);
      document.documentElement.lang = lang;
    }
  }
}
