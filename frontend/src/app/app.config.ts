import {
  APP_INITIALIZER,
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
import {provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration} from '@angular/common/http';
import {API_BASE_URL} from './core/config/api-base-url.token';
import {basicAuthInterceptor} from './core/http/basic-auth.interceptor';
import {credentialsInterceptor} from './core/interceptors/credentials.interceptor';
import { csrfInterceptor } from './core/interceptors/csrf.interceptor';
import {CsrfService} from './core/security/csrf.service';
import {provideTransloco} from '@jsverse/transloco';
import {provideTranslocoLocale} from '@jsverse/transloco-locale';
import {TranslocoHttpLoader} from './i18n/transloco-http.loader';
import {LANG_TO_LOCALE} from './i18n/locale.config';
import { firstValueFrom } from 'rxjs';

export const appConfig: ApplicationConfig = {
  providers: [
    // Browser uses localhost, SSR uses host.docker.internal (overridden in app.config.server.ts)
    {provide: API_BASE_URL, useValue: 'http://localhost:8080'},
    provideHttpClient(
      withFetch(),
      withInterceptors([credentialsInterceptor, csrfInterceptor, basicAuthInterceptor]),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      })
    ),
    // Initialize CSRF token on app startup
    {
      provide: APP_INITIALIZER,
      useFactory: (csrfService: CsrfService) => () => firstValueFrom(csrfService.initializeCsrfToken()),
      deps: [CsrfService],
      multi: true
    },
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
