import { Component, computed, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { provideFrenchDateAdapter } from '../../../../shared/providers/french-date-adapter';
import { TranslocoPipe } from '@jsverse/transloco';
import { toSignal } from '@angular/core/rxjs-interop';
import { output } from '@angular/core';
import { SalonClientService } from '../../../salon-clients/salon-client.service';
import {
  SalonClientResponse,
  CreateSalonClientRequest,
} from '../../../salon-clients/salon-client.model';

@Component({
  selector: 'app-client-create-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatIconModule,
    TranslocoPipe,
  ],
  providers: [provideFrenchDateAdapter()],
  template: `
    <div class="client-form">
      <div class="form-header">
        <div class="avatar-preview">{{ nameInitials() }}</div>
        <h3>{{ 'booking.client.new' | transloco }}</h3>
      </div>

      <div class="form-grid">
        <mat-form-field>
          <mat-label>{{ 'booking.client.name' | transloco }}</mat-label>
          <input matInput [formControl]="nameControl" required />
        </mat-form-field>
        <mat-form-field>
          <mat-label>{{ 'booking.client.phone' | transloco }}</mat-label>
          <input matInput [formControl]="phoneControl" required />
        </mat-form-field>
      </div>

      <mat-form-field class="full-width">
        <mat-label>{{ 'booking.client.email' | transloco }}</mat-label>
        <input matInput [formControl]="emailControl" />
      </mat-form-field>

      <mat-form-field class="full-width">
        <mat-label>{{ 'booking.client.dateOfBirth' | transloco }}</mat-label>
        <input matInput [matDatepicker]="dobPicker" [formControl]="dobControl" />
        <mat-datepicker-toggle matIconSuffix [for]="dobPicker"></mat-datepicker-toggle>
        <mat-datepicker #dobPicker></mat-datepicker>
      </mat-form-field>

      <mat-form-field class="full-width">
        <mat-label>{{ 'booking.client.notes' | transloco }}</mat-label>
        <textarea matInput [formControl]="notesControl" rows="3"></textarea>
      </mat-form-field>

      <div class="form-actions">
        <button class="btn-cancel" (click)="onCancel()">
          {{ 'booking.stepper.back' | transloco }}
        </button>
        <button class="btn-create" data-testid="client-create-submit" [disabled]="!isValid()" (click)="onSubmit()">
          {{ 'booking.client.createAndConfirm' | transloco }}
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      .client-form {
        background: white;
        border-radius: 14px;
        padding: 24px;
      }

      .form-header {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 24px;
      }

      .form-header h3 {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
        color: #333;
      }

      .avatar-preview {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        background: var(--pf-rose);
        color: white;
        font-size: 24px;
        font-weight: 600;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
      }

      .full-width {
        width: 100%;
      }

      .form-actions {
        display: flex;
        gap: 12px;
        justify-content: flex-end;
        margin-top: 16px;
      }

      .btn-cancel {
        background: #e0e0e0;
        color: #333;
        border: none;
        border-radius: 8px;
        padding: 12px 24px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: background 200ms ease;
      }

      .btn-cancel:hover {
        background: #d0d0d0;
      }

      .btn-create {
        background: var(--pf-rose);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 12px 24px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: background 200ms ease;
      }

      .btn-create:hover:not(:disabled) {
        background: #a00554;
      }

      .btn-create:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    `,
  ],
})
export class ClientCreateFormComponent {
  private readonly salonClientService = inject(SalonClientService);

  readonly nameControl = new FormControl('');
  readonly phoneControl = new FormControl('');
  readonly emailControl = new FormControl('');
  readonly dobControl = new FormControl<Date | null>(null);
  readonly notesControl = new FormControl('');

  readonly created = output<SalonClientResponse>();
  readonly cancel = output<void>();

  private readonly nameValue = toSignal(this.nameControl.valueChanges, { initialValue: '' });

  readonly nameInitials = computed(() => {
    const name = this.nameValue() || '';
    return (
      name
        .split(' ')
        .map((n: string) => n[0] || '')
        .join('')
        .toUpperCase()
        .substring(0, 2) || '?'
    );
  });

  isValid(): boolean {
    return !!this.nameControl.value?.trim() && !!this.phoneControl.value?.trim();
  }

  onCancel(): void {
    this.cancel.emit();
  }

  onSubmit(): void {
    if (!this.isValid()) return;

    const dob = this.dobControl.value;
    const request: CreateSalonClientRequest = {
      name: this.nameControl.value!.trim(),
      phone: this.phoneControl.value!.trim(),
      email: this.emailControl.value?.trim() || null,
      dateOfBirth: dob ? dob.toISOString().split('T')[0] : null,
      notes: this.notesControl.value?.trim() || null,
    };

    this.salonClientService.create(request).subscribe((client) => this.created.emit(client));
  }
}
