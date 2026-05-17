import { Component, inject, signal, computed, effect, OnDestroy, ElementRef, viewChild, PLATFORM_ID, DestroyRef } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonProfileService } from '../../features/salon-profile/services/salon-profile.service';
import { AuthService } from '../../core/auth/auth.service';
import { PublicSalonResponse, PublicCareDto } from '../../features/salon-profile/models/salon-profile.model';
import { BookingDialogComponent, BookingDialogData } from './booking-dialog/booking-dialog.component';
import { bottomSheetConfig } from '../../shared/uis/sheet-handle/bottom-sheet.config';
import { SalonPostsViewerComponent } from '../../features/posts/salon-posts-viewer/salon-posts-viewer.component';
import { PreviewBannerComponent } from '../../shared/uis/preview-banner/preview-banner.component';
import { SalonPagePcComponent } from './pc-view/salon-page-pc.component';

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
    PreviewBannerComponent,
    SalonPagePcComponent,
  ],
  templateUrl: './salon-page.component.html',
  styleUrl: './salon-page.component.scss',
})
export class SalonPageComponent implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly salonService = inject(SalonProfileService);
  private readonly dialog = inject(MatDialog);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);

  // Bookings are disabled while a PRO/EMPLOYEE/ADMIN is in tenant context.
  // Visitors not logged in (and clients in client mode) can always book.
  protected readonly bookingDisabled = computed(
    () => this.auth.isAuthenticated() && !this.auth.isClientMode(),
  );

  protected salon = signal<PublicSalonResponse | null>(null);
  protected loading = signal(true);
  protected notFound = signal(false);
  protected readonly activeTab = signal<'cares' | 'posts' | 'contact'>('cares');

  /**
   * True when viewport ≥ 1024px and we're in a browser. SSR returns false so
   * the mobile layout is rendered server-side (the historical behaviour).
   */
  protected readonly isPc = signal(false);

  protected readonly isPreviewMode = computed(() => {
    const s = this.salon();
    return !!s && s.status !== 'ACTIVE';
  });

  protected readonly canPublishFromBanner = computed(() => {
    // The public DTO doesn't carry canPublish. We conservatively show the
    // publish button only when status is DRAFT (which means the storefront
    // was returned because the owner is logged in). The backend returns 400
    // with a missing-fields list if the request is invalid; the dashboard
    // remains the authoritative place for the full checklist.
    return this.isPreviewMode();
  });

  readonly contactMapRef = viewChild<ElementRef<HTMLElement>>('contactMap');
  private mapInstance: any = null;
  private L: any = null;
  private contactMapInitialized = false;

  constructor() {
    // Track viewport ≥ 1024px (PC). SSR-safe: stays false until hydration.
    if (isPlatformBrowser(this.platformId)) {
      const mql = window.matchMedia('(min-width: 1024px)');
      this.isPc.set(mql.matches);
      const handler = (e: MediaQueryListEvent) => this.isPc.set(e.matches);
      mql.addEventListener('change', handler);
      this.destroyRef.onDestroy(() => mql.removeEventListener('change', handler));
    }

    // Load salon
    const slug = this.route.snapshot.paramMap.get('slug');
    const previewToken = this.route.snapshot.queryParamMap.get('preview');
    if (!slug) {
      this.notFound.set(true);
      this.loading.set(false);
    } else {
      this.salonService.getPublicSalon(slug, previewToken).subscribe({
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

    // Init contact map when tab is active and DOM is ready
    effect(() => {
      const el = this.contactMapRef()?.nativeElement;
      const salon = this.salon();
      if (el && salon && !this.contactMapInitialized && isPlatformBrowser(this.platformId)) {
        this.contactMapInitialized = true;
        this.initContactMap(salon);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.mapInstance) {
      this.mapInstance.remove();
      this.mapInstance = null;
    }
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
    if (this.bookingDisabled()) return;
    this.openBookingDialog(care);
  }

  protected onPublishedFromBanner(): void {
    const slug = this.salon()?.slug;
    if (!slug) return;
    const previewToken = this.route.snapshot.queryParamMap.get('preview');
    this.salonService.getPublicSalon(slug, previewToken).subscribe({
      next: (salon) => this.salon.set(salon),
    });
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

  protected get fullAddress(): string {
    const s = this.salon();
    if (!s) return '';
    return [s.addressStreet, s.addressPostalCode, s.addressCity, s.addressCountry]
      .filter(Boolean)
      .join(', ');
  }

  private async initContactMap(salon: PublicSalonResponse): Promise<void> {
    const address = this.fullAddress;
    if (!address) return;

    const leaflet = await import('leaflet');
    this.L = leaflet.default || leaflet;

    const el = this.contactMapRef()?.nativeElement;
    if (!el) return;

    this.mapInstance = this.L.map(el, {
      zoomControl: true,
      attributionControl: false,
    }).setView([46.6, 2.3], 6);

    this.L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
      maxZoom: 19,
    }).addTo(this.mapInstance);

    setTimeout(() => this.mapInstance?.invalidateSize(), 250);

    // Geocode the salon address
    try {
      const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address)}&limit=1`;
      const res = await fetch(url);
      const data = await res.json();
      if (data.length > 0) {
        const lat = parseFloat(data[0].lat);
        const lng = parseFloat(data[0].lon);

        this.mapInstance.setView([lat, lng], 15);

        const salonIcon = this.L.divIcon({
          className: 'salon-map-marker',
          html:
            '<div style="width:22px;height:22px;border-radius:50%;' +
            'background:#c66075;border:3px solid #fff;' +
            'box-shadow:0 2px 6px rgba(0,0,0,0.35);box-sizing:border-box;"></div>',
          iconSize: [22, 22],
          iconAnchor: [11, 11],
        });

        this.L.marker([lat, lng], { icon: salonIcon }).addTo(this.mapInstance);
      }
    } catch {
      // Geocoding failed
    }
  }

  private openBookingDialog(care: PublicCareDto): void {
    const slug = this.salon()?.slug;
    if (!slug) return;

    this.dialog.open(BookingDialogComponent, bottomSheetConfig({
      disableClose: false,
      data: { slug, care } as BookingDialogData,
    }));
  }
}
