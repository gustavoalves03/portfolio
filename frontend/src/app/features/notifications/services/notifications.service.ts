import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { NotificationResponse } from '../models/notification.model';

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  list(params: {
    read?: boolean;
    since?: string;
    page?: number;
    size?: number;
  } = {}): Observable<Page<NotificationResponse>> {
    let httpParams = new HttpParams()
      .set('page', params.page ?? 0)
      .set('size', params.size ?? 20);
    if (params.read !== undefined) {
      httpParams = httpParams.set('read', params.read);
    }
    if (params.since !== undefined) {
      httpParams = httpParams.set('since', params.since);
    }
    return this.http.get<Page<NotificationResponse>>(
      `${this.apiBaseUrl}/api/notifications`,
      { params: httpParams },
    );
  }

  unreadCount(): Observable<number> {
    return this.http.get<number>(`${this.apiBaseUrl}/api/notifications/unread/count`);
  }

  markAsRead(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiBaseUrl}/api/notifications/${id}/read`, {});
  }
}
