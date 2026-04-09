import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { ClientHistoryResponse } from './tracking.model';

@Injectable({ providedIn: 'root' })
export class ClientTrackingService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  private get baseUrl(): string {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return `${base}/api/client/tracking`;
  }

  getMyHistory(): Observable<ClientHistoryResponse> {
    return this.http.get<ClientHistoryResponse>(`${this.baseUrl}/history`);
  }

  updateMyConsent(data: { consentPhotos: boolean; consentPublicShare: boolean }): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/consent`, data);
  }

  rateVisit(visitId: number, data: { score: number; comment: string }): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/visits/${visitId}/rate`, data);
  }

  deleteMyPhotos(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/photos`);
  }
}
