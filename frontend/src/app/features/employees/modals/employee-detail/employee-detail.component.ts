import { Component, inject, OnInit, signal } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { Care } from '../../../cares/models/cares.model';
import { Employee, UpdateEmployeeRequest } from '../../employees.model';

interface EmployeeDetailDialogData {
  employee: Employee;
  cares: Care[];
}

@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatSlideToggleModule,
    MatCheckboxModule,
    MatIconModule,
    TranslocoPipe,
  ],
  templateUrl: './employee-detail.component.html',
  styleUrl: './employee-detail.component.scss',
})
export class EmployeeDetailComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<EmployeeDetailComponent>);
  private data = inject<EmployeeDetailDialogData>(MAT_DIALOG_DATA);

  employee!: Employee;
  cares: Care[] = [];
  isActive = signal(false);
  selectedCareIds = new Set<number>();

  ngOnInit(): void {
    this.employee = this.data.employee;
    this.cares = this.data.cares ?? [];
    this.isActive.set(this.employee.active);
    this.employee.assignedCares.forEach((c) => this.selectedCareIds.add(c.id));
  }

  toggleCare(careId: number): void {
    if (this.selectedCareIds.has(careId)) {
      this.selectedCareIds.delete(careId);
    } else {
      this.selectedCareIds.add(careId);
    }
  }

  onToggleActive(checked: boolean): void {
    this.isActive.set(checked);
  }

  onDelete(): void {
    this.dialogRef.close({ delete: true });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    const req: UpdateEmployeeRequest = {
      active: this.isActive(),
      careIds: [...this.selectedCareIds],
    };
    this.dialogRef.close(req);
  }
}
