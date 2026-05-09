import {
  HHMM_OPTIONS,
  hhmmToMinutes,
  minutesToHhmm,
  snapTo30,
  positionInRail,
  slotsOverlap,
} from './time-utils';

describe('time-utils', () => {
  describe('HHMM_OPTIONS', () => {
    it('lists all 30-minute steps from 06:00 to 22:00 (33 entries)', () => {
      expect(HHMM_OPTIONS.length).toBe(33);
      expect(HHMM_OPTIONS[0]).toBe('06:00');
      expect(HHMM_OPTIONS[1]).toBe('06:30');
      expect(HHMM_OPTIONS[HHMM_OPTIONS.length - 1]).toBe('22:00');
    });
  });

  describe('hhmmToMinutes', () => {
    it('converts "09:30" to 570', () => {
      expect(hhmmToMinutes('09:30')).toBe(570);
    });
    it('converts "06:00" to 360', () => {
      expect(hhmmToMinutes('06:00')).toBe(360);
    });
    it('returns 0 on malformed input', () => {
      expect(hhmmToMinutes('')).toBe(0);
      expect(hhmmToMinutes('abc')).toBe(0);
    });
  });

  describe('minutesToHhmm', () => {
    it('converts 570 to "09:30"', () => {
      expect(minutesToHhmm(570)).toBe('09:30');
    });
    it('pads single digits', () => {
      expect(minutesToHhmm(60)).toBe('01:00');
    });
  });

  describe('snapTo30', () => {
    it('rounds 09:17 to 09:30', () => {
      expect(snapTo30('09:17')).toBe('09:30');
    });
    it('keeps 09:00 unchanged', () => {
      expect(snapTo30('09:00')).toBe('09:00');
    });
    it('keeps 09:30 unchanged', () => {
      expect(snapTo30('09:30')).toBe('09:30');
    });
    it('rounds 09:45 up to 10:00', () => {
      expect(snapTo30('09:45')).toBe('10:00');
    });
  });

  describe('positionInRail', () => {
    // Rail = 6h → 22h = 16 hours = 960 minutes
    it('positions 09:00 at 18.75% from left', () => {
      expect(positionInRail('09:00')).toBeCloseTo(18.75, 2);
    });
    it('positions 06:00 at 0%', () => {
      expect(positionInRail('06:00')).toBe(0);
    });
    it('clamps 04:00 to 0%', () => {
      expect(positionInRail('04:00')).toBe(0);
    });
    it('clamps 23:00 to 100%', () => {
      expect(positionInRail('23:00')).toBe(100);
    });
  });

  describe('slotsOverlap', () => {
    it('detects overlapping slots', () => {
      expect(slotsOverlap(
        { openTime: '09:00', closeTime: '12:00' },
        { openTime: '11:00', closeTime: '14:00' },
      )).toBe(true);
    });
    it('detects non-overlapping slots (touching is OK)', () => {
      expect(slotsOverlap(
        { openTime: '09:00', closeTime: '12:00' },
        { openTime: '12:00', closeTime: '14:00' },
      )).toBe(false);
    });
    it('detects non-overlapping slots (gap)', () => {
      expect(slotsOverlap(
        { openTime: '09:00', closeTime: '12:00' },
        { openTime: '14:00', closeTime: '18:00' },
      )).toBe(false);
    });
  });
});
