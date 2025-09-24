export enum CareBookingStatus {
  PENDING = 'PENDING',
  CONFIRMED = 'CONFIRMED',
  CANCELLED = 'CANCELLED',
}

export interface CareBooking {
  id: number;
  userId: number;
  careId: number;
  quantity: number;
  status: CareBookingStatus;
  createdAt: string; // ISO date string
}

export interface CreateCareBookingRequest {
  userId: number;
  careId: number;
  quantity: number;
  status: CareBookingStatus;
}

export type UpdateCareBookingRequest = CreateCareBookingRequest;

