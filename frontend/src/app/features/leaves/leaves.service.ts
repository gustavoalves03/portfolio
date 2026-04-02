import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { LeaveResponse, LeaveReviewDto } from './leaves.model';

@Injectable({ providedIn: 'root' })
export class LeavesService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly basePath = '/api/pro/leaves';

  private get baseUrl(): string {
    const a = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return `${a}${this.basePath}`;
  }

  listPending(): Observable<LeaveResponse[]> {
    return this.http.get<LeaveResponse[]>(`${this.baseUrl}/pending`);
  }

  listByEmployee(employeeId: number): Observable<LeaveResponse[]> {
    return this.http.get<LeaveResponse[]>(`${this.baseUrl}/employee/${employeeId}`);
  }

  review(leaveId: number, dto: LeaveReviewDto): Observable<LeaveResponse> {
    return this.http.put<LeaveResponse>(`${this.baseUrl}/${leaveId}/review`, dto);
  }
}
