import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { BookingPolicy, UpdateBookingPolicyRequest } from './booking-policy.model';

@Injectable({ providedIn: 'root' })
export class BookingPolicyService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  getCurrent(): Observable<BookingPolicy> {
    return this.http.get<BookingPolicy>(`${this.base}/api/pro/booking-policy`);
  }

  update(req: UpdateBookingPolicyRequest): Observable<BookingPolicy> {
    return this.http.put<BookingPolicy>(`${this.base}/api/pro/booking-policy`, req);
  }
}
