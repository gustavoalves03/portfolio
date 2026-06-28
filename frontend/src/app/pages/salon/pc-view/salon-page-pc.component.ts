import { Component, ElementRef, PLATFORM_ID, computed, effect, inject, input, output, viewChild } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  PublicSalonResponse,
  PublicCareDto,
} from '../../../features/salon-profile/models/salon-profile.model';
import { SalonPostsViewerComponent } from '../../../features/posts/salon-posts-viewer/salon-posts-viewer.component';
import { CareImageCarouselComponent } from '../../../shared/uis/care-image-carousel/care-image-carousel.component';

/** No opening hours in PublicSalonResponse, so we just show contact info. */

@Component({
  selector: 'app-salon-page-pc',
  standalone: true,
  imports: [RouterLink, TranslocoPipe, SalonPostsViewerComponent, CareImageCarouselComponent],
  templateUrl: './salon-page-pc.component.html',
  styleUrl: './salon-page-pc.component.scss',
})
export class SalonPagePcComponent {
  private readonly platformId = inject(PLATFORM_ID);

  readonly salon = input.required<PublicSalonResponse>();
  readonly slug = input.required<string>();
  readonly isPreviewMode = input(false);
  readonly bookingDisabled = input(false);

  readonly book = output<PublicCareDto>();
  readonly bookFromPost = output<number>();

  // Leaflet map ref + state
  readonly contactMapRef = viewChild<ElementRef<HTMLElement>>('contactMap');
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private mapInstance: any = null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private L: any = null;
  private mapInitialized = false;

  constructor() {
    // Init contact map when the element appears (browser only).
    effect(() => {
      const el = this.contactMapRef()?.nativeElement;
      const salon = this.salon();
      if (
        el &&
        salon &&
        !this.mapInitialized &&
        isPlatformBrowser(this.platformId)
      ) {
        this.mapInitialized = true;
        this.initContactMap(salon);
      }
    });
  }

  // ── Address helpers ───────────────────────────────────────────────────
  readonly fullAddress = computed(() => {
    const s = this.salon();
    const parts = [s.addressStreet, s.addressPostalCode, s.addressCity].filter(
      (p): p is string => !!p
    );
    return parts.join(', ');
  });

  // ── Hero image ────────────────────────────────────────────────────────
  readonly heroImage = computed(() => {
    const s = this.salon();
    return s.heroImageUrl || null;
  });

  // ── Care ranking helpers ─────────────────────────────────────────────
  protected formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h} h ${m.toString().padStart(2, '0')}` : `${h} h`;
  }

  protected formatPrice(cents: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(cents / 100);
  }

  private readonly fallbackGradients = [
    'linear-gradient(135deg, #fce4ed, #f5d0e0)',
    'linear-gradient(135deg, #f3d5c0, #e8c4b0)',
    'linear-gradient(135deg, #d4b5d0, #c8a0c0)',
    'linear-gradient(135deg, #c0d4f3, #b0c4e8)',
  ];

  protected fallbackGradient(index: number): string {
    return this.fallbackGradients[index % this.fallbackGradients.length];
  }

  protected carouselImagesFor(care: PublicCareDto): { url: string }[] {
    return care.imageUrls.map((url) => ({ url }));
  }

  protected onBook(care: PublicCareDto): void {
    if (this.isPreviewMode() || this.bookingDisabled()) return;
    this.book.emit(care);
  }

  protected onBookFromPost(careId: number): void {
    if (this.isPreviewMode() || this.bookingDisabled()) return;
    this.bookFromPost.emit(careId);
  }

  private async initContactMap(salon: PublicSalonResponse): Promise<void> {
    const address = this.fullAddress();
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

    // Geocode the salon address via Nominatim
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
            'box-shadow:0 4px 12px rgba(0,0,0,0.35);box-sizing:border-box;"></div>',
          iconSize: [22, 22],
          iconAnchor: [11, 11],
        });
        this.L.marker([lat, lng], { icon: salonIcon }).addTo(this.mapInstance);
      }
    } catch {
      // Geocoding failed — keep the default world view.
    }
  }
}
