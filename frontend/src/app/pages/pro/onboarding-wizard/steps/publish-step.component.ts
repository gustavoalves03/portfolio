import { Component, inject, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { DashboardService } from '../../../../features/dashboard/services/dashboard.service';

interface PublishRecap {
  name: string | null;
  addressCity: string | null;
  logoUrl: string | null;
  slug: string | null;
}

@Component({
  selector: 'app-publish-step',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './publish-step.component.html',
  styleUrl: './publish-step.component.scss',
})
export class PublishStepComponent {
  readonly recap = input<PublishRecap | null>(null);
  readonly completed = output<void>();
  readonly back = output<void>();

  private readonly dashboard = inject(DashboardService);

  protected readonly publishing = signal(false);
  protected readonly error = signal<string | null>(null);

  protected onPublish(): void {
    if (this.publishing()) return;
    this.publishing.set(true);
    this.error.set(null);
    this.dashboard.publish().subscribe({
      next: () => { this.publishing.set(false); this.completed.emit(); },
      error: () => { this.publishing.set(false); this.error.set('publish'); },
    });
  }
}
