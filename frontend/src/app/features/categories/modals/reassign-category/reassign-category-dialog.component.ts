import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { Category } from '../../models/categories.model';

export interface ReassignCategoryDialogData {
  categoryId: number;
  categoryName: string;
  careCount: number;
  availableCategories: Category[];
}

@Component({
  selector: 'app-reassign-category-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatSelectModule, MatFormFieldModule, FormsModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ 'pro.categories.reassign.title' | transloco }}</h2>
    <mat-dialog-content>
      <p class="mb-4 text-sm text-neutral-600">
        {{ 'pro.categories.reassign.message' | transloco: { count: data.careCount } }}
      </p>
      <mat-form-field appearance="fill" class="w-full">
        <mat-label>{{ 'pro.categories.reassign.select' | transloco }}</mat-label>
        <mat-select [(value)]="selectedTargetId">
          @for (cat of data.availableCategories; track cat.id) {
            @if (cat.id !== data.categoryId) {
              <mat-option [value]="cat.id">{{ cat.name }}</mat-option>
            }
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'common.cancel' | transloco }}</button>
      <button mat-flat-button color="warn" [disabled]="!selectedTargetId" (click)="onConfirm()">
        {{ 'pro.categories.reassign.confirm' | transloco }}
      </button>
    </mat-dialog-actions>
  `
})
export class ReassignCategoryDialogComponent {
  data: ReassignCategoryDialogData = inject(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<ReassignCategoryDialogComponent>);

  selectedTargetId: number | null = null;

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    if (this.selectedTargetId) {
      this.dialogRef.close(this.selectedTargetId);
    }
  }
}
