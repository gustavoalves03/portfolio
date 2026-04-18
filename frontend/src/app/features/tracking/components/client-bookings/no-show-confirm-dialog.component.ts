import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';

export interface NoShowConfirmData {
  careName: string;
  appointmentDate: string;
}

@Component({
  selector: 'app-no-show-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, TranslocoPipe, SheetHandleComponent],
  template: `
    <app-sheet-handle />
    <h2 mat-dialog-title>{{ 'tracking.bookings.noShow.title' | transloco }}</h2>
    <mat-dialog-content>
      <p class="message">
        {{
          'tracking.bookings.noShow.message'
            | transloco: { careName: data.careName, date: data.appointmentDate }
        }}
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close(false)">
        {{ 'tracking.bookings.noShow.cancel' | transloco }}
      </button>
      <button mat-flat-button class="no-show-btn" (click)="dialogRef.close(true)">
        {{ 'tracking.bookings.noShow.submit' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .message {
        font-size: 14px;
        color: #4b5563;
        line-height: 1.5;
      }
      .no-show-btn {
        background: #dc2626 !important;
        color: white !important;
        border-radius: 8px;
      }
    `,
  ],
})
export class NoShowConfirmDialogComponent {
  readonly data = inject<NoShowConfirmData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<NoShowConfirmDialogComponent>);
}
