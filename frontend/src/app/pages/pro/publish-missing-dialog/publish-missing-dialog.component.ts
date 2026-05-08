import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';

/** Maps a backend `missing[]` key to the pro page that lets the pro fix it. */
const MISSING_KEY_TO_ROUTE: Readonly<Record<string, string>> = {
  name: '/pro/salon',
  hasContact: '/pro/salon',
  hasLogo: '/pro/salon',
  hasCategory: '/pro/cares',
  hasActiveCare: '/pro/cares',
  hasOpeningHours: '/pro/planning',
};

export interface PublishMissingDialogData {
  missing: string[];
}

export type PublishMissingDialogResult =
  | { action: 'goTo'; route: string }
  | { action: 'cancel' };

@Component({
  selector: 'app-publish-missing-dialog',
  standalone: true,
  imports: [MatDialogModule, MatIconModule, TranslocoPipe],
  templateUrl: './publish-missing-dialog.component.html',
  styleUrl: './publish-missing-dialog.component.scss',
})
export class PublishMissingDialogComponent {
  private readonly ref = inject(
    MatDialogRef<PublishMissingDialogComponent, PublishMissingDialogResult>
  );
  protected readonly data = inject<PublishMissingDialogData>(MAT_DIALOG_DATA);

  protected routeFor(missingKey: string): string | null {
    return MISSING_KEY_TO_ROUTE[missingKey] ?? null;
  }

  protected goTo(missingKey: string): void {
    const route = this.routeFor(missingKey);
    if (route) this.ref.close({ action: 'goTo', route });
  }

  protected cancel(): void {
    this.ref.close({ action: 'cancel' });
  }
}
