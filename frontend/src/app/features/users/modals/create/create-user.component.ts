import { Component, inject, OnInit } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { FormBuilder, FormGroup } from '@angular/forms';
import { CreateUserRequest } from '../../models/users.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig } from '../../../../shared/models/form-field.model';

@Component({
  selector: 'app-create-user',
  standalone: true,
  imports: [ModalForm, DynamicForm],
  template: `
    <modal-form
      title="Créer un utilisateur"
      icon="person_add"
      iconColor="#fa8e8e"
      saveLabel="Créer l'utilisateur"
      [saveDisabled]="userForm.invalid"
      (save)="onSave()"
      (cancel)="onCancel()">
      <dynamic-form [config]="formConfig" [formGroup]="userForm" />
    </modal-form>
  `
})
export class CreateUserComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateUserComponent>);
  private fb = inject(FormBuilder);

  userForm!: FormGroup;
  formConfig!: DynamicFormConfig;

  ngOnInit(): void {
    this.userForm = this.fb.group({});
    this.initFormConfig();
  }

  private initFormConfig(): void {
    this.formConfig = {
      rows: [
        {
          fields: [
            {
              name: 'name',
              label: 'Nom complet',
              type: 'text',
              placeholder: 'Ex: Marie Dupont',
              icon: 'person',
              required: true,
              minLength: 3,
              width: 'full'
            }
          ]
        },
        {
          fields: [
            {
              name: 'email',
              label: 'Email',
              type: 'email',
              placeholder: 'exemple@email.com',
              icon: 'email',
              required: true,
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
    if (this.userForm.valid) {
      const userData: CreateUserRequest = this.userForm.value;
      this.dialogRef.close(userData);
    } else {
      this.userForm.markAllAsTouched();
    }
  }
}
