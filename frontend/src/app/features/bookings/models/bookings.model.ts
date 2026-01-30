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
  appointmentDate: string; // ISO date string (YYYY-MM-DD)
  appointmentTime: string; // ISO time string (HH:mm:ss)
  status: CareBookingStatus;
  createdAt: string; // ISO date string
}

export interface UserInfo {
  id: number;
  name: string;
  email: string;
}

export interface CareInfo {
  id: number;
  name: string;
  price: number;
  duration: number;
}

export interface CareBookingDetailed {
  id: number;
  user: UserInfo;
  care: CareInfo;
  quantity: number;
  appointmentDate: string; // ISO date string (YYYY-MM-DD)
  appointmentTime: string; // ISO time string (HH:mm:ss)
  status: CareBookingStatus;
  createdAt: string; // ISO date string
}

export interface CreateCareBookingRequest {
  userId: number;
  careId: number;
  quantity: number;
  appointmentDate: string; // ISO date string (YYYY-MM-DD)
  appointmentTime: string; // ISO time string (HH:mm:ss)
  status: CareBookingStatus;
}

export type UpdateCareBookingRequest = CreateCareBookingRequest;

export interface BookingFilters {
  status?: CareBookingStatus;
  from?: string; // ISO date string
  to?: string; // ISO date string
  userId?: number;
}

