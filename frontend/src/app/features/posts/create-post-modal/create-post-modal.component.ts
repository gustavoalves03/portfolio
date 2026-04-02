import { Component, inject, signal, computed } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { PostsService } from '../posts.service';
import { PostResponse, PostType } from '../posts.model';

interface ImagePreview {
  file: File;
  url: string;
}

@Component({
  selector: 'app-create-post-modal',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    FormsModule,
    TranslocoPipe,
  ],
  template: `
    <div class="create-post-modal">
      <div class="modal-header">
        <h2>{{ 'posts.createTitle' | transloco }}</h2>
        <button mat-icon-button (click)="dialogRef.close()">
          <mat-icon>close</mat-icon>
        </button>
      </div>

      <!-- Type selector -->
      <div class="type-selector">
        <button
          class="type-card"
          [class.active]="postType() === 'BEFORE_AFTER'"
          (click)="selectType('BEFORE_AFTER')"
        >
          <mat-icon>compare</mat-icon>
          <span>{{ 'posts.typeBeforeAfter' | transloco }}</span>
        </button>
        <button
          class="type-card"
          [class.active]="postType() === 'PHOTO'"
          (click)="selectType('PHOTO')"
        >
          <mat-icon>photo</mat-icon>
          <span>{{ 'posts.typePhoto' | transloco }}</span>
        </button>
        <button
          class="type-card"
          [class.active]="postType() === 'CAROUSEL'"
          (click)="selectType('CAROUSEL')"
        >
          <mat-icon>collections</mat-icon>
          <span>{{ 'posts.typeCarousel' | transloco }}</span>
        </button>
      </div>

      <!-- Upload zones -->
      <div class="upload-section">
        @switch (postType()) {
          @case ('BEFORE_AFTER') {
            <div class="upload-pair">
              <div
                class="upload-zone"
                (click)="beforeInput.click()"
                (dragover)="onDragOver($event)"
                (drop)="onDropBefore($event)"
              >
                @if (beforeImage()) {
                  <img [src]="beforeImage()!.url" class="preview-img" alt="Before" />
                  <button class="remove-img" (click)="removeBefore($event)">
                    <mat-icon>close</mat-icon>
                  </button>
                } @else {
                  <mat-icon>add_photo_alternate</mat-icon>
                  <span>{{ 'posts.uploadBefore' | transloco }}</span>
                }
                <input
                  #beforeInput
                  type="file"
                  accept="image/*"
                  hidden
                  (change)="onBeforeSelected($event)"
                />
              </div>
              <div
                class="upload-zone"
                (click)="afterInput.click()"
                (dragover)="onDragOver($event)"
                (drop)="onDropAfter($event)"
              >
                @if (afterImage()) {
                  <img [src]="afterImage()!.url" class="preview-img" alt="After" />
                  <button class="remove-img" (click)="removeAfter($event)">
                    <mat-icon>close</mat-icon>
                  </button>
                } @else {
                  <mat-icon>add_photo_alternate</mat-icon>
                  <span>{{ 'posts.uploadAfter' | transloco }}</span>
                }
                <input
                  #afterInput
                  type="file"
                  accept="image/*"
                  hidden
                  (change)="onAfterSelected($event)"
                />
              </div>
            </div>
          }
          @case ('PHOTO') {
            <div
              class="upload-zone upload-zone-single"
              (click)="photoInput.click()"
              (dragover)="onDragOver($event)"
              (drop)="onDropPhoto($event)"
            >
              @if (photoImage()) {
                <img [src]="photoImage()!.url" class="preview-img" alt="Photo" />
                <button class="remove-img" (click)="removePhoto($event)">
                  <mat-icon>close</mat-icon>
                </button>
              } @else {
                <mat-icon>add_photo_alternate</mat-icon>
                <span>{{ 'posts.uploadPhoto' | transloco }}</span>
              }
              <input
                #photoInput
                type="file"
                accept="image/*"
                hidden
                (change)="onPhotoSelected($event)"
              />
            </div>
          }
          @case ('CAROUSEL') {
            <div class="carousel-uploads">
              @for (img of carouselImages(); track img.url; let i = $index) {
                <div class="carousel-thumb">
                  <img [src]="img.url" alt="Slide" />
                  <button class="remove-img" (click)="removeCarouselImage(i)">
                    <mat-icon>close</mat-icon>
                  </button>
                </div>
              }
              @if (carouselImages().length < 10) {
                <div class="upload-zone carousel-add" (click)="carouselInput.click()">
                  <mat-icon>add</mat-icon>
                  <span>{{ 'posts.uploadMore' | transloco }}</span>
                  <input
                    #carouselInput
                    type="file"
                    accept="image/*"
                    multiple
                    hidden
                    (change)="onCarouselSelected($event)"
                  />
                </div>
              }
            </div>
          }
        }
      </div>

      <!-- Caption -->
      <div class="caption-section">
        <label>{{ 'posts.caption' | transloco }}</label>
        <textarea
          [(ngModel)]="caption"
          [placeholder]="'posts.captionPlaceholder' | transloco"
          rows="3"
          maxlength="500"
        ></textarea>
      </div>

      <!-- Publish button -->
      <div class="modal-footer">
        @if (submitting()) {
          <mat-spinner diameter="28"></mat-spinner>
        } @else {
          <button
            class="publish-action-btn"
            [disabled]="!canPublish()"
            (click)="publish()"
          >
            {{ 'posts.publishBtn' | transloco }}
          </button>
        }
      </div>
    </div>
  `,
  styles: `
    .create-post-modal {
      padding: 20px;
    }

    .modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;

      h2 {
        font-size: 18px;
        font-weight: 600;
        color: #333;
        margin: 0;
      }
    }

    .type-selector {
      display: flex;
      gap: 8px;
      margin-bottom: 16px;
    }

    .type-card {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      padding: 12px 8px;
      border: 2px solid #eee;
      border-radius: 12px;
      background: #fff;
      cursor: pointer;
      transition: all 150ms ease;

      mat-icon {
        font-size: 24px;
        width: 24px;
        height: 24px;
        color: #999;
      }

      span {
        font-size: 11px;
        font-weight: 500;
        color: #666;
        text-align: center;
      }

      &.active {
        border-color: #c06;
        background: #fdf2f6;

        mat-icon {
          color: #c06;
        }

        span {
          color: #c06;
        }
      }

      &:hover:not(.active) {
        border-color: #ddd;
        background: #fafafa;
      }
    }

    .upload-section {
      margin-bottom: 16px;
    }

    .upload-pair {
      display: flex;
      gap: 8px;

      .upload-zone {
        flex: 1;
      }
    }

    .upload-zone {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      min-height: 120px;
      border: 2px dashed #ddd;
      border-radius: 12px;
      background: #fafafa;
      cursor: pointer;
      position: relative;
      overflow: hidden;
      transition: border-color 150ms ease;

      &:hover {
        border-color: #c06;
      }

      mat-icon {
        font-size: 28px;
        width: 28px;
        height: 28px;
        color: #bbb;
      }

      span {
        font-size: 12px;
        color: #999;
        font-weight: 500;
      }
    }

    .upload-zone-single {
      min-height: 180px;
    }

    .preview-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      position: absolute;
      inset: 0;
    }

    .remove-img {
      position: absolute;
      top: 6px;
      right: 6px;
      width: 24px;
      height: 24px;
      border-radius: 50%;
      border: none;
      background: rgba(0, 0, 0, 0.5);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      z-index: 2;

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        color: #fff;
      }
    }

    .carousel-uploads {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }

    .carousel-thumb {
      width: 80px;
      height: 80px;
      border-radius: 10px;
      overflow: hidden;
      position: relative;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .carousel-add {
      width: 80px;
      height: 80px;
      min-height: unset;

      mat-icon {
        font-size: 22px;
        width: 22px;
        height: 22px;
      }

      span {
        font-size: 10px;
      }
    }

    .caption-section {
      margin-bottom: 16px;

      label {
        display: block;
        font-size: 13px;
        font-weight: 500;
        color: #555;
        margin-bottom: 6px;
      }

      textarea {
        width: 100%;
        border: 1px solid #ddd;
        border-radius: 10px;
        padding: 10px 12px;
        font-size: 13px;
        font-family: Roboto, sans-serif;
        resize: vertical;
        outline: none;
        transition: border-color 150ms ease;
        box-sizing: border-box;

        &:focus {
          border-color: #c06;
        }
      }
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
    }

    .publish-action-btn {
      padding: 10px 28px;
      border: none;
      border-radius: 20px;
      background: linear-gradient(135deg, #a8385d, #c06);
      color: #fff;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: opacity 150ms ease;

      &:hover:not(:disabled) {
        opacity: 0.9;
      }

      &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
      }
    }
  `,
})
export class CreatePostModalComponent {
  readonly dialogRef = inject(MatDialogRef<CreatePostModalComponent>);
  private readonly postsService = inject(PostsService);

