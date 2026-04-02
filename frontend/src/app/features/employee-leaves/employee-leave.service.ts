import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class EmployeeLeaveService {
    private http = inject(HttpClient);
    private apiBaseUrl = inject(API_BASE_URL);

    getMyLeaves(): Observable<any[]> {
        return this.http.get<any[]>(`${this.apiBaseUrl}/api/employee/me/leaves`);
    }

    createLeave(dto: { type: string; startDate: string; endDate: string; reason?: string }): Observable<any> {
        return this.http.post<any>(`${this.apiBaseUrl}/api/employee/me/leaves`, dto);
    }
}
