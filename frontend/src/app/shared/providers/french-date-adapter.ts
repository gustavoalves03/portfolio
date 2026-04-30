import { Provider } from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';

/**
 * Material's MatDatepickerIntl ships hard-coded English aria-labels and
 * tooltips ("Next month", "Previous year"…). We translate them once here
 * so the calendar overlay matches the French UI.
 */
class FrenchDatepickerIntl extends MatDatepickerIntl {
  override calendarLabel = 'Calendrier';
  override openCalendarLabel = 'Ouvrir le calendrier';
  override closeCalendarLabel = 'Fermer le calendrier';
  override prevMonthLabel = 'Mois précédent';
  override nextMonthLabel = 'Mois suivant';
  override prevYearLabel = 'Année précédente';
  override nextYearLabel = 'Année suivante';
  override prevMultiYearLabel = 'Vingt années précédentes';
  override nextMultiYearLabel = 'Vingt années suivantes';
  override switchToMonthViewLabel = 'Choisir le mois';
  override switchToMultiYearViewLabel = 'Choisir une année';
}

/**
 * Combines Material's native date adapter with the French locale so datepickers
 * display and parse dates as DD/MM/YYYY, and overrides MatDatepickerIntl so the
 * navigation aria-labels / tooltips are also in French. Returns a Provider
 * array so it can be spread into a component's `providers: []` (Angular 20
 * rejects EnvironmentProviders there).
 */
export function provideFrenchDateAdapter(): Provider[] {
  return [
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' },
    { provide: MatDatepickerIntl, useClass: FrenchDatepickerIntl },
  ];
}
