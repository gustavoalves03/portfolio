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
import { EmployeeSlim } from '../../salon-profile/models/salon-profile.model';

export interface AvailableSlot {
  startTime: string;
  endTime: string;
}

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

  getAvailableSlots(careId: number, date: string): Observable<AvailableSlot[]> {
    return this.http.get<AvailableSlot[]>(
      `${this.apiBaseUrl}/api/pro/opening-hours/available-slots`,
      { params: { careId: careId.toString(), date } }
    );
  }

  getEmployeesForCare(careId: number): Observable<EmployeeSlim[]> {
    return this.http.get<EmployeeSlim[]>(
      `${this.apiBaseUrl}/api/pro/employees`,
      { params: { careId: careId.toString() } }
    );
  }
}
