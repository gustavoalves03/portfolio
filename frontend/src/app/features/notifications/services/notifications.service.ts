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

  list(read?: boolean, page = 0, size = 20): Observable<Page<NotificationResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (read !== undefined) {
      params = params.set('read', read);
    }
    return this.http.get<Page<NotificationResponse>>(`${this.apiBaseUrl}/api/notifications`, { params });
  }

  unreadCount(): Observable<number> {
    return this.http.get<number>(`${this.apiBaseUrl}/api/notifications/unread/count`);
  }

  markAsRead(id: number): Observable<void> {
    return this.http.patch<void>(`${this.apiBaseUrl}/api/notifications/${id}/read`, {});
  }
}
