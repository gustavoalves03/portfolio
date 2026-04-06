import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { AnalyticsResponse } from './analytics.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  private get baseUrl(): string {
    return this.apiBaseUrl?.replace(/\/$/, '') ?? '';
  }

  getAnalytics(
    period: string,
    employeeId?: number,
    careId?: number
  ): Observable<AnalyticsResponse> {
    const params: Record<string, string> = { period };
    if (employeeId) params['employeeId'] = String(employeeId);
    if (careId) params['careId'] = String(careId);
    return this.http.get<AnalyticsResponse>(`${this.baseUrl}/api/pro/analytics`, { params });
  }
}
