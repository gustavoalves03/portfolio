import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatRadioModule } from '@angular/material/radio';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { SheetHandleComponent } from '../../../../shared/uis/sheet-handle/sheet-handle.component';
import { EmployeesStore } from '../../../../features/employees/employees.store';

@Component({
  selector: 'app-employee-filter-sheet',
  standalone: true,
  imports: [MatRadioModule, FormsModule, TranslocoPipe, SheetHandleComponent],
  providers: [EmployeesStore],
  template: `
    <app-sheet-handle />
    <div class="sheet-wrap">
      <h3>{{ 'pro.history.filter.employee.all' | transloco }}</h3>
      <mat-radio-group [(ngModel)]="value">
        <mat-radio-button [value]="null">{{ 'pro.history.filter.employee.all' | transloco }}</mat-radio-button>
        @for (e of employeesStore.employees(); track e.id) {
          <mat-radio-button [value]="e.id">{{ e.name }}</mat-radio-button>
        }
      </mat-radio-group>
      <button class="apply" (click)="apply()">{{ 'common.apply' | transloco }}</button>
    </div>
  `,
  styles: [`
    .sheet-wrap { padding: 16px; display: flex; flex-direction: column; gap: 14px; max-height: 60vh; overflow-y: auto; }
    h3 { margin: 0; font-size: 16px; color: #333; }
    mat-radio-group { display: flex; flex-direction: column; gap: 8px; }
    .apply {
      margin-top: 8px; padding: 12px; border: none; border-radius: 10px;
      background: var(--pf-rose); color: white; font-weight: 600; font-size: 14px;
      cursor: pointer;
    }
  `],
})
export class EmployeeFilterSheetComponent {
  private readonly dialogRef = inject(MatDialogRef<EmployeeFilterSheetComponent>);
  protected readonly employeesStore = inject(EmployeesStore);
  private readonly data = inject<{ selected: number | null } | null>(MAT_DIALOG_DATA, { optional: true });

  value: number | null = this.data?.selected ?? null;

  apply(): void {
    this.dialogRef.close(this.value);
  }
}
