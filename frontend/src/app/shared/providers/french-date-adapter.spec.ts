import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { MAT_DATE_LOCALE, DateAdapter } from '@angular/material/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';

import { provideFrenchDateAdapter } from './french-date-adapter';

describe('provideFrenchDateAdapter', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ...provideFrenchDateAdapter(),
      ],
    });
  });

  it('sets MAT_DATE_LOCALE to fr-FR', () => {
    expect(TestBed.inject(MAT_DATE_LOCALE)).toBe('fr-FR');
  });

  it('binds a DateAdapter (provided via provideNativeDateAdapter)', () => {
    const adapter = TestBed.inject(DateAdapter);
    expect(adapter).toBeTruthy();
  });

  describe('MatDatepickerIntl localization', () => {
    let intl: MatDatepickerIntl;

    beforeEach(() => {
      intl = TestBed.inject(MatDatepickerIntl);
    });

    it('translates the navigation buttons', () => {
      expect(intl.prevMonthLabel).toBe('Mois précédent');
      expect(intl.nextMonthLabel).toBe('Mois suivant');
      expect(intl.prevYearLabel).toBe('Année précédente');
      expect(intl.nextYearLabel).toBe('Année suivante');
    });

    it('translates the multi-year navigation', () => {
      expect(intl.prevMultiYearLabel).toBe('Vingt années précédentes');
      expect(intl.nextMultiYearLabel).toBe('Vingt années suivantes');
    });

    it('translates the calendar open / close affordances', () => {
      expect(intl.openCalendarLabel).toBe('Ouvrir le calendrier');
      expect(intl.closeCalendarLabel).toBe('Fermer le calendrier');
      expect(intl.calendarLabel).toBe('Calendrier');
    });

    it('translates the view switchers', () => {
      expect(intl.switchToMonthViewLabel).toBe('Choisir le mois');
      expect(intl.switchToMultiYearViewLabel).toBe('Choisir une année');
    });

    it('does not leak any English fallback', () => {
      const props = [
        intl.prevMonthLabel,
        intl.nextMonthLabel,
        intl.prevYearLabel,
        intl.nextYearLabel,
        intl.prevMultiYearLabel,
        intl.nextMultiYearLabel,
        intl.openCalendarLabel,
        intl.closeCalendarLabel,
        intl.calendarLabel,
        intl.switchToMonthViewLabel,
        intl.switchToMultiYearViewLabel,
      ];
      for (const value of props) {
        // None of the French strings include the English month/year nouns
        // — a regression that swaps a default back in would re-introduce them.
        expect(value).not.toMatch(/\b(Next|Previous|month|year|Choose|Open|Close)\b/i);
      }
    });
  });
});
