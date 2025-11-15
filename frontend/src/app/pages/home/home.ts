import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { CaresStore } from '../../features/cares/store/cares.store';

@Component({
  selector: 'app-home',
  imports: [MatCardModule, CurrencyPipe, MatIconModule, MatButtonModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  providers: [CaresStore]
})
export class Home {
  readonly caresStore = inject(CaresStore);

  // Track current image index for each care
  currentImageIndex = new Map<number, number>();

  // Get current image index for a care
  getCurrentImageIndex(careId: number): number {
    return this.currentImageIndex.get(careId) ?? 0;
  }

  // Navigate to next image
  nextImage(careId: number, totalImages: number, event: Event): void {
    event.stopPropagation();
    const currentIndex = this.getCurrentImageIndex(careId);
    const newIndex = (currentIndex + 1) % totalImages;
    this.currentImageIndex.set(careId, newIndex);
  }

  // Navigate to previous image
  previousImage(careId: number, totalImages: number, event: Event): void {
    event.stopPropagation();
    const currentIndex = this.getCurrentImageIndex(careId);
    const newIndex = currentIndex === 0 ? totalImages - 1 : currentIndex - 1;
    this.currentImageIndex.set(careId, newIndex);
  }

  // Touch event handling for swipe
  private touchStartX = 0;
  private touchEndX = 0;

  onTouchStart(event: TouchEvent): void {
    this.touchStartX = event.touches[0].clientX;
  }

  onTouchEnd(event: TouchEvent, careId: number, totalImages: number): void {
    this.touchEndX = event.changedTouches[0].clientX;
    this.handleSwipe(careId, totalImages);
  }

  private handleSwipe(careId: number, totalImages: number): void {
    const swipeThreshold = 50; // Minimum swipe distance in pixels
    const diff = this.touchStartX - this.touchEndX;

    if (Math.abs(diff) > swipeThreshold) {
      if (diff > 0) {
        // Swipe left - next image
        const currentIndex = this.getCurrentImageIndex(careId);
        const newIndex = (currentIndex + 1) % totalImages;
        this.currentImageIndex.set(careId, newIndex);
      } else {
        // Swipe right - previous image
        const currentIndex = this.getCurrentImageIndex(careId);
        const newIndex = currentIndex === 0 ? totalImages - 1 : currentIndex - 1;
        this.currentImageIndex.set(careId, newIndex);
      }
    }
  }
}
