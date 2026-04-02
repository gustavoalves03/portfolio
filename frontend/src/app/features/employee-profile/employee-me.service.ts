import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { Employee } from '../employees/employees.model';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class EmployeeMeService {
    private http = inject(HttpClient);
    private apiBaseUrl = inject(API_BASE_URL);

    getProfile(): Observable<Employee> {
        return this.http.get<Employee>(`${this.apiBaseUrl}/api/employee/me`);
    }
}
