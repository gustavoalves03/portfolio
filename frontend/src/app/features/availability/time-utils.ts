import { TimeSlot } from './availability.model';

/** Rail boundaries (en minutes depuis minuit). 6h = 360, 22h = 1320. */
export const RAIL_START_MIN = 360;
export const RAIL_END_MIN = 1320;
const RAIL_SPAN = RAIL_END_MIN - RAIL_START_MIN; // 960

/** Liste des heures HH:MM disponibles (snap 30 min) entre 06:00 et 22:00 inclus. */
export const HHMM_OPTIONS: string[] = (() => {
  const out: string[] = [];
  for (let m = RAIL_START_MIN; m <= RAIL_END_MIN; m += 30) {
    out.push(minutesToHhmm(m));
  }
  return out;
})();

/** "09:30" → 570 minutes since midnight. Returns 0 on malformed input. */
export function hhmmToMinutes(value: string): number {
  if (!value || !value.includes(':')) return 0;
  const [h, m] = value.split(':');
  const hn = Number(h);
  const mn = Number(m);
  if (!Number.isFinite(hn) || !Number.isFinite(mn)) return 0;
  return hn * 60 + mn;
}

/** 570 → "09:30". */
export function minutesToHhmm(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/** Rounds an HH:MM value to the nearest 30-min step. */
export function snapTo30(value: string): string {
  const total = hhmmToMinutes(value);
  const snapped = Math.round(total / 30) * 30;
  return minutesToHhmm(snapped);
}

/**
 * Returns the % position of a time on the 6h-22h rail.
 * 06:00 → 0, 22:00 → 100, values outside are clamped.
 */
export function positionInRail(time: string): number {
  const min = hhmmToMinutes(time);
  if (min <= RAIL_START_MIN) return 0;
  if (min >= RAIL_END_MIN) return 100;
  return ((min - RAIL_START_MIN) / RAIL_SPAN) * 100;
}

/**
 * Checks if two slots overlap. Touching at the boundary is NOT an overlap.
 * 09-12 + 12-14 = no overlap. 09-12 + 11-14 = overlap.
 */
export function slotsOverlap(a: TimeSlot, b: TimeSlot): boolean {
  const aStart = hhmmToMinutes(a.openTime);
  const aEnd = hhmmToMinutes(a.closeTime);
  const bStart = hhmmToMinutes(b.openTime);
  const bEnd = hhmmToMinutes(b.closeTime);
  return aStart < bEnd && bStart < aEnd;
}
