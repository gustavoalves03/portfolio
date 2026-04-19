import { Provider } from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';

/**
 * Combines Material's native date adapter with the French locale so datepickers
 * display and parse dates as DD/MM/YYYY. Returns a Provider array so it can be
 * spread into a component's `providers: []` (Angular 20 rejects
 * EnvironmentProviders there).
 */
export function provideFrenchDateAdapter(): Provider[] {
  return [
    provideNativeDateAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' },
  ];
}
