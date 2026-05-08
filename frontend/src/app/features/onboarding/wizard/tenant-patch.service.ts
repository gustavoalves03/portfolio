import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

export interface TenantPatch {
  name?: string;
  addressStreet?: string;
  addressPostalCode?: string;
  addressCity?: string;
  addressCountry?: string;
  phone?: string;
  contactEmail?: string;
  logo?: string; // base64 data URL
  heroImage?: string;
  categorySlugs?: string;
}

@Injectable({ providedIn: 'root' })
export class TenantPatchService {
  private http = inject(HttpClient);
  private apiBaseUrl = inject(API_BASE_URL);

  patch(body: TenantPatch): Observable<unknown> {
    return this.http.patch(`${this.apiBaseUrl}/api/pro/tenant`, body);
  }
}
