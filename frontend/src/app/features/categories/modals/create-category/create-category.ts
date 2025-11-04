import {Component, inject} from '@angular/core';
import {DynamicForm} from '../../../../shared/uis/dynamic-form/dynamic-form';
import {ModalForm} from '../../../../shared/uis/modal-form/modal-form';
import {MatDialogRef} from '@angular/material/dialog';
import {FormBuilder, FormGroup} from '@angular/forms';
import {DynamicFormConfig} from '../../../../shared/models/form-field.model';
import {CreateCareRequest} from '../../../cares/models/cares.model';

@Component({
  selector: 'app-create-category',
  imports: [
    DynamicForm,
    ModalForm
  ],
  templateUrl: './create-category.html',
  styleUrl: './create-category.scss'
})
export class CreateCategory {

  private dialogRef = inject(MatDialogRef<CreateCategory>);
  private fb = inject(FormBuilder);

  careForm!: FormGroup;

  formConfig!: DynamicFormConfig;

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
        }
      ]
    }
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

}
