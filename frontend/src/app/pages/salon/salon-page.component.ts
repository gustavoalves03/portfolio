import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../features/salon-profile/services/salon-profile.service';
import { PublicSalonResponse, PublicCareDto } from '../../features/salon-profile/models/salon-profile.model';
import { BookingDialogComponent, BookingDialogData } from './booking-dialog/booking-dialog.component';
import { SalonPostsViewerComponent } from '../../features/posts/salon-posts-viewer/salon-posts-viewer.component';

@Component({
  selector: 'app-salon-page',
  standalone: true,
  imports: [
    MatExpansionModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatIconModule,
    TranslocoPipe,
    SalonPostsViewerComponent,
  ],
  templateUrl: './salon-page.component.html',
  styleUrl: './salon-page.component.scss',
})
export class SalonPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly salonService = inject(SalonProfileService);
  private readonly dialog = inject(MatDialog);

  protected salon = signal<PublicSalonResponse | null>(null);
  protected loading = signal(true);
  protected notFound = signal(false);
  protected readonly activeTab = signal<'cares' | 'posts'>('cares');

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

  private readonly fallbackGradients = [
    'linear-gradient(135deg, #f3d5c0, #e8c4b0)',
    'linear-gradient(135deg, #d4b5d0, #c8a0c0)',
    'linear-gradient(135deg, #b5d4c0, #a0c8b0)',
    'linear-gradient(135deg, #c0d4f3, #b0c4e8)',
  ];

  protected fallbackGradient(index: number): string {
    return this.fallbackGradients[index % this.fallbackGradients.length];
  }

  protected onBook(care: PublicCareDto): void {
    this.openBookingDialog(care);
  }

  protected onBookFromPost(careId: number): void {
    const salon = this.salon();
    if (!salon) return;
    for (const cat of salon.categories) {
      const care = cat.cares.find((c) => c.id === careId);
      if (care) {
        this.openBookingDialog(care);
        return;
      }
    }
  }

  protected get slug(): string {
    return this.route.snapshot.paramMap.get('slug') ?? '';
  }

  private openBookingDialog(care: PublicCareDto): void {
    const slug = this.salon()?.slug;
    if (!slug) return;

    this.dialog.open(BookingDialogComponent, {
      width: '360px',
      disableClose: false,
      data: { slug, care } as BookingDialogData,
    });
  }
}
