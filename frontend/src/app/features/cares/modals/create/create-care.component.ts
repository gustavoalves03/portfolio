import { Component, inject, OnInit, signal } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { FormBuilder, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { Care, CareStatus, CreateCareRequest, CareImage, CareImageRequest } from '../../models/cares.model';
import { Category } from '../../../categories/models/categories.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig, FormFieldConfig } from '../../../../shared/models/form-field.model';
import { ImageManager } from '../../../../shared/uis/image-manager/image-manager.component';
import { ImageCarousel } from '../../../../shared/uis/image-carousel/image-carousel.component';

interface CreateCareDialogData {
  categories: Category[];
  care?: Care;
  viewOnly?: boolean;
}

@Component({
  selector: 'app-create',
  standalone: true,
  imports: [
    ModalForm,
    DynamicForm,
    MatTabsModule,
    ImageManager,
    ImageCarousel
  ],
  templateUrl: './create-care.component.html',
  styleUrl: './create-care.component.scss'
})
export class CreateCare implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateCare>);
  private fb = inject(FormBuilder);
  private data = inject<CreateCareDialogData>(MAT_DIALOG_DATA);

  careForm!: FormGroup;
  categories: Category[] = this.mergeCategoriesWithCurrent();
  formConfig!: DynamicFormConfig;
  images = signal<CareImage[]>([]);

  readonly isViewOnly = !!this.data?.viewOnly;
  readonly isEditMode = !!this.data?.care && !this.isViewOnly;
  readonly dialogTitle = this.isViewOnly ? 'Détails de la prestation' : this.isEditMode ? 'Modifier un soin' : 'Créer un nouveau soin';
  readonly saveLabel = this.isEditMode ? 'Mettre à jour le soin' : 'Créer le soin';

  ngOnInit(): void {
    this.formConfig = this.buildFormConfig();
    this.careForm = this.buildFormGroup(this.formConfig);
    if (this.data?.care) {
      this.populateForm(this.data.care);
      if (this.data.care.images) {
        // Sort images by order before setting them
        const sortedImages = [...this.data.care.images].sort((a, b) => a.order - b.order);
        this.images.set(sortedImages);
      }
    }

    // Disable all form controls in view-only mode
    if (this.isViewOnly) {
      this.careForm.disable();
    }
  }

  onImagesChange(updatedImages: CareImage[]): void {
    this.images.set(updatedImages);
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
    if (field.name === 'status') {
      return CareStatus.ACTIVE;
    }
    if (field.name === 'categoryId') {
      return this.data?.care?.category?.id ?? this.categories[0]?.id ?? null;
    }
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

    // Convert images: extract base64Data from url for new images
    const imagesWithBase64: CareImageRequest[] = this.images().map(img => {
      // If image has 'file' property, it's a new image with Data URL in 'url'
      if (img.file && img.url) {
        return {
          id: img.id,
          name: img.name,
          order: img.order,
          // Don't send url for new images (redundant with base64Data)
          base64Data: img.url // Data URL already in correct format (data:image/png;base64,...)
        };
      }
      // Existing image from server (has numeric ID, no 'file', no base64Data)
      // Only send essential fields: id, name, order (no url needed)
      return {
        id: img.id,
        name: img.name,
        order: img.order
        // No url, no base64Data for existing images
      };
    });

    return {
      name: rawValue['name'] ?? '',
      description: rawValue['description'] ?? '',
      status: rawValue['status'] as CareStatus,
      categoryId: Number(
        rawValue['categoryId'] ?? this.data?.care?.category?.id ?? 0
      ),
      price: Number(rawValue['price'] ?? 0),
      duration: Number(rawValue['duration'] ?? 0),
      images: imagesWithBase64,
    };
  }

  private populateForm(care: Care): void {
    this.careForm.patchValue({
      name: care.name,
      description: care.description,
      price: care.price,
      duration: care.duration,
      status: care.status,
      categoryId: care.category?.id ?? null,
    });
  }

  private mergeCategoriesWithCurrent(): Category[] {
    const categories = this.data?.categories ?? [];
    const careCategory = this.data?.care?.category;
    if (!careCategory) {
      return categories;
    }
    const exists = categories.some(category => category.id === careCategory.id);
    return exists ? categories : [...categories, careCategory];
  }
}
