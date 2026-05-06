import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { OnboardingProgress, OnboardingStep, OnboardingStepKey } from '../../../features/onboarding/onboarding-step.model';
import { SheetHandleComponent } from '../../uis/sheet-handle/sheet-handle.component';

export interface OnboardingSheetData {
  readonly steps: readonly OnboardingStep[];
  readonly progress: OnboardingProgress;
  readonly canPublish: boolean;
  readonly slug: string;
}

export type OnboardingSheetResult =
  | { action: 'step'; stepKey: OnboardingStepKey }
  | { action: 'preview' }
  | { action: 'publish' }
  | { action: 'dashboard' };

@Component({
  selector: 'app-onboarding-indicator-sheet',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, TranslocoPipe, SheetHandleComponent],
  templateUrl: './onboarding-indicator-sheet.component.html',
  styleUrl: './onboarding-indicator-sheet.component.scss',
})
export class OnboardingIndicatorSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<OnboardingIndicatorSheetComponent>);
  private readonly data = inject<OnboardingSheetData>(MAT_DIALOG_DATA);

  readonly steps = this.data.steps;
  readonly progress = this.data.progress;
  readonly canPublish = this.data.canPublish;
  readonly slug = this.data.slug;

  onStep(stepKey: OnboardingStepKey): void {
    this.dialogRef.close({ action: 'step', stepKey });
  }

  onPreview(): void {
    this.dialogRef.close({ action: 'preview' });
  }

  onPublish(): void {
    this.dialogRef.close({ action: 'publish' });
  }

  onDashboard(): void {
    this.dialogRef.close({ action: 'dashboard' });
  }
}
