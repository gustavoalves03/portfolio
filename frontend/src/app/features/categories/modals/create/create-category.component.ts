import { Component, inject, OnInit } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { FormBuilder, FormGroup } from '@angular/forms';
import { CreateCategoryRequest } from '../../models/categories.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig } from '../../../../shared/models/form-field.model';

@Component({
  selector: 'app-create-category',
  standalone: true,
  imports: [ModalForm, DynamicForm],
  template: `
    <modal-form
      title="Créer une catégorie"
      icon="category"
      iconColor="#fa8e8e"
      saveLabel="Créer la catégorie"
      [saveDisabled]="categoryForm.invalid"
      (save)="onSave()"
      (cancel)="onCancel()">
      <dynamic-form [config]="formConfig" [formGroup]="categoryForm" />
    </modal-form>
  `
})
export class CreateCategoryComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateCategoryComponent>);
  private fb = inject(FormBuilder);

  categoryForm!: FormGroup;
  formConfig!: DynamicFormConfig;

  ngOnInit(): void {
    this.categoryForm = this.fb.group({});
    this.initFormConfig();
  }

  private initFormConfig(): void {
    this.formConfig = {
      rows: [
        {
          fields: [
            {
              name: 'name',
              label: 'Nom de la catégorie',
              type: 'text',
              placeholder: 'Ex: Soins du visage',
              icon: 'label',
              required: true,
              minLength: 3,
              width: 'full'
            }
          ]
        },
        {
          fields: [
            {
              name: 'description',
              label: 'Description',
              type: 'textarea',
              placeholder: 'Description de la catégorie...',
              icon: 'description',
              rows: 3,
              width: 'full'
            }
          ]
        }
      ]
    };
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.categoryForm.valid) {
      const categoryData: CreateCategoryRequest = this.categoryForm.value;
      this.dialogRef.close(categoryData);
    } else {
      this.categoryForm.markAllAsTouched();
    }
  }
}
