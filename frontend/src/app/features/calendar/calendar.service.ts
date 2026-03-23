import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { BlockedSlotRequest, BlockedSlotResponse } from './calendar.model';

@Injectable({ providedIn: 'root' })
export class CalendarService {
  private http = inject(HttpClient);
  private apiBaseUrl = inject(API_BASE_URL);

  loadBlockedSlots(): Observable<BlockedSlotResponse[]> {
    return this.http.get<BlockedSlotResponse[]>(`${this.apiBaseUrl}/api/pro/blocked-slots`);
  }

  createBlock(req: BlockedSlotRequest): Observable<BlockedSlotResponse> {
    return this.http.post<BlockedSlotResponse>(`${this.apiBaseUrl}/api/pro/blocked-slots`, req);
  }

  deleteBlock(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/pro/blocked-slots/${id}`);
  }
}
