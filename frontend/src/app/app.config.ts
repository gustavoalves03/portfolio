import {
  ApplicationConfig,
  isDevMode,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection
} from '@angular/core';
import {registerLocaleData} from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeEn from '@angular/common/locales/en';
import {provideRouter} from '@angular/router';

import {routes} from './app.routes';
import {provideClientHydration, withEventReplay} from '@angular/platform-browser';
import {provideHttpClient, withFetch, withInterceptors} from '@angular/common/http';
import {API_BASE_URL} from './core/config/api-base-url.token';
import {basicAuthInterceptor} from './core/http/basic-auth.interceptor';
import {provideTransloco} from '@jsverse/transloco';
import {provideTranslocoLocale} from '@jsverse/transloco-locale';
import {TranslocoHttpLoader} from './i18n/transloco-http.loader';
import {LANG_TO_LOCALE} from './i18n/locale.config';

export const appConfig: ApplicationConfig = {
  providers: [
    {provide: API_BASE_URL, useValue: 'http://localhost:8080'},
    provideHttpClient(
      withFetch(),
      withInterceptors([basicAuthInterceptor])
    ),
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes), provideClientHydration(withEventReplay()),
    provideTransloco({
      config: {
        availableLangs: ['fr', 'en'],
        defaultLang: 'fr',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
    // Locale formatting (dates, numbers, currency) synced with language
    provideTranslocoLocale({
      defaultLocale: LANG_TO_LOCALE['fr'],
      langToLocaleMapping: LANG_TO_LOCALE,
    })
  ]
};

// Register locale data used by Angular formatters
registerLocaleData(localeFr);
registerLocaleData(localeEn);
