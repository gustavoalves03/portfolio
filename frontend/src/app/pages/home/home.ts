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
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';
import { PostsService } from '../../features/posts/posts.service';
import { RecentPost } from '../../features/posts/posts.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

const SALON_GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];

const POST_GRADIENTS: Record<string, string> = {
  BEFORE_AFTER: 'linear-gradient(180deg, #f0d5c0, #e8c0a8)',
  PHOTO: 'linear-gradient(180deg, #d5dce8, #c0c8d5)',
  CAROUSEL: 'linear-gradient(180deg, #dce8d2, #c8d5b8)',
};

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TranslocoPipe, MatIconModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnDestroy {
  private router = inject(Router);
  private discoveryService = inject(DiscoveryService);
  private postsService = inject(PostsService);
  private platformId = inject(PLATFORM_ID);
  private transloco = inject(TranslocoService);
  private apiBaseUrl = inject(API_BASE_URL);

  private mapInstance: any = null;
  private markerMap = new Map<string, any>();
  private userMarker: any = null;
  private L: any = null;
  private geocodeCache = new Map<string, { lat: number; lng: number } | null>();

  readonly miniMapRef = viewChild<ElementRef<HTMLElement>>('miniMap');

  readonly salons = toSignal(this.discoveryService.searchSalons(), {
    initialValue: [] as SalonCard[],
  });

  readonly recentPosts = toSignal(this.postsService.listRecentPublic(), {
    initialValue: [] as RecentPost[],
  });

  readonly searchQuery = signal('');
  private mapInitialized = false;

  constructor() {
    // Watch for salons + miniMap DOM element to both be ready
    effect(() => {
      const salons = this.salons();
      const mapEl = this.miniMapRef()?.nativeElement;
      if (salons.length > 0 && mapEl && !this.mapInitialized && isPlatformBrowser(this.platformId)) {
        this.mapInitialized = true;
        this.initMap();
      }
    });
  }

  getSalonGradient(index: number): string {
    return SALON_GRADIENTS[index % SALON_GRADIENTS.length];
  }

  getPostGradient(type: string): string {
    return POST_GRADIENTS[type] || POST_GRADIENTS['PHOTO'];
  }

  getPostTypeLabel(type: string): string {
    switch (type) {
      case 'BEFORE_AFTER':
        return this.transloco.translate('posts.typeBeforeAfter');
      case 'PHOTO':
        return this.transloco.translate('posts.typePhoto');
      case 'CAROUSEL':
        return this.transloco.translate('posts.typeCarousel');
      default:
        return type;
    }
  }

  getThumbnailUrl(post: RecentPost): string | null {
    if (!post.thumbnailUrl) return null;
    if (post.thumbnailUrl.startsWith('http')) return post.thumbnailUrl;
    return `${this.apiBaseUrl}${post.thumbnailUrl}`;
  }

  ngOnDestroy(): void {
    if (this.mapInstance) {
      this.mapInstance.remove();
      this.mapInstance = null;
    }
  }

  onSearch(): void {
    const q = this.searchQuery().trim();
    if (q) {
      this.router.navigate(['/discover'], { queryParams: { q } });
    }
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  onDiscoverAll(): void {
    this.router.navigate(['/discover']);
  }

  onPostClick(post: RecentPost): void {
    if (post.salonSlug) {
      this.router.navigate(['/salon', post.salonSlug]);
    }
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

    const currentSalons = this.salons();
    if (currentSalons.length > 0) {
      this.geocodeAndPlotSalons(currentSalons);
    }
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

        const marker = this.L
          .marker([coords.lat, coords.lng], { icon: salonIcon })
          .addTo(this.mapInstance);

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
