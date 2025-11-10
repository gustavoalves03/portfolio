import { Component, inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { ModalForm } from '../../../../shared/uis/modal-form/modal-form';

interface DeleteCareDialogData {
  careName: string;
}

@Component({
  selector: 'app-delete-care',
  standalone: true,
  imports: [ModalForm],
  templateUrl: './delete-care.component.html',
  styleUrl: './delete-care.component.scss'
})
export class DeleteCareComponent {
  private dialogRef = inject(MatDialogRef<DeleteCareComponent>);
  protected data = inject<DeleteCareDialogData>(MAT_DIALOG_DATA);

  get title(): string {
    return `Supprimer ${this.data.careName ? `"${this.data.careName}"` : 'le soin'}`;
  }

  confirm(): void {
    this.dialogRef.close(true);
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
