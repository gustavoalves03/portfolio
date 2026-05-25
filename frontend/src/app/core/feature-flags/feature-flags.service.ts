import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api-base-url.token';
import { FeatureFlagSnapshot } from './feature-key';

@Injectable({ providedIn: 'root' })
export class FeatureFlagsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  fetch(): Observable<FeatureFlagSnapshot> {
    return this.http.get<FeatureFlagSnapshot>(`${this.apiBaseUrl}/api/me/features`);
  }
}
