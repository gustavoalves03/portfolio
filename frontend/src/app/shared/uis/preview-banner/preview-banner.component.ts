import { Component, EventEmitter, Output, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { DashboardService } from '../../../features/dashboard/services/dashboard.service';

@Component({
  selector: 'app-preview-banner',
  standalone: true,
  imports: [RouterLink, MatIconModule, MatButtonModule, TranslocoPipe],
  templateUrl: './preview-banner.component.html',
  styleUrl: './preview-banner.component.scss',
})
export class PreviewBannerComponent {
  readonly slug = input.required<string>();
  readonly canPublish = input<boolean>(false);

  /** Fires once the publish call returned successfully. */
  @Output() readonly published = new EventEmitter<void>();

  readonly publishing = signal(false);

  private readonly dashboardService = inject(DashboardService);
  private readonly router = inject(Router);

  onPublish(): void {
    if (this.publishing()) return;
    this.publishing.set(true);
    this.dashboardService.publish().subscribe({
      next: () => {
        this.publishing.set(false);
        this.published.emit();
      },
      error: (err: HttpErrorResponse) => {
        this.publishing.set(false);
        if (err.status === 402) {
          const tier = err.error?.tier ?? 'GESTION';
          const billing = err.error?.billing ?? 'YEARLY';
          this.router.navigate(['/pro/onboarding/payment'], {
            queryParams: { tier, billing },
          });
        }
      },
    });
  }
}
