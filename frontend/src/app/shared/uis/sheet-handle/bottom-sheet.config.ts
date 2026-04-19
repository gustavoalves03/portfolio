import { MatDialogConfig } from '@angular/material/dialog';

function asArray(value: string | string[] | undefined): string[] {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

export function bottomSheetConfig<T = unknown>(
  overrides: MatDialogConfig<T> = {},
): MatDialogConfig<T> {
  return {
    maxWidth: 'min(480px, 100vw)',
    ...overrides,
    panelClass: ['bottom-sheet', ...asArray(overrides.panelClass)],
    backdropClass: ['bottom-sheet-backdrop', ...asArray(overrides.backdropClass)],
  };
}
