import { MatDialogConfig } from '@angular/material/dialog';

function asArray(value: string | string[] | undefined): string[] {
  if (!value) return [];
  return Array.isArray(value) ? value : [value];
}

export function bottomSheetConfig<T = unknown>(
  overrides: MatDialogConfig<T> = {},
): MatDialogConfig<T> {
  return {
    maxWidth: '100vw',
    width: '480px',
    ...overrides,
    panelClass: ['bottom-sheet', ...asArray(overrides.panelClass)],
    backdropClass: ['bottom-sheet-backdrop', ...asArray(overrides.backdropClass)],
  };
}
