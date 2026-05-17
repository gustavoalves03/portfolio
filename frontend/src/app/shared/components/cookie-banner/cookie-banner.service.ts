import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

const STORAGE_KEY = 'lp_cookie_banner_v1';

@Injectable({ providedIn: 'root' })
export class CookieBannerService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  readonly dismissed = signal<boolean>(this.loadInitial());

  private loadInitial(): boolean {
    if (!this.isBrowser) return true;
    try {
      return localStorage.getItem(STORAGE_KEY) === 'dismissed';
    } catch {
      return true;
    }
  }

  dismiss(): void {
    if (!this.isBrowser) return;
    try {
      localStorage.setItem(STORAGE_KEY, 'dismissed');
    } catch {
      /* localStorage unavailable (private mode, quota) — silently ignore */
    }
    this.dismissed.set(true);
  }
}
