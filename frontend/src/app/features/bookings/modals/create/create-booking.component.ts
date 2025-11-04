import { Component, inject, OnInit } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { FormBuilder, FormGroup } from '@angular/forms';
import { CareBookingStatus, CreateCareBookingRequest } from '../../models/bookings.model';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';
import { DynamicForm } from '../../../../shared/uis/dynamic-form/dynamic-form';
import { DynamicFormConfig } from '../../../../shared/models/form-field.model';
import { CaresStore } from '../../../cares/store/cares.store';
import { UsersStore } from '../../../users/store/users.store';

@Component({
  selector: 'app-create-booking',
  standalone: true,
  imports: [ModalForm, DynamicForm],
  template: `
    <modal-form
      title="Créer une réservation"
      icon="event"
      iconColor="#fa8e8e"
      saveLabel="Créer la réservation"
      [saveDisabled]="bookingForm.invalid"
      (save)="onSave()"
      (cancel)="onCancel()">
      <dynamic-form [config]="formConfig" [formGroup]="bookingForm" />
    </modal-form>
  `,
  providers: [CaresStore, UsersStore]
})
export class CreateBookingComponent implements OnInit {
  private dialogRef = inject(MatDialogRef<CreateBookingComponent>);
  private fb = inject(FormBuilder);
  readonly caresStore = inject(CaresStore);
  readonly usersStore = inject(UsersStore);

  bookingForm!: FormGroup;
  formConfig!: DynamicFormConfig;

  ngOnInit(): void {
    this.bookingForm = this.fb.group({});
    this.initFormConfig();
  }

  private initFormConfig(): void {
    this.formConfig = {
      rows: [
        {
          fields: [
            {
              name: 'userId',
              label: 'Utilisateur',
              type: 'select',
              icon: 'person',
              options: this.usersStore.users().map(u => ({
                label: `${u.name} (${u.email})`,
                value: u.id
              })),
              required: true,
              width: 'full'
            }
          ]
        },
        {
          fields: [
            {
              name: 'careId',
              label: 'Soin',
              type: 'select',
              icon: 'spa',
              options: this.caresStore.availableCares().map(c => ({
                label: `${c.name} - ${c.price}€`,
                value: c.id
              })),
              required: true,
              width: 'full'
            }
          ]
        },
        {
          fields: [
            {
              name: 'quantity',
              label: 'Quantité',
              type: 'number',
              placeholder: '1',
              icon: 'numbers',
              min: 1,
              required: true,
              width: 'half'
            },
            {
              name: 'status',
              label: 'Statut',
              type: 'select',
              icon: 'toggle_on',
              options: Object.values(CareBookingStatus).map(s => ({
                label: this.getStatusLabel(s),
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

  private getStatusLabel(status: CareBookingStatus): string {
    const labels: Record<CareBookingStatus, string> = {
      [CareBookingStatus.PENDING]: 'En attente',
      [CareBookingStatus.CONFIRMED]: 'Confirmé',
      [CareBookingStatus.CANCELLED]: 'Annulé'
    };
    return labels[status];
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.bookingForm.valid) {
      const bookingData: CreateCareBookingRequest = this.bookingForm.value;
      this.dialogRef.close(bookingData);
    } else {
      this.bookingForm.markAllAsTouched();
    }
  }
}
