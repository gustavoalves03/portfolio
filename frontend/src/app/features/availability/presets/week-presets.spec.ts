import { WEEK_PRESETS, applyPreset } from './week-presets';
import { WeekSlots } from '../availability.model';

const emptyWeek = (): WeekSlots =>
  [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, slots: [] }));

describe('week-presets', () => {
  it('exposes 3 named presets', () => {
    expect(WEEK_PRESETS.map((p) => p.key)).toEqual([
      'fullWeek-9-18',
      'midDayBreak',
      'closeAll',
    ]);
  });

  describe('fullWeek-9-18', () => {
    it('opens Mon-Fri 9-18 and closes weekend', () => {
      const out = applyPreset('fullWeek-9-18', emptyWeek());
      expect(out.find((d) => d.dayOfWeek === 1)!.slots).toEqual([
        { openTime: '09:00', closeTime: '18:00' },
      ]);
      expect(out.find((d) => d.dayOfWeek === 5)!.slots.length).toBe(1);
      expect(out.find((d) => d.dayOfWeek === 6)!.slots).toEqual([]);
      expect(out.find((d) => d.dayOfWeek === 7)!.slots).toEqual([]);
    });
  });

  describe('midDayBreak', () => {
    it('opens Mon-Fri 9-13 + 14-18 and closes weekend', () => {
      const out = applyPreset('midDayBreak', emptyWeek());
      expect(out.find((d) => d.dayOfWeek === 1)!.slots).toEqual([
        { openTime: '09:00', closeTime: '13:00' },
        { openTime: '14:00', closeTime: '18:00' },
      ]);
      expect(out.find((d) => d.dayOfWeek === 6)!.slots).toEqual([]);
    });
  });

  describe('closeAll', () => {
    it('clears every slot', () => {
      const week = emptyWeek();
      week[0].slots = [{ openTime: '09:00', closeTime: '12:00' }];
      const out = applyPreset('closeAll', week);
      expect(out.every((d) => d.slots.length === 0)).toBe(true);
    });
  });

  it('returns a new array (does not mutate input)', () => {
    const input = emptyWeek();
    const out = applyPreset('fullWeek-9-18', input);
    expect(out).not.toBe(input);
    expect(input[0].slots.length).toBe(0);
  });
});
