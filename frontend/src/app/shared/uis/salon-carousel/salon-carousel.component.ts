import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonCard } from '../../../features/discovery/discovery.model';

const SALON_GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];

interface DisplayCard {
  readonly salon: SalonCard;
  readonly index: number;
  readonly offset: number;
  readonly position: 'center' | 'side' | 'side-far' | 'hidden';
  readonly gradient: string;
}

@Component({
  selector: 'app-salon-carousel',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslocoPipe],
  templateUrl: './salon-carousel.component.html',
  styleUrl: './salon-carousel.component.scss',
})
export class SalonCarouselComponent {
  readonly salons = input.required<SalonCard[]>();

  readonly centerIndex = signal(0);
  private readonly flippedSlugs = signal<ReadonlySet<string>>(new Set());

  protected readonly displayCards = computed<DisplayCard[]>(() => {
    const salons = this.salons();
    if (salons.length === 0) return [];
    const center = this.centerIndex();
    return salons.map((salon, index) => {
      const rawOffset = index - center;
      const half = Math.floor(salons.length / 2);
      const offset =
        rawOffset > half
          ? rawOffset - salons.length
          : rawOffset < -half
            ? rawOffset + salons.length
            : rawOffset;
      const abs = Math.abs(offset);
      const position: DisplayCard['position'] =
        abs === 0 ? 'center' : abs === 1 ? 'side' : abs === 2 ? 'side-far' : 'hidden';
      const gradient = SALON_GRADIENTS[index % SALON_GRADIENTS.length];
      return { salon, index, offset, position, gradient };
    });
  });

  private readonly router = inject(Router);

  constructor() {
    // When the center changes, un-flip all cards. Avoids the awkward
    // visual of a card sliding sideways while showing the map.
    // We track the previous value so the initial effect run is a no-op.
    let prevCenter: number | undefined = undefined;
    effect(
      () => {
        const current = this.centerIndex();
        if (prevCenter !== undefined && prevCenter !== current) {
          this.flippedSlugs.set(new Set());
        }
        prevCenter = current;
      },
      { allowSignalWrites: true },
    );
  }

  isFlipped(slug: string): boolean {
    return this.flippedSlugs().has(slug);
  }

  toggleFlip(slug: string): void {
    const next = new Set(this.flippedSlugs());
    if (next.has(slug)) {
      next.delete(slug);
    } else {
      next.add(slug);
    }
    this.flippedSlugs.set(next);
  }

  next(): void {
    const len = this.salons().length;
    if (len === 0) return;
    this.centerIndex.update((i) => (i + 1) % len);
  }

  prev(): void {
    const len = this.salons().length;
    if (len === 0) return;
    this.centerIndex.update((i) => (i - 1 + len) % len);
  }

  goTo(index: number): void {
    const len = this.salons().length;
    if (index < 0 || index >= len) return;
    this.centerIndex.set(index);
  }

  onCardClick(card: DisplayCard, event: MouseEvent): void {
    if (card.position === 'hidden') return;
    if (card.position !== 'center') {
      event.preventDefault();
      event.stopPropagation();
      this.goTo(card.index);
      return;
    }
    if (this.isFlipped(card.salon.slug)) {
      // Don't navigate when the card is flipped — let the user dismiss the map first.
      return;
    }
    this.router.navigate(['/salon', card.salon.slug]);
  }

  onFlipClick(card: DisplayCard, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.toggleFlip(card.salon.slug);
  }
}
