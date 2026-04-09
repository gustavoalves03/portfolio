import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';

export interface RateVisitDialogData {
  visitId: number;
  careName: string | null;
}

@Component({
  selector: 'app-rate-visit-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'evolution.rateTitle' | transloco }}</h2>
    <mat-dialog-content>
      @if (data.careName) {
        <p class="care-name">{{ data.careName }}</p>
      }

      <div class="stars-row">
        @for (star of starsArray; track star) {
          <button class="star-btn" (click)="score.set(star)">
            <mat-icon [class.filled]="star <= score()">
              {{ star <= score() ? 'star' : 'star_border' }}
            </mat-icon>
          </button>
        }
      </div>

      <mat-form-field appearance="outline" class="comment-field">
        <textarea
          matInput
          [(ngModel)]="comment"
          rows="3"
          placeholder="..."
        ></textarea>
      </mat-form-field>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | transloco }}</button>
      <button
        mat-flat-button
        class="submit-btn"
        [disabled]="score() === 0"
        (click)="onSubmit()"
      >
        {{ 'evolution.rateSubmit' | transloco }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .care-name {
      font-size: 14px;
      color: #888;
      margin: 0 0 16px;
    }

    .stars-row {
      display: flex;
      justify-content: center;
      gap: 8px;
      margin-bottom: 20px;
    }

    .star-btn {
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      transition: transform 150ms ease;

      &:hover {
        transform: scale(1.2);
      }

      mat-icon {
        font-size: 36px;
        width: 36px;
        height: 36px;
        color: #ccc;
        transition: color 150ms ease;

        &.filled {
          color: #ffc107;
        }
      }
    }

    .comment-field {
      width: 100%;
    }

    .submit-btn {
      background: var(--mat-sys-primary, #a8385d) !important;
      color: #fff !important;
    }
  `,
})
export class RateVisitDialogComponent {
  readonly dialogRef = inject(MatDialogRef<RateVisitDialogComponent>);
  readonly data: RateVisitDialogData = inject(MAT_DIALOG_DATA);

  readonly score = signal(0);
  comment = '';

  readonly starsArray = [1, 2, 3, 4, 5];

  onSubmit(): void {
    if (this.score() > 0) {
      this.dialogRef.close({ score: this.score(), comment: this.comment });
    }
  }
}
