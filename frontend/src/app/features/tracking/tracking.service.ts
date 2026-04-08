import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import {
  ClientHistoryResponse,
  ClientProfileResponse,
  CreateReminderRequest,
  CreateVisitRequest,
  ReminderResponse,
  UpdateProfileRequest,
  VisitPhotoResponse,
  VisitRecordResponse,
} from './tracking.model';

@Injectable({ providedIn: 'root' })
export class TrackingService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  getClientHistory(userId: number): Observable<ClientHistoryResponse> {
    return this.http.get<ClientHistoryResponse>(
      `${this.apiBaseUrl}/api/pro/tracking/clients/${userId}`
    );
  }

  updateProfile(userId: number, data: UpdateProfileRequest): Observable<ClientProfileResponse> {
    return this.http.put<ClientProfileResponse>(
      `${this.apiBaseUrl}/api/pro/tracking/clients/${userId}/profile`,
      data
    );
  }

  createVisit(userId: number, data: CreateVisitRequest): Observable<VisitRecordResponse> {
    return this.http.post<VisitRecordResponse>(
      `${this.apiBaseUrl}/api/pro/tracking/clients/${userId}/visits`,
      data
    );
  }

  uploadVisitPhoto(visitId: number, photo: File, type: string): Observable<VisitPhotoResponse> {
    const formData = new FormData();
    formData.append('photo', photo);
    formData.append('type', type);
    return this.http.post<VisitPhotoResponse>(
      `${this.apiBaseUrl}/api/pro/tracking/visits/${visitId}/photos`,
      formData
    );
  }

  createReminder(userId: number, data: CreateReminderRequest): Observable<ReminderResponse> {
    return this.http.post<ReminderResponse>(
      `${this.apiBaseUrl}/api/pro/tracking/clients/${userId}/reminders`,
      data
    );
  }

  getClientHistoryAsEmployee(userId: number): Observable<ClientHistoryResponse> {
    return this.http.get<ClientHistoryResponse>(`${this.apiBaseUrl}/api/employee/tracking/clients/${userId}`);
  }

  getMyPermissions(): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(`${this.apiBaseUrl}/api/employee/permissions/me`);
  }
}
