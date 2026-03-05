import { Component, inject } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-auth-modal',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    TranslocoPipe
  ],
  templateUrl: './auth-modal.component.html',
  styleUrl: './auth-modal.component.scss'
})
export class AuthModalComponent {
  private readonly authService = inject(AuthService);
  private readonly dialogRef = inject(MatDialogRef<AuthModalComponent>);

  loginWithGoogle(): void {
    this.dialogRef.close();
    this.authService.loginWithGoogle();
  }

  loginWithFacebook(): void {
    // TODO: Implement when Facebook OAuth2 is configured in backend
    console.log('Facebook OAuth2: Backend configuration required');
    // this.dialogRef.close();
    // this.authService.loginWithFacebook();
  }

  loginWithApple(): void {
    // TODO: Implement when Apple OAuth2 is configured in backend
    console.log('Apple OAuth2: Backend configuration required');
    // this.dialogRef.close();
    // this.authService.loginWithApple();
  }

  close(): void {
    this.dialogRef.close();
  }
}
