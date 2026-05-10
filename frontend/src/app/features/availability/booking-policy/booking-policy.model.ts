export interface BookingPolicy {
  readonly maxBookingsPerDayPerClient: number;
  readonly maxBookingsPerWeekForNewClient: number;
  readonly updatedAt: string;
}

export interface UpdateBookingPolicyRequest {
  readonly maxBookingsPerDayPerClient: number;
  readonly maxBookingsPerWeekForNewClient: number;
}
