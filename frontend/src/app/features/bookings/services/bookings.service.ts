import { Injectable } from '@angular/core';
import {
  CareBooking,
  CreateCareBookingRequest,
  UpdateCareBookingRequest,
} from '../models/bookings.model';
import { BaseCrudService } from '../../../core/data/base-crud.service';

@Injectable({ providedIn: 'root' })
export class BookingsService extends BaseCrudService<
  CareBooking,
  CreateCareBookingRequest,
  UpdateCareBookingRequest
> {
  protected readonly basePath = '/api/bookings';
}
