import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { ClientBookingHistoryResponse } from './client-bookings.model';

@Injectable({ providedIn: 'root' })
export class ClientBookingsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  getMyBookings(tab: 'upcoming' | 'past'): Observable<ClientBookingHistoryResponse[]> {
    return this.http.get<ClientBookingHistoryResponse[]>(
      `${this.apiBaseUrl}/api/client/me/bookings`,
      { params: { tab } }
    );
  }
}
