import { Component, input, output } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { FormValidationHintComponent } from '../form-validation-hint/form-validation-hint.component';

/**
 * Composant modal réutilisable pour les formulaires
 *
 * Usage:
 * <modal-form
 *   title="Créer un soin"
 *   icon="spa"
 *   [saveDisabled]="form.invalid"
 *   (save)="onSave()"
 *   (cancel)="onCancel()">
 *   <!-- Contenu du formulaire en ng-content -->
 *   <form [formGroup]="form">...</form>
 * </modal-form>
 */
@Component({
  selector: 'modal-form',
  standalone: true,
  styleUrl: 'modal-form.scss',
  templateUrl: 'modal-form.html',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, FormValidationHintComponent],
})
export class ModalForm {
  // Inputs
  title = input.required<string>();
  icon = input<string>('edit');
  iconColor = input<string>('#fa8e8e');
  saveLabel = input<string>('Sauvegarder');
  cancelLabel = input<string>('Annuler');
  saveIcon = input<string>('check');
  cancelIcon = input<string>('close');
  saveDisabled = input<boolean>(false);
  showCloseButton = input<boolean>(true);
  hideSaveButton = input<boolean>(false);
  // Optional: pass the modal's FormGroup so we can render an inline
  // "check these fields" hint above the save button.
  form = input<FormGroup | null>(null);

  // Outputs
  save = output<void>();
  cancel = output<void>();

  onSave() {
    this.save.emit();
  }

  onCancel() {
    this.cancel.emit();
  }
}
