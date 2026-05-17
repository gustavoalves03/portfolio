import { Component, ElementRef, inject, signal, computed, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';
import { ParallaxHeroComponent } from '../../shared/uis/parallax-hero/parallax-hero.component';
import { ProDemoStoryComponent } from '../../shared/uis/pro-demo-story/pro-demo-story.component';

interface HomeCategory {
  key: string;
  iconPath: string;
}

interface ProFeature {
  readonly key: 'vitrine' | 'planning' | 'clients' | 'payments';
  readonly icon: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatIconModule,
    ParallaxHeroComponent,
    ProDemoStoryComponent,
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly router = inject(Router);
  private readonly discoveryService = inject(DiscoveryService);

  readonly searchQuery = signal('');
  readonly searchPlace = signal('');

  readonly salons = toSignal(
    toObservable(this.searchQuery).pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) => this.discoveryService.searchSalons(null, q || null)),
    ),
    { initialValue: [] as SalonCard[] },
  );

  readonly featuredSalons = computed(() => this.salons().slice(0, 4));

  readonly categories: HomeCategory[] = [
    {
      key: 'facial',
      iconPath: 'M12 3c1.5 3 3.5 4.5 6 5-2.5.5-4.5 2-6 5-1.5-3-3.5-4.5-6-5 2.5-.5 4.5-2 6-5z',
    },
    {
      key: 'hair',
      iconPath: 'M5 6c2 4 5 5 7 5s5-1 7-5 M5 14c2 4 5 5 7 5s5-1 7-5',
    },
    { key: 'nails', iconPath: 'rect' },
    { key: 'massage', iconPath: 'M4 12c4-3 6-3 8 0s4 3 8 0 M4 17c4-3 6-3 8 0s4 3 8 0' },
    {
      key: 'lashes',
      iconPath: 'M12 4v8 M8 12c0 2.5 2 4.5 4 4.5s4-2 4-4.5 M5 18c2 1.5 4.5 2 7 2s5-.5 7-2',
    },
    { key: 'makeup', iconPath: 'circle' },
    {
      key: 'waxing',
      iconPath: 'M5 8c4-2 10-2 14 0 M5 12c4-2 10-2 14 0 M5 16c4-2 10-2 14 0',
    },
    { key: 'wellness', iconPath: 'M6 18l3-3 4 4 5-7' },
    { key: 'barber', iconPath: 'rect-clip' },
    {
      key: 'rituals',
      iconPath: 'M12 3a9 9 0 1 0 9 9c0-1.5-.5-3-1.5-4.5 M12 7v5l3 2',
    },
  ];

  readonly cities = computed(() => {
    const seen = new Set<string>();
    const ordered: string[] = [];
    for (const salon of this.salons()) {
      const city = salon.addressCity?.trim();
      if (!city || seen.has(city)) continue;
      seen.add(city);
      ordered.push(city);
    }
    return ordered;
  });

  readonly hasEnoughCities = computed(() => this.cities().length >= 5);

  readonly availabilityLabels = ['today', 'tomorrow', 'thisWeek', 'tomorrow'] as const;

  readonly proFeatures: readonly ProFeature[] = [
    { key: 'vitrine', icon: 'storefront' },
    { key: 'planning', icon: 'event_available' },
    { key: 'clients', icon: 'groups' },
    { key: 'payments', icon: 'payments' },
  ];

  protected readonly demoSentinel = viewChild<ElementRef<HTMLElement>>('demoSentinel');

  scrollToDemo(): void {
    const el = this.demoSentinel()?.nativeElement;
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  onLaunchPro(): void {
    this.router.navigate(['/pricing']);
  }

  readonly heroBlobUrl =
    'https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?w=1280&q=80&auto=format&fit=crop';

  readonly fallbackCovers = [
    'https://images.unsplash.com/photo-1560066984-138dadb4c035?w=720&q=80&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1633681926022-84c23e8cb2d6?w=720&q=80&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?w=720&q=80&auto=format&fit=crop',
    'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?w=720&q=80&auto=format&fit=crop',
  ];

  coverFor(salon: SalonCard, index: number): string {
    return salon.logoUrl ?? this.fallbackCovers[index % this.fallbackCovers.length];
  }

  availabilityKey(index: number): string {
    return this.availabilityLabels[index % this.availabilityLabels.length];
  }

  onSearch(): void {
    this.router.navigate(['/discover'], {
      queryParams: {
        q: this.searchQuery() || null,
        city: this.searchPlace() || null,
      },
    });
  }

  onDiscoverAll(): void {
    this.router.navigate(['/discover']);
  }

  onCategory(key: string): void {
    this.router.navigate(['/discover'], { queryParams: { category: key } });
  }

  onCity(city: string): void {
    this.router.navigate(['/discover'], { queryParams: { city } });
  }

  onSalon(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  onProCta(): void {
    this.router.navigate(['/pricing']);
  }
}
