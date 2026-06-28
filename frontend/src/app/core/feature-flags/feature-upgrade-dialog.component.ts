import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { FeatureKey } from './feature-key';

export interface FeatureUpgradeDialogData {
  feature: FeatureKey;
}

/**
 * Upsell modal shown when a user tries to activate a tier-gated capability they
 * don't have (e.g. the "manage employees" toggle in Settings). Closing it leaves
 * the underlying setting untouched, so the caller's control reverts to off.
 */
@Component({
  selector: 'lp-feature-upgrade-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ 'features.upgrade.title' | transloco }}</h2>
    <mat-dialog-content>
      <p class="upgrade-text">{{ upsellKey | transloco }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'features.upgrade.later' | transloco }}</button>
      <a
        mat-flat-button
        color="primary"
        routerLink="/pricing"
        [queryParams]="{ highlight: data.feature }"
        mat-dialog-close
      >
        {{ 'features.locked.cta' | transloco }}
      </a>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .upgrade-text {
        margin: 0;
        color: var(--mat-sys-on-surface);
        font-size: 14px;
        line-height: 1.5;
      }
    `,
  ],
})
export class FeatureUpgradeDialogComponent {
  readonly data = inject<FeatureUpgradeDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<FeatureUpgradeDialogComponent>);

  readonly upsellKey = `features.locked.${this.data.feature.toLowerCase()}`;
}
