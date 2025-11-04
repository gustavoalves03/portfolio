import { Component, inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { FormBuilder, FormGroup } from '@angular/forms';
import { CareStatus, CreateCareRequest } from '../../models/cares.model';
import { Category } from '../../../categories/models/categories.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig } from '../../../../shared/models/form-field.model';

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
    this.careForm = this.fb.group({});
    this.initFormConfig();
    // TODO: Charger les catégories
    // this.loadCategories();
  }

  private initFormConfig(): void {
    this.formConfig = {
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
