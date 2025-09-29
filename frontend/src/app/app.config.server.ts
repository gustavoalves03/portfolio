import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { API_BASE_URL } from './core/config/api-base-url.token';
import { serverRoutes } from './app.routes.server';

const serverConfig: ApplicationConfig = {
  providers: [
    // If you run SSR and need server-side HTTP calls to hit the API,
    // also set the API base URL here.
    { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
    provideServerRendering(withRoutes(serverRoutes))
  ]
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
