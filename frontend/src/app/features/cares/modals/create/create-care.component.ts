import { Component, inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { FormBuilder, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { CareStatus, CreateCareRequest } from '../../models/cares.model';
import { Category } from '../../../categories/models/categories.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig, FormFieldConfig } from '../../../../shared/models/form-field.model';

interface CreateCareDialogData {
  categories: Category[];
}

@Component({
  selector: 'app-create',
  standalone: true,
  imports: [
    ModalForm,
    DynamicForm
  ],
  templateUrl: './create-care.component.html',
  styleUrl: './create-care.component.scss'
})
export class CreateCare implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateCare>);
  private fb = inject(FormBuilder);
  private data = inject<CreateCareDialogData>(MAT_DIALOG_DATA);

  careForm!: FormGroup;
  categories: Category[] = this.data.categories;
  formConfig!: DynamicFormConfig;

  ngOnInit(): void {
    this.formConfig = this.buildFormConfig();
    this.careForm = this.buildFormGroup(this.formConfig);
    // TODO: Charger les catégories
    // this.loadCategories();
  }

  private buildFormConfig(): DynamicFormConfig {
    return {
      rows: [
        // Nom
        {
          fields: [
            {
              name: 'name',
              label: 'Nom du soin',
              type: 'text',
              placeholder: 'Ex: Massage relaxant',
              icon: 'edit',
              required: true,
              minLength: 3,
              width: 'full'
            }
          ]
        },
        // Description
        {
          fields: [
            {
              name: 'description',
              label: 'Description',
              type: 'textarea',
              placeholder: 'Décrivez le soin en détail...',
              icon: 'description',
              rows: 4,
              required: true,
              minLength: 10,
              width: 'full'
            }
          ]
        },
        // Prix et Durée
        {
          fields: [
            {
              name: 'price',
              label: 'Prix (€)',
              type: 'number',
              placeholder: '50.00',
              icon: 'euro',
              suffix: '€',
              step: 5,
              min: 5,
              required: true,
              width: 'half'
            },
            {
              name: 'duration',
              label: 'Durée (minutes)',
              type: 'number',
              placeholder: '30',
              icon: 'schedule',
              suffix: 'min',
              min: 1,
              required: true,
              width: 'half'
            }
          ]
        },
        // Catégorie et Statut
        {
          fields: [
            {
              name: 'categoryId',
              label: 'Catégorie',
              type: 'select',
              icon: 'category',
              options: this.mapCategoriesToOptions(),
              required: true,
              width: 'half'
            },
            {
              name: 'status',
              label: 'Statut',
              type: 'select',
              icon: 'toggle_on',
              options: Object.values(CareStatus).map(s => ({
                label: s === 'ACTIVE' ? 'Actif' : 'Inactif',
                value: s
              })),
              required: true,
              width: 'half'
            }
          ]
        }
      ]
    };
  }

  private mapCategoriesToOptions(): Array<{ label: string; value: number }> {
    return this.categories.map(c => ({ label: c.name, value: c.id }));
  }

  private buildFormGroup(config: DynamicFormConfig): FormGroup {
    const group = this.fb.group({});

    config.rows.forEach(row => {
      row.fields.forEach(field => {
        const validators = this.getValidators(field);
        const defaultValue = this.getDefaultValue(field);
        group.addControl(
          field.name,
          this.fb.control({ value: defaultValue, disabled: field.disabled }, validators)
        );
      });
    });

    return group;
  }

  private getValidators(field: FormFieldConfig): ValidatorFn[] {
    const validators: ValidatorFn[] = [];

    if (field.required) {
      validators.push(Validators.required);
    }
    if (field.minLength) {
      validators.push(Validators.minLength(field.minLength));
    }
    if (field.maxLength) {
      validators.push(Validators.maxLength(field.maxLength));
    }
    if (field.min !== undefined) {
      validators.push(Validators.min(field.min));
    }
    if (field.max !== undefined) {
      validators.push(Validators.max(field.max));
    }
    if (field.pattern) {
      validators.push(Validators.pattern(field.pattern));
    }
    if (field.type === 'email') {
      validators.push(Validators.email);
    }

    return validators;
  }

  private getDefaultValue(field: FormFieldConfig): any {
    if (field.type === 'number') return 0;
    if (field.type === 'select' && field.options?.length) return field.options[0].value;
    return '';
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.careForm.valid) {
      const careData = this.mapFormToRequest();
      this.dialogRef.close(careData);
    } else {
      this.careForm.markAllAsTouched();
    }
  }

  private mapFormToRequest(): CreateCareRequest {
    const rawValue = this.careForm.getRawValue();

    return {
      name: rawValue['name'] ?? '',
      description: rawValue['description'] ?? '',
      status: rawValue['status'] as CareStatus,
      categoryId: rawValue['categoryId'] ?? 0,
      price: Number(rawValue['price'] ?? 0),
      duration: Number(rawValue['duration'] ?? 0),
    };
  }
}
