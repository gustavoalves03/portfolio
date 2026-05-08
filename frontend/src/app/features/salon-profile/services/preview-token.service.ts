import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { PreviewTokenResponse } from '../models/preview-token.model';

@Injectable({ providedIn: 'root' })
export class PreviewTokenService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = inject(API_BASE_URL);

  private endpoint(): string {
    return `${this.apiBase}/api/pro/salon/preview-tokens`;
  }

  list(): Observable<PreviewTokenResponse[]> {
    return this.http.get<PreviewTokenResponse[]>(this.endpoint());
  }

  create(): Observable<PreviewTokenResponse> {
    return this.http.post<PreviewTokenResponse>(this.endpoint(), {});
  }

  revoke(id: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint()}/${id}`);
  }
}
