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
import { Router } from '@angular/router';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';
import { PostsService } from '../../features/posts/posts.service';
import { RecentPost } from '../../features/posts/posts.model';
import { RecentPostsViewerComponent } from '../../features/posts/recent-posts-viewer/recent-posts-viewer.component';

const SALON_GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];


@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule, RecentPostsViewerComponent],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnDestroy {
  private router = inject(Router);
  private discoveryService = inject(DiscoveryService);
  private postsService = inject(PostsService);
  private platformId = inject(PLATFORM_ID);

  readonly isBrowser = isPlatformBrowser(this.platformId);

  private mapInstance: any = null;
  private markerMap = new Map<string, any>();
  private userMarker: any = null;
  private L: any = null;
  private geocodeCache = new Map<string, { lat: number; lng: number } | null>();

  readonly miniMapRef = viewChild<ElementRef<HTMLElement>>('miniMap');

  readonly searchQuery = signal('');

  readonly salons = toSignal(
    toObservable(this.searchQuery).pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) => this.discoveryService.searchSalons(null, q || null)),
    ),
    { initialValue: [] as SalonCard[] },
  );

  readonly recentPosts = toSignal(this.postsService.listRecentPublic(), {
    initialValue: [] as RecentPost[],
  });
  readonly selectedSlug = signal<string | null>(null);
  private readonly mapReady = signal(false);
  private isTouchDevice = false;

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.isTouchDevice = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
    }

    // Init map when DOM element is available
    effect(() => {
      const mapEl = this.miniMapRef()?.nativeElement;
      if (mapEl && !this.mapReady() && isPlatformBrowser(this.platformId)) {
        this.initMap();
      }
    });

    // Plot salons when map is ready AND salons change
    effect(() => {
      const ready = this.mapReady();
      const salons = this.salons();
      if (ready && salons.length > 0) {
        this.geocodeAndPlotSalons(salons);
      }
    });
  }

  getSalonGradient(index: number): string {
    return SALON_GRADIENTS[index % SALON_GRADIENTS.length];
  }


  ngOnDestroy(): void {
    if (this.mapInstance) {
      this.mapInstance.remove();
      this.mapInstance = null;
    }
  }

  onSearch(): void {
    // Search is handled reactively via toObservable(searchQuery) with debounce
  }

  onSalonCardClick(slug: string): void {
    // Touch device: first tap selects (shows on map), second tap navigates
    if (this.isTouchDevice) {
      if (this.selectedSlug() === slug) {
        this.router.navigate(['/salon', slug]);
      } else {
        this.selectedSlug.set(slug);
        this.flyToSalon(slug);
      }
    } else {
      // Desktop: click always navigates
      this.router.navigate(['/salon', slug]);
    }
  }

  onSalonHover(slug: string): void {
    if (this.isTouchDevice) return;
    this.selectedSlug.set(slug);
    this.flyToSalon(slug);
  }

  onSalonLeave(): void {
    if (this.isTouchDevice) return;
    this.selectedSlug.set(null);
    // Fit back to all markers
    if (this.mapInstance && this.markerMap.size > 0) {
      const bounds: [number, number][] = [];
      for (const [, m] of this.markerMap) {
        const pos = m.getLatLng();
        bounds.push([pos.lat, pos.lng]);
      }
      if (bounds.length > 1) {
        this.mapInstance.fitBounds(bounds, { padding: [20, 20], maxZoom: 13 });
      }
    }
  }

  private flyToSalon(slug: string): void {
    const marker = this.markerMap.get(slug);
    if (marker && this.mapInstance) {
      const pos = marker.getLatLng();
      this.mapInstance.flyTo([pos.lat, pos.lng], 14, { duration: 0.5 });
      marker.openPopup();
    }
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  onDiscoverAll(): void {
    this.router.navigate(['/discover']);
  }

  onProCta(): void {
    this.router.navigate(['/pricing']);
  }

  private async initMap(): Promise<void> {
    const leaflet = await import('leaflet');
    this.L = leaflet.default || leaflet;

    const el = this.miniMapRef()?.nativeElement;
    if (!el) return;

    this.mapInstance = this.L
      .map(el, {
        zoomControl: false,
        attributionControl: false,
      })
      .setView([46.6, 2.3], 6);

    this.L
      .tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
        maxZoom: 19,
      })
      .addTo(this.mapInstance);

    this.locateUser();

    // Signal map is ready — triggers salon plotting effect
    setTimeout(() => {
      this.mapInstance?.invalidateSize();
      this.mapReady.set(true);
    }, 250);
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
          iconSize: [14, 14],
          iconAnchor: [7, 7],
        });

        this.userMarker = this.L
          .marker([latitude, longitude], { icon: userIcon })
          .addTo(this.mapInstance);
      },
      () => {
        // Geolocation denied or failed
      },
      { timeout: 8000 },
    );
  }

  private async geocodeAndPlotSalons(salons: SalonCard[]): Promise<void> {
    if (!this.mapInstance || !this.L) return;

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
          html: '<div class="salon-pin-small"></div>',
          iconSize: [20, 20],
          iconAnchor: [10, 20],
        });

        const popup = `<div style="font-family:Roboto,sans-serif"><strong style="font-size:12px">${salon.name}</strong></div>`;

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
        // Geocoding failed for this salon
      }
    }

    if (this.userMarker) {
      const userPos = this.userMarker.getLatLng();
      bounds.push([userPos.lat, userPos.lng]);
    }

    if (bounds.length > 1) {
      this.mapInstance.fitBounds(bounds, { padding: [20, 20], maxZoom: 13 });
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
