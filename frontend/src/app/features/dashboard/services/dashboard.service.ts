import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { TenantReadiness } from '../models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  private get baseUrl(): string {
    return this.apiBaseUrl?.replace(/\/$/, '') ?? '';
  }

  getReadiness(): Observable<TenantReadiness> {
    return this.http.get<TenantReadiness>(`${this.baseUrl}/api/pro/tenant/readiness`);
  }

  publish(): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/api/pro/tenant/publish`, {});
  }

  unpublish(): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/api/pro/tenant/unpublish`, {});
  }
}
