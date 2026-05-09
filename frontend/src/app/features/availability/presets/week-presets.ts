import { WeekSlots } from '../availability.model';

export type WeekPresetKey = 'fullWeek-9-18' | 'midDayBreak' | 'closeAll';

export interface WeekPreset {
  key: WeekPresetKey;
  /** i18n key for the toolbar button label. */
  labelKey: string;
}

export const WEEK_PRESETS: WeekPreset[] = [
  { key: 'fullWeek-9-18', labelKey: 'pro.availability.preset.fullWeek' },
  { key: 'midDayBreak', labelKey: 'pro.availability.preset.midDayBreak' },
  { key: 'closeAll', labelKey: 'pro.availability.preset.closeAll' },
];

export function applyPreset(key: WeekPresetKey, _current: WeekSlots): WeekSlots {
  switch (key) {
    case 'fullWeek-9-18':
      return [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({
        dayOfWeek,
        slots: dayOfWeek <= 5 ? [{ openTime: '09:00', closeTime: '18:00' }] : [],
      }));
    case 'midDayBreak':
      return [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({
        dayOfWeek,
        slots:
          dayOfWeek <= 5
            ? [
                { openTime: '09:00', closeTime: '13:00' },
                { openTime: '14:00', closeTime: '18:00' },
              ]
            : [],
      }));
    case 'closeAll':
      return [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, slots: [] }));
  }
}
