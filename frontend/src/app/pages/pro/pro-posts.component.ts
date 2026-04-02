import { Component, computed, inject, OnInit, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PostsService } from '../../features/posts/posts.service';
import { PostResponse, PostType } from '../../features/posts/posts.model';
import { CaresService } from '../../features/cares/services/cares.service';

interface ImagePreview {
  file: File;
  url: string;
}

@Component({
  selector: 'app-pro-posts',
  standalone: true,
  imports: [
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    FormsModule,
    TranslocoPipe,
  ],
  templateUrl: './pro-posts.component.html',
  styleUrl: './pro-posts.component.scss',
})
export class ProPostsComponent implements OnInit {
  private readonly postsService = inject(PostsService);
  private readonly caresService = inject(CaresService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly platformId = inject(PLATFORM_ID);

  // Posts list
  readonly posts = signal<PostResponse[]>([]);
  readonly loading = signal(true);

  // Form state
  readonly showForm = signal(false);
  readonly selectedType = signal<PostType>('BEFORE_AFTER');
  caption = '';
  readonly selectedCareId = signal<number | null>(null);
  readonly publishing = signal(false);

  // Image previews
  readonly beforeImage = signal<ImagePreview | null>(null);
  readonly afterImage = signal<ImagePreview | null>(null);
  readonly photoImage = signal<ImagePreview | null>(null);
  readonly carouselImages = signal<ImagePreview[]>([]);

  // Available cares for care selector
  readonly cares = signal<{ id: number; name: string }[]>([]);

  readonly canPublish = computed(() => {
    if (this.publishing()) return false;
    switch (this.selectedType()) {
      case 'BEFORE_AFTER':
        return !!this.beforeImage() && !!this.afterImage();
      case 'PHOTO':
        return !!this.photoImage();
      case 'CAROUSEL':
        return this.carouselImages().length >= 2;
    }
  });

  ngOnInit(): void {
    this.loadPosts();
    this.loadCares();
  }

  toggleForm(): void {
    this.showForm.update((v) => !v);
  }

  selectType(type: PostType): void {
    this.selectedType.set(type);
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
    this.publishing.set(true);

    const fd = new FormData();
    if (this.caption.trim()) {
      fd.append('caption', this.caption.trim());
    }
    if (this.selectedCareId()) {
      fd.append('careId', String(this.selectedCareId()));
    }

    let obs$;

    switch (this.selectedType()) {
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
        this.publishing.set(false);
        this.posts.update((list) => [created, ...list]);
        this.resetForm();
        this.snackBar.open(
          this.transloco.translate('pro.posts.publishSuccess'),
          undefined,
          { duration: 3000 },
        );
      },
      error: () => {
        this.publishing.set(false);
      },
    });
  }

  // Delete
  onDelete(post: PostResponse): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const msg = this.transloco.translate('posts.deleteConfirm');
    if (confirm(msg)) {
      this.postsService.delete(post.id).subscribe({
        next: () => {
          this.posts.update((list) => list.filter((p) => p.id !== post.id));
          this.snackBar.open(
            this.transloco.translate('pro.posts.deleteSuccess'),
            undefined,
            { duration: 3000 },
          );
        },
      });
    }
  }

  getThumbnail(post: PostResponse): string {
    switch (post.type) {
      case 'BEFORE_AFTER':
        return post.afterImageUrl ?? post.beforeImageUrl ?? '';
      case 'PHOTO':
        return post.beforeImageUrl ?? (post.carouselImageUrls.length > 0 ? post.carouselImageUrls[0] : '');
      case 'CAROUSEL':
        return post.carouselImageUrls.length > 0 ? post.carouselImageUrls[0] : '';
    }
  }

  formatDate(isoString: string): string {
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString(this.transloco.getActiveLang(), {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      });
    } catch {
      return isoString;
    }
  }

  private loadPosts(): void {
    this.loading.set(true);
    this.postsService.listPro().subscribe({
      next: (posts) => {
        this.posts.set(posts);
        this.loading.set(false);
      },
      error: () => {
        this.posts.set([]);
        this.loading.set(false);
      },
    });
  }

  private loadCares(): void {
    this.caresService.listOrdered().subscribe({
      next: (cares) => {
        this.cares.set(
          cares
            .filter((c) => c.status === 'ACTIVE')
            .map((c) => ({ id: c.id, name: c.name })),
        );
      },
      error: () => {
        this.cares.set([]);
      },
    });
  }

  private resetForm(): void {
    this.showForm.set(false);
    this.selectedType.set('BEFORE_AFTER');
    this.caption = '';
    this.selectedCareId.set(null);
    this.revokeUrl(this.beforeImage()?.url);
    this.revokeUrl(this.afterImage()?.url);
    this.revokeUrl(this.photoImage()?.url);
    for (const img of this.carouselImages()) {
      this.revokeUrl(img.url);
    }
    this.beforeImage.set(null);
    this.afterImage.set(null);
    this.photoImage.set(null);
    this.carouselImages.set([]);
  }

  private revokeUrl(url: string | undefined): void {
    if (url) URL.revokeObjectURL(url);
  }
}
