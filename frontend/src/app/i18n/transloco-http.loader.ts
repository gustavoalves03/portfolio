import {inject, Injectable, PLATFORM_ID} from '@angular/core';
import {HttpClient} from '@angular/common/http';

@Injectable({providedIn: 'root'})
export class TranslocoHttpLoader {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);

  getTranslation(lang: string) {
    // Assets served from `frontend/public/i18n/*.json` at "/i18n/<lang>.json"
    // Works in browser and SSR.
    const url = `/i18n/${lang}.json`;
    return this.http.get<Record<string, any>>(url);
  }
}
