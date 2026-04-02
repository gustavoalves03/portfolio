import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';

export interface ReviewLeaveDialogData {
  action: 'APPROVED' | 'REJECTED';
  employeeName: string;
}

export interface ReviewLeaveDialogResult {
  status: 'APPROVED' | 'REJECTED';
  reviewerNote?: string;
}

@Component({
  selector: 'app-review-leave-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>
      {{
        (data.action === 'APPROVED' ? 'pro.leaves.approveTitle' : 'pro.leaves.rejectTitle')
          | transloco
      }}
    </h2>
    <mat-dialog-content>
      <p class="dialog-subtitle">{{ data.employeeName }}</p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'pro.leaves.reviewerNote' | transloco }}</mat-label>
        <textarea
          matInput
          [(ngModel)]="note"
          rows="3"
          [placeholder]="('pro.leaves.reviewerNotePlaceholder' | transloco)"
        ></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | transloco }}</button>
      <button
        mat-flat-button
        [class]="data.action === 'APPROVED' ? 'approve-btn' : 'reject-btn'"
        (click)="confirm()"
      >
        {{
          (data.action === 'APPROVED' ? 'pro.leaves.approve' : 'pro.leaves.reject') | transloco
        }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .dialog-subtitle {
        font-size: 14px;
        color: #666;
        margin-bottom: 16px;
      }
      .full-width {
        width: 100%;
      }
      .approve-btn {
        background-color: #cc0066 !important;
        color: white !important;
      }
      .reject-btn {
        background-color: #999 !important;
        color: white !important;
      }
    `,
  ],
})
export class ReviewLeaveDialogComponent {
  readonly data = inject<ReviewLeaveDialogData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<ReviewLeaveDialogComponent>);
  note = '';

  confirm(): void {
    const result: ReviewLeaveDialogResult = {
      status: this.data.action,
      reviewerNote: this.note.trim() || undefined,
    };
    this.dialogRef.close(result);
  }
}
