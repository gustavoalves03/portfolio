import { Component, inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { Care } from '../../../cares/models/cares.model';
import { CreateEmployeeRequest } from '../../employees.model';

interface CreateEmployeeDialogData {
  cares: Care[];
}

@Component({
  selector: 'app-create-employee',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    TranslocoPipe,
  ],
  templateUrl: './create-employee.component.html',
  styleUrl: './create-employee.component.scss',
})
export class CreateEmployeeComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateEmployeeComponent>);
  private fb = inject(FormBuilder);
  private data = inject<CreateEmployeeDialogData>(MAT_DIALOG_DATA);

  form!: FormGroup;
  cares: Care[] = this.data?.cares ?? [];
  selectedCareIds = new Set<number>();

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      password: ['', [Validators.required, Validators.minLength(8)]],
    });
  }

  toggleCare(careId: number): void {
    if (this.selectedCareIds.has(careId)) {
      this.selectedCareIds.delete(careId);
    } else {
      this.selectedCareIds.add(careId);
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.form.valid) {
      const val = this.form.getRawValue();
      const req: CreateEmployeeRequest = {
        name: val.name,
        email: val.email,
        password: val.password,
        careIds: [...this.selectedCareIds],
      };
      if (val.phone?.trim()) {
        req.phone = val.phone.trim();
      }
      this.dialogRef.close(req);
    } else {
      this.form.markAllAsTouched();
    }
  }
}
