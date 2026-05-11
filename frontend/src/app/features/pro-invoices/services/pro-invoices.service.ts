import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { ProInvoice, ProInvoiceStatus } from '../models/pro-invoice.model';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class ProInvoicesService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly path = `${this.apiBaseUrl.replace(/\/$/, '')}/api/pro/invoices`;

  list(opts: { status?: ProInvoiceStatus; year?: number; q?: string; page?: number; size?: number } = {}): Observable<PageResponse<ProInvoice>> {
    let params = new HttpParams();
    if (opts.status) params = params.set('status', opts.status);
    if (opts.year) params = params.set('year', String(opts.year));
    if (opts.q) params = params.set('q', opts.q);
    params = params.set('page', String(opts.page ?? 0));
    params = params.set('size', String(opts.size ?? 20));
    return this.http.get<PageResponse<ProInvoice>>(this.path, { params });
  }

  get(id: number): Observable<ProInvoice> {
    return this.http.get<ProInvoice>(`${this.path}/${id}`);
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.path}/${id}/pdf`, { responseType: 'blob' });
  }
}
