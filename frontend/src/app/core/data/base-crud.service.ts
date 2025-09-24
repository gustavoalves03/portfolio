import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../../shared/models/page.model';
import { API_BASE_URL } from '../config/api-base-url.token';

export abstract class BaseCrudService<T, CreateDto, UpdateDto> {
  protected readonly http = inject(HttpClient);
  protected readonly apiBaseUrl = inject(API_BASE_URL);

  // Each subclass sets its base path, e.g. '/api/users'
  protected abstract readonly basePath: string;

  protected get baseUrl(): string {
    // Ensure no double slashes when apiBaseUrl ends with '/'
    const a = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    const b = this.basePath.startsWith('/') ? this.basePath : `/${this.basePath}`;
    return `${a}${b}`;
  }

  list(params?: { page?: number; size?: number; sort?: string }): Observable<Page<T>> {
    return this.http.get<Page<T>>(this.baseUrl, { params: (params as any) ?? {} });
  }

  get(id: number): Observable<T> {
    return this.http.get<T>(`${this.baseUrl}/${id}`);
  }

  create(payload: CreateDto): Observable<T> {
    return this.http.post<T>(this.baseUrl, payload as any);
  }

  update(id: number, payload: UpdateDto): Observable<T> {
    return this.http.put<T>(`${this.baseUrl}/${id}`, payload as any);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}

