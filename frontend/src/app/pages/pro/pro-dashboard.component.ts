import { Component, computed, effect, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';
import { ConfirmDialogComponent } from './confirm-dialog.component';

@Component({
  selector: 'app-pro-dashboard',
  standalone: true,
  imports: [
    MatCardModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    RouterLink,
    SlicePipe,
    TranslocoPipe,
  ],
  providers: [DashboardStore],
  templateUrl: './pro-dashboard.component.html',
  styleUrl: './pro-dashboard.component.scss',
})
export class ProDashboardComponent {
  readonly store = inject(DashboardStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  readonly salonUrl = computed(() => {
    const readiness = this.store.readiness();
    return readiness ? '/salon/' + readiness.slug : '';
  });

  constructor() {
    effect(() => {
      if (this.store.isActive()) {
        this.store.loadActivity();
      }
    });

    effect(() => {
      if (this.store.publishSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.dashboard.publishSuccess'),
          undefined,
          { duration: 3000 }
        );
      }
    });

    effect(() => {
      if (this.store.unpublishSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.dashboard.unpublishSuccess'),
          undefined,
          { duration: 3000 }
        );
      }
    });
  }

  onPublish(): void {
    this.store.publish();
  }

  onUnpublish(): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.transloco.translate('pro.dashboard.unpublishConfirmTitle'),
        body: this.transloco.translate('pro.dashboard.unpublishConfirmBody'),
        action: this.transloco.translate('pro.dashboard.unpublishConfirmAction'),
      },
    });

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.store.unpublish();
      }
    });
  }
}
