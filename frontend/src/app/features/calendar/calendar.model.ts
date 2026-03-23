export interface BlockedSlotRequest {
  date: string; // "2026-03-12"
  startTime?: string;
  endTime?: string;
  fullDay: boolean;
  reason?: string;
}

export interface BlockedSlotResponse {
  id: number;
  date: string;
  startTime: string | null;
  endTime: string | null;
  fullDay: boolean;
  reason: string | null;
}

export interface CalendarDay {
  date: Date;
  dayOfMonth: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  isClosed: boolean;
  hasBlocks: boolean;
}
