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
import {provideRouter, withInMemoryScrolling} from '@angular/router';

import {routes} from './app.routes';
import {provideClientHydration, withEventReplay} from '@angular/platform-browser';
import {provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration} from '@angular/common/http';
import {API_BASE_URL} from './core/config/api-base-url.token';
import {credentialsInterceptor} from './core/interceptors/credentials.interceptor';
import { csrfInterceptor } from './core/interceptors/csrf.interceptor';
import { authInterceptor } from './core/auth/auth.interceptor';
import { featureFlagInterceptor } from './core/feature-flags/feature-flag.interceptor';
import {CsrfService} from './core/security/csrf.service';
import {AuthService} from './core/auth/auth.service';
import {provideTransloco} from '@jsverse/transloco';
import {provideTranslocoLocale} from '@jsverse/transloco-locale';
import {TranslocoHttpLoader} from './i18n/transloco-http.loader';
import {LANG_TO_LOCALE} from './i18n/locale.config';
import { firstValueFrom } from 'rxjs';
import { NotificationsStore } from './features/notifications/store/notifications.store';

export const appConfig: ApplicationConfig = {
  providers: [
    // Browser API base URL.
    //   - dev (ng serve): hits the backend on localhost:8080 directly.
    //   - prod build: empty string → relative paths → the reverse proxy routes
    //     /api/** to the backend on the same origin. No rebuild per environment.
    // SSR overrides this in app.config.server.ts (uses host.docker.internal or
    // API_BASE_URL env var).
    {provide: API_BASE_URL, useValue: isDevMode() ? 'http://localhost:8080' : ''},
    provideHttpClient(
      withFetch(),
      withInterceptors([credentialsInterceptor, csrfInterceptor, authInterceptor, featureFlagInterceptor]),
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
    // Load current user on app startup (if token exists) — subscribes the cold Observable
    // so `authService.isAuthenticated()` is accurate before components read it.
    {
      provide: APP_INITIALIZER,
      useFactory: (authService: AuthService) => () =>
        firstValueFrom(authService.checkAuthentication()).catch(() => false),
      deps: [AuthService],
      multi: true
    },
    NotificationsStore,
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(
      routes,
      // Reset scroll to top on navigation (and restore on back/forward).
      // Without this, navigating from a scrolled list landed lower on the
      // next page (e.g. the care detail page opened scrolled to the bottom).
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    provideClientHydration(withEventReplay()),
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
