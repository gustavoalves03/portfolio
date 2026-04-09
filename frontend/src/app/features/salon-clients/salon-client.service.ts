import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { SalonClientResponse, CreateSalonClientRequest } from './salon-client.model';

@Injectable({ providedIn: 'root' })
export class SalonClientService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  search(query: string): Observable<SalonClientResponse[]> {
    const params = new HttpParams().set('q', query);
    return this.http.get<SalonClientResponse[]>(`${this.apiBaseUrl}/api/pro/clients/search`, { params });
  }

  recent(): Observable<SalonClientResponse[]> {
    return this.http.get<SalonClientResponse[]>(`${this.apiBaseUrl}/api/pro/clients/recent`);
  }

  create(request: CreateSalonClientRequest): Observable<SalonClientResponse> {
    return this.http.post<SalonClientResponse>(`${this.apiBaseUrl}/api/pro/clients`, request);
  }

  link(salonClientId: number, userId: number): Observable<SalonClientResponse> {
    return this.http.post<SalonClientResponse>(`${this.apiBaseUrl}/api/pro/clients/${salonClientId}/link/${userId}`, {});
  }
}
