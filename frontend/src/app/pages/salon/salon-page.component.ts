import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../features/salon-profile/services/salon-profile.service';
import { PublicSalonResponse } from '../../features/salon-profile/models/salon-profile.model';

@Component({
  selector: 'app-salon-page',
  standalone: true,
  imports: [MatExpansionModule, MatProgressSpinnerModule, TranslocoPipe],
  templateUrl: './salon-page.component.html',
  styleUrl: './salon-page.component.scss',
})
export class SalonPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly salonService = inject(SalonProfileService);

  protected salon = signal<PublicSalonResponse | null>(null);
  protected loading = signal(true);
  protected notFound = signal(false);

  ngOnInit(): void {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      this.notFound.set(true);
      this.loading.set(false);
      return;
    }

    this.salonService.getPublicSalon(slug).subscribe({
      next: (salon) => {
        this.salon.set(salon);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  protected formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  protected formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' \u20AC';
  }
}
