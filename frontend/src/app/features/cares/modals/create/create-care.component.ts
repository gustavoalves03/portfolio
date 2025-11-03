import { Component, inject, OnInit } from '@angular/core';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CareStatus, CreateCareRequest } from '../../models/cares.model';
import { Category } from '../../../categories/models/categories.model';

@Component({
  selector: 'app-create',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    ReactiveFormsModule
  ],
  templateUrl: './create-care.component.html',
  styleUrl: './create-care.component.scss'
})
export class CreateCare implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateCare>);
  private fb = inject(FormBuilder);

  careForm!: FormGroup;
  careStatuses = Object.values(CareStatus);

  // TODO: Récupérer les catégories depuis un service
  categories: Category[] = [];

  ngOnInit(): void {
    this.initForm();
    // TODO: Charger les catégories
    // this.loadCategories();
  }

  private initForm(): void {
    this.careForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      description: ['', [Validators.required, Validators.minLength(10)]],
      price: [0, [Validators.required, Validators.min(0)]],
      duration: [30, [Validators.required, Validators.min(1)]],
      status: [CareStatus.ACTIVE, Validators.required],
      categoryId: ['', Validators.required]
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.careForm.valid) {
      const careData: CreateCareRequest = this.careForm.value;
      this.dialogRef.close(careData);
    } else {
      this.careForm.markAllAsTouched();
    }
  }

  getErrorMessage(fieldName: string): string {
    const field = this.careForm.get(fieldName);
    if (field?.hasError('required')) {
      return 'Ce champ est requis';
    }
    if (field?.hasError('minLength')) {
      const minLength = field.errors?.['minLength'].requiredLength;
      return `Minimum ${minLength} caractères requis`;
    }
    if (field?.hasError('min')) {
      return 'La valeur doit être positive';
    }
    return '';
  }
}
