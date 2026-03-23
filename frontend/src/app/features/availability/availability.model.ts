export interface TimeSlot {
  openTime: string; // "09:00"
  closeTime: string; // "18:00"
}

export interface DaySlots {
  dayOfWeek: number; // 1-7
  slots: TimeSlot[];
}

export interface OpeningHourRequest {
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}

export interface OpeningHourResponse {
  id: number;
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
}
