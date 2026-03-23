import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { OpeningHourRequest, OpeningHourResponse } from './availability.model';

@Injectable({ providedIn: 'root' })
export class AvailabilityService {
  private http = inject(HttpClient);
  private apiBaseUrl = inject(API_BASE_URL);

  loadHours(): Observable<OpeningHourResponse[]> {
    return this.http.get<OpeningHourResponse[]>(`${this.apiBaseUrl}/api/pro/opening-hours`);
  }

  saveHours(hours: OpeningHourRequest[]): Observable<OpeningHourResponse[]> {
    return this.http.put<OpeningHourResponse[]>(
      `${this.apiBaseUrl}/api/pro/opening-hours`,
      hours
    );
  }

  loadPublicHours(slug: string): Observable<OpeningHourResponse[]> {
    return this.http.get<OpeningHourResponse[]>(
      `${this.apiBaseUrl}/api/salon/${slug}/opening-hours`
    );
  }
}
