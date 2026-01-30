import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BookingFilters,
  CareBooking,
  CareBookingDetailed,
  CreateCareBookingRequest,
  UpdateCareBookingRequest,
} from '../models/bookings.model';
import { BaseCrudService } from '../../../core/data/base-crud.service';
import { Page } from '../../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class BookingsService extends BaseCrudService<
  CareBooking,
  CreateCareBookingRequest,
  UpdateCareBookingRequest
> {
  protected override readonly basePath = '/api/bookings';

  /**
   * List bookings with detailed information (user, care) and optional filters
   */
  listDetailed(
    filters?: BookingFilters,
    params?: { page?: number; size?: number; sort?: string }
  ): Observable<Page<CareBookingDetailed>> {
    const queryParams: any = { ...(params ?? {}) };

    if (filters?.status) {
      queryParams.status = filters.status;
    }
    if (filters?.from) {
      queryParams.from = filters.from;
    }
    if (filters?.to) {
      queryParams.to = filters.to;
    }
    if (filters?.userId) {
      queryParams.userId = filters.userId;
    }

    const url = `${this.apiBaseUrl}/api/bookings/detailed`;
    return this.http.get<Page<CareBookingDetailed>>(url, { params: queryParams });
  }
}
