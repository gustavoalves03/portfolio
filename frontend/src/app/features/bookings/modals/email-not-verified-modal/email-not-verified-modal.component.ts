import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../../../core/auth/auth.service';

@Component({
  selector: 'app-email-not-verified-modal',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatDialogModule, TranslocoModule],
  templateUrl: './email-not-verified-modal.component.html',
  styleUrl: './email-not-verified-modal.component.scss',
})
export class EmailNotVerifiedModalComponent {
  private readonly auth = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<EmailNotVerifiedModalComponent>);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  readonly user = this.auth.user;

  resend() {
    this.auth.sendVerification().subscribe({
      next: () => {
        this.snackBar.open(
          this.transloco.translate('verifyEmail.required.resendCooldown'),
          'OK',
          { duration: 4000 },
        );
        this.dialogRef.close();
      },
    });
  }
}
