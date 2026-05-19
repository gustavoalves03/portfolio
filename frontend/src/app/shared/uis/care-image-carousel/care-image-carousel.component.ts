import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export interface CarouselImage {
  url: string;
  name?: string;
}

@Component({
  selector: 'app-care-image-carousel',
  standalone: true,
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './care-image-carousel.component.html',
  styleUrl: './care-image-carousel.component.scss',
})
export class CareImageCarouselComponent {
  readonly images = input.required<CarouselImage[]>();
  readonly alt = input<string>('');

  private readonly currentIndex = signal(0);

  protected readonly safeIndex = computed(() => {
    const len = this.images().length;
    if (len === 0) return 0;
    return this.currentIndex() % len;
  });
  protected readonly hasMultiple = computed(() => this.images().length > 1);

  private touchStartX = 0;

  protected next(event: Event): void {
    event.stopPropagation();
    const len = this.images().length;
    if (len === 0) return;
    this.currentIndex.set((this.safeIndex() + 1) % len);
  }

  protected previous(event: Event): void {
    event.stopPropagation();
    const len = this.images().length;
    if (len === 0) return;
    this.currentIndex.set(this.safeIndex() === 0 ? len - 1 : this.safeIndex() - 1);
  }

  protected onDotClick(index: number, event: Event): void {
    event.stopPropagation();
    this.currentIndex.set(index);
  }

  protected onTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0].clientX;
  }

  protected onTouchEnd(event: TouchEvent): void {
    const diff = this.touchStartX - event.changedTouches[0].clientX;
    if (Math.abs(diff) < 50) return;
    const synthetic = new Event('swipe');
    if (diff > 0) this.next(synthetic);
    else this.previous(synthetic);
  }
}
