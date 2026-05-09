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

/** Day of week, 1 = Monday → 7 = Sunday. */
export type DayOfWeek = 1 | 2 | 3 | 4 | 5 | 6 | 7;

/** Whole-week opening slots — array of 7 entries, ordered by dayOfWeek 1→7. */
export type WeekSlots = DaySlots[];
