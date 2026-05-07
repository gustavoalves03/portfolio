import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  MISSING_KEY_TO_STEP,
  WizardStepKey,
} from '../../../features/onboarding/wizard/wizard-step.model';

export interface PublishMissingDialogData {
  missing: string[];
}

export type PublishMissingDialogResult =
  | { action: 'goTo'; step: WizardStepKey }
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

  protected stepFor(missingKey: string): WizardStepKey | null {
    return MISSING_KEY_TO_STEP[missingKey] ?? null;
  }

  protected goTo(missingKey: string): void {
    const step = this.stepFor(missingKey);
    if (step) this.ref.close({ action: 'goTo', step });
  }

  protected cancel(): void {
    this.ref.close({ action: 'cancel' });
  }
}
