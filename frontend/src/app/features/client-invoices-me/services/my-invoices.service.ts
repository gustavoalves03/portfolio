import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { MyInvoice, MyInvoiceStatus } from '../models/my-invoice.model';
import { PageResponse } from '../../pro-invoices/services/pro-invoices.service';

@Injectable({ providedIn: 'root' })
export class MyInvoicesService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly path = `${this.apiBaseUrl.replace(/\/$/, '')}/api/me/invoices`;

  list(opts: { status?: MyInvoiceStatus; year?: number; page?: number; size?: number } = {}): Observable<PageResponse<MyInvoice>> {
    let params = new HttpParams();
    if (opts.status) params = params.set('status', opts.status);
    if (opts.year) params = params.set('year', String(opts.year));
    params = params.set('page', String(opts.page ?? 0));
    params = params.set('size', String(opts.size ?? 20));
    return this.http.get<PageResponse<MyInvoice>>(this.path, { params });
  }

  get(id: number): Observable<MyInvoice> {
    return this.http.get<MyInvoice>(`${this.path}/${id}`);
  }

  downloadPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.path}/${id}/pdf`, { responseType: 'blob' });
  }
}
