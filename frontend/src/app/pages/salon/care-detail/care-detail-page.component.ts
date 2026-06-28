import {
  Component,
  computed,
  inject,
  signal,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser, Location } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  PublicCareDto,
  PublicSalonResponse,
} from '../../../features/salon-profile/models/salon-profile.model';
import { BookingDialogComponent, BookingDialogData } from '../booking-dialog/booking-dialog.component';
import { bottomSheetConfig } from '../../../shared/uis/sheet-handle/bottom-sheet.config';
import { CareImageCarouselComponent } from '../../../shared/uis/care-image-carousel/care-image-carousel.component';

/**
 * Dedicated page for a single care: full-width gallery on top, full description
 * and metadata below, and a Réserver action. Reached by clicking a care card on
 * the public salon page. No new backend endpoint — the care is resolved from the
 * existing public-salon payload.
 */
@Component({
  selector: 'app-care-detail-page',
  standalone: true,
  imports: [
    RouterLink,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatIconModule,
    TranslocoPipe,
    CareImageCarouselComponent,
  ],
  templateUrl: './care-detail-page.component.html',
  styleUrl: './care-detail-page.component.scss',
})
export class CareDetailPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly salonService = inject(SalonProfileService);
  private readonly dialog = inject(MatDialog);
  private readonly auth = inject(AuthService);
  private readonly location = inject(Location);
  private readonly platformId = inject(PLATFORM_ID);

  protected readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  private readonly careId = Number(this.route.snapshot.paramMap.get('careId'));

  protected readonly salon = signal<PublicSalonResponse | null>(null);
  protected readonly care = signal<PublicCareDto | null>(null);
  protected readonly categoryName = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly notFound = signal(false);

  /** Bookings are disabled while a PRO/EMPLOYEE/ADMIN is in tenant context. */
  protected readonly bookingDisabled = computed(
    () => this.auth.isAuthenticated() && !this.auth.isClientMode(),
  );

  protected readonly carouselImages = computed(() =>
    (this.care()?.imageUrls ?? []).map((url) => ({ url })),
  );

  constructor() {
    const previewToken = this.route.snapshot.queryParamMap.get('preview');
    this.salonService.getPublicSalon(this.slug, previewToken).subscribe({
      next: (salon) => {
        this.salon.set(salon);
        for (const cat of salon.categories) {
          const found = cat.cares.find((c) => c.id === this.careId);
          if (found) {
            this.care.set(found);
            this.categoryName.set(cat.name);
            break;
          }
        }
        if (!this.care()) this.notFound.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  protected onBook(): void {
    const care = this.care();
    if (!care || this.bookingDisabled()) return;
    this.dialog.open(
      BookingDialogComponent,
      bottomSheetConfig({
        disableClose: false,
        data: { slug: this.slug, care } as BookingDialogData,
      }),
    );
  }

  protected goBack(): void {
    if (isPlatformBrowser(this.platformId) && window.history.length > 1) {
      this.location.back();
    }
    // Fallback handled by the [routerLink] on the back control in the template.
  }

  protected formatPrice(cents: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
    }).format(cents / 100);
  }

  protected formatDuration(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) return `${m} min`;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }
}
