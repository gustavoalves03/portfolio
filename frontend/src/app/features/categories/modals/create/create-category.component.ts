import { Component, inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { FormBuilder, FormGroup } from '@angular/forms';
import { CreateCategoryRequest, Category } from '../../models/categories.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig } from '../../../../shared/models/form-field.model';
import { TranslocoService } from '@jsverse/transloco';

export interface CategoryDialogData {
  category?: Category;
}

@Component({
  selector: 'app-create-category',
  standalone: true,
  imports: [ModalForm, DynamicForm],
  template: `
    <modal-form
      [title]="dialogTitle"
      icon="category"
      iconColor="#fa8e8e"
      [saveLabel]="saveLabel"
      [saveDisabled]="categoryForm.invalid"
      (save)="onSave()"
      (cancel)="onCancel()">
      <dynamic-form [config]="formConfig" [formGroup]="categoryForm" />
    </modal-form>
  `
})
export class CreateCategoryComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateCategoryComponent>);
  private data: CategoryDialogData = inject(MAT_DIALOG_DATA, { optional: true }) ?? {};
  private fb = inject(FormBuilder);
  private i18n = inject(TranslocoService);

  categoryForm!: FormGroup;
  formConfig!: DynamicFormConfig;
  dialogTitle = '';
  saveLabel = '';

  get isEditMode(): boolean {
    return !!this.data.category;
  }

  ngOnInit(): void {
    this.dialogTitle = this.isEditMode
      ? this.i18n.translate('pro.categories.edit')
      : this.i18n.translate('pro.categories.add');
    this.saveLabel = this.isEditMode
      ? this.i18n.translate('common.save')
      : this.i18n.translate('pro.categories.add');
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
              label: this.i18n.translate('categories.columns.name'),
              type: 'text',
              placeholder: 'Ex: Soins du visage',
              icon: 'label',
              required: true,
              width: 'full',
              value: this.data.category?.name ?? ''
            }
          ]
        },
        {
          fields: [
            {
              name: 'description',
              label: this.i18n.translate('categories.columns.description'),
              type: 'textarea',
              placeholder: '',
              icon: 'description',
              rows: 3,
              width: 'full',
              value: this.data.category?.description ?? ''
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