  readonly postType = signal<PostType>('BEFORE_AFTER');
  readonly beforeImage = signal<ImagePreview | null>(null);
  readonly afterImage = signal<ImagePreview | null>(null);
  readonly photoImage = signal<ImagePreview | null>(null);
  readonly carouselImages = signal<ImagePreview[]>([]);
  readonly submitting = signal(false);
  caption = '';

  readonly canPublish = computed(() => {
    if (this.submitting()) return false;
    switch (this.postType()) {
      case 'BEFORE_AFTER':
        return !!this.beforeImage() && !!this.afterImage();
      case 'PHOTO':
        return !!this.photoImage();
      case 'CAROUSEL':
        return this.carouselImages().length >= 2;
    }
  });

  selectType(type: PostType): void {
    this.postType.set(type);
  }

  // Before/After
  onBeforeSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.beforeImage.set({ file, url: URL.createObjectURL(file) });
  }

  onAfterSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.afterImage.set({ file, url: URL.createObjectURL(file) });
  }

  removeBefore(e: Event): void {
    e.stopPropagation();
    this.revokeUrl(this.beforeImage()?.url);
    this.beforeImage.set(null);
  }

  removeAfter(e: Event): void {
    e.stopPropagation();
    this.revokeUrl(this.afterImage()?.url);
    this.afterImage.set(null);
  }

  onDropBefore(e: DragEvent): void {
    e.preventDefault();
    const file = e.dataTransfer?.files?.[0];
    if (file?.type.startsWith('image/')) {
      this.beforeImage.set({ file, url: URL.createObjectURL(file) });
    }
  }

  onDropAfter(e: DragEvent): void {
    e.preventDefault();
    const file = e.dataTransfer?.files?.[0];
    if (file?.type.startsWith('image/')) {
      this.afterImage.set({ file, url: URL.createObjectURL(file) });
    }
  }

  // Photo
  onPhotoSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.photoImage.set({ file, url: URL.createObjectURL(file) });
  }

  removePhoto(e: Event): void {
    e.stopPropagation();
    this.revokeUrl(this.photoImage()?.url);
    this.photoImage.set(null);
  }

  onDropPhoto(e: DragEvent): void {
    e.preventDefault();
    const file = e.dataTransfer?.files?.[0];
    if (file?.type.startsWith('image/')) {
      this.photoImage.set({ file, url: URL.createObjectURL(file) });
    }
  }

  // Carousel
  onCarouselSelected(event: Event): void {
    const files = (event.target as HTMLInputElement).files;
    if (!files) return;
    const newImages: ImagePreview[] = [];
    for (let i = 0; i < files.length; i++) {
      if (this.carouselImages().length + newImages.length >= 10) break;
      newImages.push({ file: files[i], url: URL.createObjectURL(files[i]) });
    }
    this.carouselImages.update((list) => [...list, ...newImages]);
  }

  removeCarouselImage(index: number): void {
    this.carouselImages.update((list) => {
      const removed = list[index];
      this.revokeUrl(removed?.url);
      return list.filter((_, i) => i !== index);
    });
  }

  onDragOver(e: DragEvent): void {
    e.preventDefault();
  }

  // Publish
  publish(): void {
    if (!this.canPublish()) return;
    this.submitting.set(true);

    const fd = new FormData();
    if (this.caption.trim()) {
      fd.append('caption', this.caption.trim());
    }

    let obs$;

    switch (this.postType()) {
      case 'BEFORE_AFTER':
        fd.append('beforeImage', this.beforeImage()!.file);
        fd.append('afterImage', this.afterImage()!.file);
        obs$ = this.postsService.createBeforeAfter(fd);
        break;
      case 'PHOTO':
        fd.append('image', this.photoImage()!.file);
        obs$ = this.postsService.createPhoto(fd);
        break;
      case 'CAROUSEL':
        for (const img of this.carouselImages()) {
          fd.append('images', img.file);
        }
        obs$ = this.postsService.createCarousel(fd);
        break;
    }

    obs$.subscribe({
      next: (created: PostResponse) => {
        this.submitting.set(false);
        this.dialogRef.close(created);
      },
      error: () => {
        this.submitting.set(false);
      },
    });
  }

  private revokeUrl(url: string | undefined): void {
    if (url) URL.revokeObjectURL(url);
  }
}
