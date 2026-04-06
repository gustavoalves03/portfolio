import {
  Component,
  inject,
  signal,
  effect,
  PLATFORM_ID,
  OnDestroy,
  ElementRef,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged, merge, switchMap } from 'rxjs';
import { toObservable } from '@angular/core/rxjs-interop';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';

const GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];

@Component({
  selector: 'app-discover-page',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule],
  templateUrl: './discover-page.component.html',
  styleUrl: './discover-page.component.scss',
})
export class DiscoverPageComponent implements OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private discoveryService = inject(DiscoveryService);
  private platformId = inject(PLATFORM_ID);
  private transloco = inject(TranslocoService);

  readonly searchQuery = signal('');
  readonly hoveredSlug = signal<string | null>(null);

  private mapInstance: any = null;
  private markerMap = new Map<string, any>();
  private userMarker: any = null;
  private L: any = null;
  private geocodeCache = new Map<string, { lat: number; lng: number } | null>();
  readonly mapContainer = viewChild<ElementRef<HTMLElement>>('mapContainer');

  // Signal to track when map is ready — used by the salon-plotting effect
  private readonly mapReady = signal(false);

  readonly salons = toSignal(
    merge(
      // React to URL query params (initial load, shared links)
      this.route.queryParamMap.pipe(
        switchMap((p) => {
          const q = p.get('q');
          if (q) this.searchQuery.set(q);
          return this.discoveryService.searchSalons(p.get('category'), q);
        }),
      ),
      // React to live typing with debounce
      toObservable(this.searchQuery).pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => this.discoveryService.searchSalons(null, q || null)),
      ),
    ),
    { initialValue: [] as SalonCard[] },
  );

  constructor() {
    // Init map when DOM element is available
    effect(() => {
      const el = this.mapContainer()?.nativeElement;
      if (el && !this.mapReady() && isPlatformBrowser(this.platformId)) {
        this.initMap();
      }
    });

    // Plot salons when BOTH map is ready AND salons are loaded
    effect(() => {
      const ready = this.mapReady();
      const salons = this.salons();
      if (ready && salons.length > 0) {
        this.geocodeAndPlotSalons(salons);
      }
    });
  }

  getGradient(index: number): string {
    return GRADIENTS[index % GRADIENTS.length];
  }

  ngOnDestroy(): void {
    if (this.mapInstance) {
      this.mapInstance.remove();
      this.mapInstance = null;
    }
  }

  onSearch(): void {
    // Search is handled reactively via toObservable(searchQuery) with debounce
    // This method is kept for form submit (pressing Enter)
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  onCardEnter(slug: string): void {
    this.hoveredSlug.set(slug);
    const marker = this.markerMap.get(slug);
    if (!marker) return;
    const pinEl = marker.getElement()?.querySelector('.salon-pin');
    if (pinEl) pinEl.classList.add('highlighted');
    marker.openPopup();
  }

  onCardLeave(slug: string): void {
    this.hoveredSlug.set(null);
    const marker = this.markerMap.get(slug);
    if (!marker) return;
    const pinEl = marker.getElement()?.querySelector('.salon-pin');
    if (pinEl) pinEl.classList.remove('highlighted');
    marker.closePopup();
  }

  truncate(text: string | null, max: number): string {
    if (!text) return '';
    const plain = text.replace(/<[^>]*>/g, '');
    return plain.length > max ? plain.substring(0, max) + '...' : plain;
  }

  private async initMap(): Promise<void> {
    const leaflet = await import('leaflet');
    this.L = leaflet.default || leaflet;

    const el = this.mapContainer()?.nativeElement;
    if (!el) return;

    this.mapInstance = this.L.map(el, {
      zoomControl: true,
      attributionControl: true,
    }).setView([46.6, 2.3], 6);

    this.L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; OpenStreetMap',
      maxZoom: 19,
    }).addTo(this.mapInstance);

    // Force Leaflet to recalculate size after layout settles
    setTimeout(() => {
      this.mapInstance?.invalidateSize();
      // Signal that map is ready — triggers salon plotting effect
      this.mapReady.set(true);
    }, 250);

    this.locateUser();
  }

  private locateUser(): void {
    if (!navigator.geolocation || !this.mapInstance) return;

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude, longitude } = pos.coords;
        if (!this.mapInstance) return;

        this.mapInstance.setView([latitude, longitude], 11);

        if (this.userMarker) {
          this.mapInstance.removeLayer(this.userMarker);
        }

        const userIcon = this.L.divIcon({
          className: 'user-location-marker',
          html: '<div class="user-dot"></div>',
          iconSize: [16, 16],
          iconAnchor: [8, 8],
        });

        this.userMarker = this.L
          .marker([latitude, longitude], { icon: userIcon })
          .addTo(this.mapInstance)
          .bindPopup(this.transloco.translate('discover.yourLocation'));
      },
      () => {
        // Geolocation denied or failed
      },
      { timeout: 8000 },
    );
  }

  private async geocodeAndPlotSalons(salons: SalonCard[]): Promise<void> {
    if (!this.mapInstance || !this.L) return;

    // Clear existing markers
    for (const [, m] of this.markerMap) {
      this.mapInstance.removeLayer(m);
    }
    this.markerMap.clear();

    const bounds: [number, number][] = [];

    for (const salon of salons) {
      if (!salon.fullAddress) continue;

      try {
        const coords = await this.geocodeAddress(salon.fullAddress);
        if (!coords) continue;

        const salonIcon = this.L.divIcon({
          className: 'salon-map-marker',
          html: '<div class="salon-pin"></div>',
          iconSize: [24, 24],
          iconAnchor: [12, 24],
          popupAnchor: [0, -24],
        });

        const popup = `
          <div style="font-family:Roboto,sans-serif;min-width:140px;cursor:pointer">
            <strong style="font-size:13px">${salon.name}</strong>
            ${salon.addressCity ? `<div style="font-size:11px;color:#888;margin-top:2px">${salon.addressCity}</div>` : ''}
          </div>
        `;

        const marker = this.L
          .marker([coords.lat, coords.lng], { icon: salonIcon })
          .addTo(this.mapInstance)
          .bindPopup(popup);

        marker.on('click', () => {
          this.router.navigate(['/salon', salon.slug]);
        });

        this.markerMap.set(salon.slug, marker);
        bounds.push([coords.lat, coords.lng]);
      } catch {
        // Geocoding failed
      }
    }

    if (this.userMarker) {
      const userPos = this.userMarker.getLatLng();
      bounds.push([userPos.lat, userPos.lng]);
    }

    if (bounds.length > 1) {
      this.mapInstance.fitBounds(bounds, { padding: [40, 40], maxZoom: 13 });
    } else if (bounds.length === 1) {
      this.mapInstance.setView(bounds[0], 13);
    }
  }

  private async geocodeAddress(address: string): Promise<{ lat: number; lng: number } | null> {
    if (this.geocodeCache.has(address)) {
      return this.geocodeCache.get(address)!;
    }

    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address)}&limit=1`;
    try {
      const res = await fetch(url);
      const data = await res.json();
      if (data.length > 0) {
        const result = { lat: parseFloat(data[0].lat), lng: parseFloat(data[0].lon) };
        this.geocodeCache.set(address, result);
        return result;
      }
    } catch {
      // Network error
    }
    this.geocodeCache.set(address, null);
    return null;
  }
}
