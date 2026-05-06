import {
  Component,
  ElementRef,
  PLATFORM_ID,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { ParallaxHeroComponent } from '../../shared/uis/parallax-hero/parallax-hero.component';
import { MockBrowserComponent } from '../../shared/uis/mock-browser/mock-browser.component';
import { useIntersection } from '../../shared/utils/use-intersection';
import { RevenueWidgetComponent } from './widgets/revenue-widget.component';
import { CalendarWidgetComponent } from './widgets/calendar-widget.component';
import { ReviewsWidgetComponent } from './widgets/reviews-widget.component';

interface Feature {
  readonly key: 'vitrine' | 'planning' | 'clients' | 'payments';
  readonly icon: string;
}

@Component({
  selector: 'app-pricing',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    TranslocoPipe,
    ParallaxHeroComponent,
    MockBrowserComponent,
    RevenueWidgetComponent,
    CalendarWidgetComponent,
    ReviewsWidgetComponent,
  ],
  templateUrl: './pricing.component.html',
  styleUrl: './pricing.component.scss',
})
export class PricingComponent {
  protected readonly demoSentinel = viewChild.required<ElementRef<HTMLElement>>('demoSentinel');
  protected readonly demoVisible = useIntersection(this.demoSentinel, 0.3);

  // Cascade: widget 1 immediate, widget 2 +400ms, widget 3 +800ms.
  private readonly platformId = inject(PLATFORM_ID);
  private readonly w1 = signal(false);
  private readonly w2 = signal(false);
  private readonly w3 = signal(false);

  protected readonly w1Started = computed(() => this.w1());
  protected readonly w2Started = computed(() => this.w2());
  protected readonly w3Started = computed(() => this.w3());

  protected readonly features: readonly Feature[] = [
    { key: 'vitrine', icon: 'storefront' },
    { key: 'planning', icon: 'event_available' },
    { key: 'clients', icon: 'groups' },
    { key: 'payments', icon: 'payments' },
  ];

  constructor() {
    effect(() => {
      if (!this.demoVisible()) return;
      if (this.w1()) return; // Already triggered.
      this.w1.set(true);
      if (!isPlatformBrowser(this.platformId)) {
        // SSR/static: just flip them all so the final state renders.
        this.w2.set(true);
        this.w3.set(true);
        return;
      }
      setTimeout(() => this.w2.set(true), 400);
      setTimeout(() => this.w3.set(true), 800);
    });
  }

  scrollToDemo(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const el = this.demoSentinel().nativeElement;
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
