import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

export type ClosedDayReason =
  | 'HOLIDAY'
  | 'WEEKLY_CLOSED'
  | 'FULL_DAY_BLOCK'
  | 'TODAY_CLOSED'
  | 'PAST';

export interface ClosedDay {
  date: string; // YYYY-MM-DD
  reason: ClosedDayReason;
}

@Injectable({ providedIn: 'root' })
export class ClosedDaysService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  loadClosedDays(from: string, to: string): Observable<ClosedDay[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ClosedDay[]>(`${this.apiBaseUrl}/api/pro/availability/closed-days`, {
      params,
    });
  }

  loadPublicClosedDays(slug: string, from: string, to: string): Observable<ClosedDay[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ClosedDay[]>(`${this.apiBaseUrl}/api/salon/${slug}/closed-days`, {
      params,
    });
  }
}
