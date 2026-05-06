import { Component, EventEmitter, Output, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
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

  onPublish(): void {
    if (this.publishing()) return;
    this.publishing.set(true);
    this.dashboardService.publish().subscribe({
      next: () => {
        this.publishing.set(false);
        this.published.emit();
      },
      error: () => {
        this.publishing.set(false);
      },
    });
  }
}
