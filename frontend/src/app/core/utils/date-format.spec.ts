import {
  formatDate,
  formatDateTime,
  toYMD,
  addDays,
  parseYMD,
} from './date-format';

describe('date-format utility', () => {
  describe('formatDate', () => {
    it('formats an ISO date string as dd/MM/yyyy', () => {
      expect(formatDate('2026-04-18')).toBe('18/04/2026');
    });

    it('formats an ISO datetime string dropping the time part', () => {
      expect(formatDate('2026-04-18T14:30:00')).toBe('18/04/2026');
    });

    it('formats a Date object as dd/MM/yyyy', () => {
      expect(formatDate(new Date(2026, 3, 18))).toBe('18/04/2026');
    });

    it('returns empty string for null or undefined', () => {
      expect(formatDate(null)).toBe('');
      expect(formatDate(undefined)).toBe('');
    });

    it('returns empty string for an invalid date string', () => {
      expect(formatDate('not-a-date')).toBe('');
    });
  });

  describe('formatDateTime', () => {
    it('formats an ISO datetime string as dd/MM/yyyy HH:mm', () => {
      expect(formatDateTime('2026-04-18T14:30:00')).toBe('18/04/2026 14:30');
    });

    it('returns empty string for null', () => {
      expect(formatDateTime(null)).toBe('');
    });

    it('pads hours and minutes with leading zero', () => {
      expect(formatDateTime('2026-04-18T04:05:00')).toBe('18/04/2026 04:05');
    });
  });

  describe('toYMD', () => {
    it('returns YYYY-MM-DD for a Date', () => {
      expect(toYMD(new Date(2026, 3, 18))).toBe('2026-04-18');
    });

    it('pads single-digit months and days', () => {
      expect(toYMD(new Date(2026, 0, 5))).toBe('2026-01-05');
    });
  });

  describe('addDays', () => {
    it('returns a new Date n days later', () => {
      const result = addDays(new Date(2026, 3, 18), 2);
      expect(result.getDate()).toBe(20);
      expect(result.getMonth()).toBe(3);
    });

    it('accepts negative deltas', () => {
      const result = addDays(new Date(2026, 3, 1), -5);
      expect(result.getMonth()).toBe(2);
      expect(result.getDate()).toBe(27);
    });

    it('does not mutate the original Date', () => {
      const original = new Date(2026, 3, 18);
      addDays(original, 10);
      expect(original.getDate()).toBe(18);
    });
  });

  describe('parseYMD', () => {
    it('parses a YYYY-MM-DD string into a Date', () => {
      const result = parseYMD('2026-04-18');
      expect(result).not.toBeNull();
      expect(result?.getFullYear()).toBe(2026);
      expect(result?.getMonth()).toBe(3);
      expect(result?.getDate()).toBe(18);
    });

    it('returns null for a malformed string', () => {
      expect(parseYMD('invalid')).toBeNull();
      expect(parseYMD('2026-13-01')).toBeNull();
    });
  });
});
