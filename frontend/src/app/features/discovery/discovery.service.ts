import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { isPlatformBrowser } from '@angular/common';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { SalonCard } from './discovery.model';

@Injectable({ providedIn: 'root' })
export class DiscoveryService {
  private http = inject(HttpClient);
  private apiBaseUrl = inject(API_BASE_URL);
  private isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  searchSalons(category?: string | null, q?: string | null): Observable<SalonCard[]> {
    const params: Record<string, string> = {};
    if (category) params['category'] = category;
    if (q) params['q'] = q;

    return this.http
      .get<SalonCard[]>(`${this.apiBaseUrl}/api/public/salons`, { params })
      .pipe(map((salons) => salons.map((s) => this.transformLogoUrl(s))));
  }

  private transformLogoUrl(salon: SalonCard): SalonCard {
    if (!salon.logoUrl || !this.isBrowser) return salon;
    if (salon.logoUrl.startsWith('http')) return salon;
    return { ...salon, logoUrl: `${this.apiBaseUrl}${salon.logoUrl}` };
  }
}
