import { Component, input, output, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { DragDropModule, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { FormsModule } from '@angular/forms';
import { CareImage } from '../../../features/cares/models/cares.model';

const MAX_IMAGES = 5;
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const ACCEPTED_FORMATS = ['image/png', 'image/jpeg', 'image/jpg'];

@Component({
  selector: 'image-manager',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatInputModule,
    MatFormFieldModule,
    DragDropModule,
    FormsModule
  ],
  templateUrl: './image-manager.component.html',
  styleUrl: './image-manager.component.scss'
})
export class ImageManager {
  images = input<CareImage[]>([]);
  imagesChange = output<CareImage[]>();
  readonly = input<boolean>(false);

  localImages = signal<CareImage[]>([]);
  errorMessage = signal<string | null>(null);

  readonly MAX_IMAGES = MAX_IMAGES;

  constructor() {
    effect(() => {
      this.localImages.set([...this.images()]);
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;

    const files = Array.from(input.files);
    this.errorMessage.set(null);

    // Validation: max images
    const currentCount = this.localImages().length;
    const remainingSlots = MAX_IMAGES - currentCount;

    if (remainingSlots <= 0) {
      this.errorMessage.set(`Vous ne pouvez ajouter que ${MAX_IMAGES} images maximum`);
      input.value = '';
      return;
    }

    const filesToAdd = files.slice(0, remainingSlots);

    if (files.length > remainingSlots) {
      this.errorMessage.set(`Seules ${remainingSlots} image(s) ont été ajoutées (limite: ${MAX_IMAGES})`);
    }

    filesToAdd.forEach(file => {
      // Validation: format
      if (!ACCEPTED_FORMATS.includes(file.type)) {
        this.errorMessage.set(`Format non accepté: ${file.name}. Utilisez PNG ou JPG.`);
        return;
      }

      // Validation: size
      if (file.size > MAX_FILE_SIZE) {
        this.errorMessage.set(`${file.name} dépasse la taille maximale de 5MB`);
        return;
      }

      this.addImage(file);
    });

    input.value = '';
  }

  private addImage(file: File): void {
    const reader = new FileReader();
    reader.onload = (e) => {
      const url = e.target?.result as string;
      const newImage: CareImage = {
        id: crypto.randomUUID(),
        url,
        name: file.name.replace(/\.[^/.]+$/, ''), // Remove extension
        order: this.localImages().length,
        file
      };

      const updated = [...this.localImages(), newImage];
      this.localImages.set(updated);
      this.emitChanges(updated);
    };
    reader.readAsDataURL(file);
  }

  onDrop(event: CdkDragDrop<CareImage[]>): void {
    const images = [...this.localImages()];
    moveItemInArray(images, event.previousIndex, event.currentIndex);

    // Update order property
    images.forEach((img, index) => {
      img.order = index;
    });

    this.localImages.set(images);
    this.emitChanges(images);
  }

  onNameChange(image: CareImage, newName: string): void {
    const images = this.localImages().map(img =>
      img.id === image.id ? { ...img, name: newName } : img
    );
    this.localImages.set(images);
    this.emitChanges(images);
  }

  onDelete(imageId: string | undefined): void {
    if (!imageId) return;

    const images = this.localImages()
      .filter(img => img.id !== imageId)
      .map((img, index) => ({ ...img, order: index })); // Reorder

    this.localImages.set(images);
    this.emitChanges(images);
  }

  private emitChanges(images: CareImage[]): void {
    this.imagesChange.emit(images);
  }

  get canAddMore(): boolean {
    return this.localImages().length < MAX_IMAGES;
  }

  get remainingSlots(): number {
    return MAX_IMAGES - this.localImages().length;
  }
}
