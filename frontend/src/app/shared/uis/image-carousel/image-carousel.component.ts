import { Component, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { CareImage } from '../../../features/cares/models/cares.model';

@Component({
  selector: 'image-carousel',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  templateUrl: './image-carousel.component.html',
  styleUrl: './image-carousel.component.scss'
})
export class ImageCarousel {
  images = input<CareImage[]>([]);
  currentIndex = signal(0);

  get hasImages(): boolean {
    return this.images().length > 0;
  }

  get currentImage(): CareImage | null {
    if (!this.hasImages) return null;
    return this.images()[this.currentIndex()];
  }

  get totalImages(): number {
    return this.images().length;
  }

  get hasPrevious(): boolean {
    return this.hasImages && this.totalImages > 1;
  }

  get hasNext(): boolean {
    return this.hasImages && this.totalImages > 1;
  }

  previousImage(): void {
    if (!this.hasPrevious) return;
    const newIndex = this.currentIndex() - 1;
    this.currentIndex.set(newIndex < 0 ? this.totalImages - 1 : newIndex);
  }

  nextImage(): void {
    if (!this.hasNext) return;
    const newIndex = this.currentIndex() + 1;
    this.currentIndex.set(newIndex >= this.totalImages ? 0 : newIndex);
  }

  goToImage(index: number): void {
    this.currentIndex.set(index);
  }
}
