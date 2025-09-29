import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import {provideHttpClient, withInterceptors} from '@angular/common/http';
import { API_BASE_URL } from './core/config/api-base-url.token';
import { basicAuthInterceptor } from './core/http/basic-auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
    provideHttpClient(withInterceptors([basicAuthInterceptor])),
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes), provideClientHydration(withEventReplay())
  ]
};
