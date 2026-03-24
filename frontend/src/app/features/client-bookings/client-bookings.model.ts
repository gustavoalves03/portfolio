export interface ClientBookingHistoryResponse {
  id: number;
  bookingId: number;
  tenantSlug: string;
  salonName: string;
  careName: string;
  carePrice: number;
  careDuration: number;
  appointmentDate: string;
  appointmentTime: string;
  status: string;
  createdAt: string;
}
