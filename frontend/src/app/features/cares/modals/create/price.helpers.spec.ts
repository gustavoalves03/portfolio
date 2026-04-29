import { centsToEuros, eurosToCents } from './create-care.component';

describe('price helpers (cents <-> euros)', () => {
  describe('centsToEuros', () => {
    it('converts whole-euro cents back to integer euros', () => {
      expect(centsToEuros(5500)).toBe(55);
      expect(centsToEuros(0)).toBe(0);
      expect(centsToEuros(100)).toBe(1);
    });

    it('keeps decimals where present', () => {
      expect(centsToEuros(5550)).toBe(55.5);
      expect(centsToEuros(99)).toBe(0.99);
    });

    it('null / undefined / NaN → 0 (no crash on bad data)', () => {
      expect(centsToEuros(null)).toBe(0);
      expect(centsToEuros(undefined)).toBe(0);
      expect(centsToEuros(NaN as unknown as number)).toBe(0);
    });

    it('rounds odd input to closest cent before dividing', () => {
      // Defensive: should never receive non-int cents but if it does, round.
      expect(centsToEuros(5500.4 as unknown as number)).toBe(55);
      expect(centsToEuros(5500.6 as unknown as number)).toBe(55.01);
    });
  });

  describe('eurosToCents', () => {
    it('converts integer euros to cents', () => {
      expect(eurosToCents(55)).toBe(5500);
      expect(eurosToCents(0)).toBe(0);
      expect(eurosToCents(1)).toBe(100);
    });

    it('handles 2-decimal euros', () => {
      expect(eurosToCents(55.5)).toBe(5550);
      expect(eurosToCents(0.99)).toBe(99);
      expect(eurosToCents(12.34)).toBe(1234);
    });

    it('accepts a string with comma decimal (FR keyboard)', () => {
      expect(eurosToCents('55,5')).toBe(5550);
      expect(eurosToCents('12,34')).toBe(1234);
    });

    it('trims surrounding whitespace from string input', () => {
      expect(eurosToCents('  55  ')).toBe(5500);
    });

    it('null / undefined / empty string / non-numeric → 0', () => {
      expect(eurosToCents(null)).toBe(0);
      expect(eurosToCents(undefined)).toBe(0);
      expect(eurosToCents('')).toBe(0);
      expect(eurosToCents('abc')).toBe(0);
    });

    it('refuses negative values (returns 0)', () => {
      expect(eurosToCents(-5)).toBe(0);
      expect(eurosToCents('-5')).toBe(0);
    });

    it('round-trip: cents → euros → cents preserves the value', () => {
      [0, 100, 5500, 5550, 1234, 99].forEach((cents) => {
        expect(eurosToCents(centsToEuros(cents))).toBe(cents);
      });
    });
  });
});
