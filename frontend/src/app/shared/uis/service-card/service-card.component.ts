import { Component, computed, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { CurrencyPipe } from '@angular/common';
import { Care } from '../../../features/cares/models/cares.model';

@Component({
  selector: 'app-service-card',
  imports: [MatIconModule, CurrencyPipe],
  templateUrl: './service-card.component.html',
  styleUrl: './service-card.component.scss'
})
export class ServiceCardComponent {
  // Inputs
  care = input.required<Care>();

  // Outputs
  bookingRequested = output<number>();

  // Local state
  currentImageIndex = signal(0);
  isFlipped = signal(false);
  readonly images = computed(() => this.care().images ?? []);
  readonly hasImages = computed(() => this.images().length > 0);
  readonly hasMultipleImages = computed(() => this.images().length > 1);

  // Touch handling for swipe
  private touchStartX = 0;
  private touchEndX = 0;

  getCurrentImageIndex(): number {
    return this.currentImageIndex();
  }

  nextImage(totalImages: number, event: Event): void {
    event.stopPropagation();
    const currentIndex = this.currentImageIndex();
    const newIndex = (currentIndex + 1) % totalImages;
    this.currentImageIndex.set(newIndex);
  }

  previousImage(totalImages: number, event: Event): void {
    event.stopPropagation();
    const currentIndex = this.currentImageIndex();
    const newIndex = currentIndex === 0 ? totalImages - 1 : currentIndex - 1;
    this.currentImageIndex.set(newIndex);
  }

  onTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0].clientX;
  }

  onTouchEnd(event: TouchEvent, totalImages: number): void {
    this.touchEndX = event.changedTouches[0].clientX;
    this.handleSwipe(totalImages);
  }

  private handleSwipe(totalImages: number): void {
    if (totalImages <= 0) {
      return;
    }
    const swipeThreshold = 50;
    const diff = this.touchStartX - this.touchEndX;

    if (Math.abs(diff) > swipeThreshold) {
      if (diff > 0) {
        const currentIndex = this.currentImageIndex();
        const newIndex = (currentIndex + 1) % totalImages;
        this.currentImageIndex.set(newIndex);
      } else {
        const currentIndex = this.currentImageIndex();
        const newIndex = currentIndex === 0 ? totalImages - 1 : currentIndex - 1;
        this.currentImageIndex.set(newIndex);
      }
    }
  }

  toggleBooking(): void {
    this.isFlipped.update(v => !v);
    if (this.isFlipped()) {
      this.bookingRequested.emit(this.care().id);
    }
  }
}
