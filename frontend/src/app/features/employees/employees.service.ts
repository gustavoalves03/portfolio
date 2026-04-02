import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { Employee, CreateEmployeeRequest, UpdateEmployeeRequest } from './employees.model';

@Injectable({ providedIn: 'root' })
export class EmployeesService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly basePath = '/api/pro/employees';

  private get baseUrl(): string {
    const a = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    const b = this.basePath;
    return `${a}${b}`;
  }

  list(): Observable<Employee[]> {
    return this.http.get<Employee[]>(this.baseUrl);
  }

  get(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }

  create(req: CreateEmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, req);
  }

  update(id: number, req: UpdateEmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, req);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
