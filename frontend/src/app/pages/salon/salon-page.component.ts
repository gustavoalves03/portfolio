import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../features/salon-profile/services/salon-profile.service';
import { PublicSalonResponse, PublicCareDto } from '../../features/salon-profile/models/salon-profile.model';
import { BookingDialogComponent, BookingDialogData } from './booking-dialog/booking-dialog.component';

@Component({
  selector: 'app-salon-page',
  standalone: true,
  imports: [MatExpansionModule, MatProgressSpinnerModule, MatButtonModule, TranslocoPipe],
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
