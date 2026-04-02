import {
  Component,
  inject,
  input,
  output,
  signal,
  effect,
  ElementRef,
  viewChild,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PostsService } from '../posts.service';
import { PostResponse } from '../posts.model';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';
import { CreatePostModalComponent } from '../create-post-modal/create-post-modal.component';

@Component({
  selector: 'app-salon-posts-viewer',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatProgressSpinnerModule, TranslocoPipe],
  template: `
    <div class="posts-viewer">
      @if (isPro()) {
        <button class="publish-btn" (click)="openCreateModal()">
          <mat-icon>add_circle</mat-icon>
          <span>{{ 'posts.publish' | transloco }}</span>
        </button>
      }

      @if (loading()) {
        <div class="loading">
          <mat-spinner diameter="36"></mat-spinner>
        </div>
      } @else if (posts().length === 0) {
        <div class="empty">
          <mat-icon class="empty-icon">photo_library</mat-icon>
          <p>{{ 'posts.empty' | transloco }}</p>
        </div>
      } @else {
        <div class="snap-viewport">
          <div class="snap-scroll" #snapScroll (scroll)="onScroll()">
            @for (post of posts(); track post.id; let i = $index) {
              <div class="snap-item">
                <div class="snap-media">
                  @switch (post.type) {
                    @case ('BEFORE_AFTER') {
                      <div
                        class="ba-container"
                        (mousedown)="onBaDragStart($event, post.id)"
                        (mousemove)="onBaDragMove($event, post.id)"
                        (mouseup)="onBaDragEnd(post.id)"
                        (mouseleave)="onBaDragEnd(post.id)"
                        (touchstart)="onBaTouchStart($event, post.id)"
                        (touchmove)="onBaTouchMove($event, post.id)"
                        (touchend)="onBaDragEnd(post.id)"
                      >
                        @if (post.afterImageUrl) {
                          <img [src]="post.afterImageUrl" class="ba-img ba-after" alt="After" />
                        }
                        @if (post.beforeImageUrl) {
                          <img
                            [src]="post.beforeImageUrl"
                            class="ba-img ba-before"
                            [style.clip-path]="
                              'inset(0 ' + (100 - getBaSplit(post.id)) + '% 0 0)'
                            "
                            alt="Before"
                          />
                        }
                        <div
                          class="ba-handle"
                          [style.left.%]="getBaSplit(post.id)"
                        >
                          <div class="ba-handle-line"></div>
                          <div class="ba-handle-circle">
                            <mat-icon>swap_horiz</mat-icon>
                          </div>
                          <div class="ba-handle-line"></div>
                        </div>
                        <span class="ba-label ba-label-before">{{
                          'posts.before' | transloco
                        }}</span>
                        <span class="ba-label ba-label-after">{{
                          'posts.after' | transloco
                        }}</span>
                      </div>
                    }
                    @case ('PHOTO') {
                      @if (post.beforeImageUrl) {
                        <img [src]="post.beforeImageUrl" class="photo-img" [alt]="post.caption || 'Photo'" />
                      } @else if (post.carouselImageUrls.length > 0) {
                        <img [src]="post.carouselImageUrls[0]" class="photo-img" [alt]="post.caption || 'Photo'" />
                      }
                    }
                    @case ('CAROUSEL') {
                      <div class="carousel-container">
                        <div
                          class="carousel-track"
                          [style.transform]="
                            'translateX(-' + getCarouselIndex(post.id) * 100 + '%)'
                          "
                        >
                          @for (
                            url of post.carouselImageUrls;
                            track url;
                            let ci = $index
                          ) {
                            <img [src]="url" class="carousel-slide" [alt]="'Slide ' + (ci + 1)" />
                          }
                        </div>
                        <div
                          class="carousel-tap carousel-tap-left"
                          (click)="carouselPrev(post.id, post.carouselImageUrls.length)"
                        ></div>
                        <div
                          class="carousel-tap carousel-tap-right"
                          (click)="carouselNext(post.id, post.carouselImageUrls.length)"
                        ></div>
                        @if (post.carouselImageUrls.length > 1) {
                          <div class="carousel-dots">
                            @for (
                              url of post.carouselImageUrls;
                              track url;
                              let ci = $index
                            ) {
                              <div
                                class="carousel-dot"
                                [class.active]="getCarouselIndex(post.id) === ci"
                              ></div>
                            }
                          </div>
                        }
                      </div>
                    }
                  }

                  <div class="snap-gradient"></div>

                  <div class="snap-info">
                    <div class="post-type-pill">
                      @switch (post.type) {
                        @case ('BEFORE_AFTER') {
                          <mat-icon>compare</mat-icon>
                          <span>{{ 'posts.typeBeforeAfter' | transloco }}</span>
                        }
                        @case ('PHOTO') {
                          <mat-icon>photo</mat-icon>
                          <span>{{ 'posts.typePhoto' | transloco }}</span>
                        }
                        @case ('CAROUSEL') {
                          <mat-icon>collections</mat-icon>
                          <span>{{ 'posts.typeCarousel' | transloco }}</span>
                        }
                      }
                    </div>
                    @if (post.caption) {
                      <div class="post-caption">{{ post.caption }}</div>
                    }
                    @if (post.careName) {
                      <div class="post-tags">
                        <span class="post-tag">{{ post.careName }}</span>
                      </div>
                    }
                    <div class="post-meta">{{ formatDate(post.createdAt) }}</div>
                  </div>

                  <div class="snap-actions">
                    <button class="sa-btn" (click)="onShare(post)">
                      <mat-icon>share</mat-icon>
                      <span>{{ 'posts.share' | transloco }}</span>
                    </button>
                    @if (post.careId) {
                      <button class="sa-btn book-btn" (click)="onBook(post)">
                        <mat-icon>calendar_today</mat-icon>
                        <span>{{ 'posts.book' | transloco }}</span>
                      </button>
                    }
                    @if (isPro()) {
                      <button class="sa-btn delete-btn" (click)="onDelete(post)">
                        <mat-icon>delete</mat-icon>
                      </button>
                    }
                  </div>
                </div>
              </div>
            }
          </div>

          @if (posts().length > 1) {
            <div class="snap-progress">
              @for (post of posts(); track post.id; let i = $index) {
                <div class="sp-dot" [class.active]="currentIndex() === i"></div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .posts-viewer {
      width: 100%;
    }

    .publish-btn {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 0 auto 12px;
      padding: 8px 20px;
      border: none;
      border-radius: 20px;
      background: linear-gradient(135deg, #a8385d, #c06);
      color: #fff;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: opacity 150ms ease;

      &:hover {
        opacity: 0.9;
      }

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }
    }

    .loading {
      display: flex;
      justify-content: center;
      padding: 48px 0;
    }

    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 48px 0;
      color: #999;

      .empty-icon {
        font-size: 40px;
        width: 40px;
        height: 40px;
        color: #ccc;
      }

      p {
        font-size: 14px;
        margin: 0;
      }
    }

    .snap-viewport {
      position: relative;
      height: 480px;
      border-radius: 16px;
      overflow: hidden;
      background: #000;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
    }

    @media (min-height: 800px) {
      .snap-viewport {
        height: 560px;
      }
    }

    .snap-scroll {
      height: 100%;
      overflow-y: scroll;
      scroll-snap-type: y mandatory;
      overscroll-behavior: contain;
      -webkit-overflow-scrolling: touch;
      scrollbar-width: none;

      &::-webkit-scrollbar {
        display: none;
      }
    }

    .snap-item {
      scroll-snap-align: start;
      height: 100%;
      width: 100%;
      position: relative;
    }

    .snap-media {
      position: relative;
      width: 100%;
      height: 100%;
      overflow: hidden;
    }

    /* Before / After */
    .ba-container {
      position: relative;
      width: 100%;
      height: 100%;
      cursor: ew-resize;
      user-select: none;
      touch-action: none;
    }

    .ba-img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .ba-before {
      z-index: 2;
    }

    .ba-after {
      z-index: 1;
    }

    .ba-handle {
      position: absolute;
      top: 0;
      bottom: 0;
      z-index: 3;
      display: flex;
      flex-direction: column;
      align-items: center;
      transform: translateX(-50%);
      pointer-events: none;
    }

    .ba-handle-line {
      flex: 1;
      width: 2px;
      background: #fff;
      box-shadow: 0 0 4px rgba(0, 0, 0, 0.4);
    }

    .ba-handle-circle {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);

      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
        color: #333;
      }
    }

    .ba-label {
      position: absolute;
      top: 12px;
      z-index: 4;
      padding: 3px 10px;
      border-radius: 12px;
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      color: #fff;
      background: rgba(0, 0, 0, 0.45);
      pointer-events: none;
    }

    .ba-label-before {
      left: 12px;
    }

    .ba-label-after {
      right: 12px;
    }

    /* Photo */
    .photo-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    /* Carousel */
    .carousel-container {
      position: relative;
      width: 100%;
      height: 100%;
      overflow: hidden;
    }

    .carousel-track {
      display: flex;
      height: 100%;
      transition: transform 250ms ease;
    }

    .carousel-slide {
      min-width: 100%;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .carousel-tap {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 50%;
      z-index: 2;
      cursor: pointer;
    }

    .carousel-tap-left {
      left: 0;
    }

    .carousel-tap-right {
      right: 0;
    }

    .carousel-dots {
      position: absolute;
      top: 10px;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      gap: 5px;
      z-index: 3;
    }

    .carousel-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.5);
      transition: background 150ms ease;

      &.active {
        background: #fff;
      }
    }

    /* Gradient overlay */
    .snap-gradient {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 50%;
      z-index: 5;
      background: linear-gradient(to top, rgba(0, 0, 0, 0.7) 0%, transparent 100%);
      pointer-events: none;
    }

    /* Info overlay */
    .snap-info {
      position: absolute;
      bottom: 16px;
      left: 16px;
      right: 72px;
      z-index: 6;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .post-type-pill {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 3px 10px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.15);
      backdrop-filter: blur(6px);
      color: #fff;
      font-size: 11px;
      font-weight: 500;
      width: fit-content;

      mat-icon {
        font-size: 14px;
        width: 14px;
        height: 14px;
      }
    }

    .post-caption {
      font-size: 13px;
      color: #fff;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .post-tags {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }

    .post-tag {
      padding: 2px 8px;
      border-radius: 10px;
      background: rgba(192, 0, 102, 0.6);
      color: #fff;
      font-size: 11px;
      font-weight: 500;
    }

    .post-meta {
      font-size: 11px;
      color: rgba(255, 255, 255, 0.6);
    }

    /* Action buttons */
    .snap-actions {
      position: absolute;
      right: 12px;
      bottom: 16px;
      z-index: 6;
      display: flex;
      flex-direction: column;
      gap: 14px;
      align-items: center;
    }

    .sa-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      background: none;
      border: none;
      color: #fff;
      cursor: pointer;
      padding: 0;
      transition: transform 150ms ease;

      &:active {
        transform: scale(0.9);
      }

      mat-icon {
        font-size: 24px;
        width: 24px;
        height: 24px;
        filter: drop-shadow(0 1px 3px rgba(0, 0, 0, 0.5));
      }

      span {
        font-size: 10px;
        font-weight: 500;
        text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
      }
    }

    .book-btn mat-icon {
      color: #f8b4d0;
    }

    .delete-btn mat-icon {
      color: #ff6b6b;
    }

    /* Progress dots */
    .snap-progress {
      position: absolute;
      right: 8px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      flex-direction: column;
      gap: 6px;
      z-index: 6;
    }

    .sp-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.35);
      transition: all 200ms ease;

      &.active {
        background: #fff;
        transform: scale(1.3);
      }
    }
  `,
})
export class SalonPostsViewerComponent {
  private readonly postsService = inject(PostsService);
  private readonly authService = inject(AuthService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly dialog = inject(MatDialog);
  private readonly translocoService = inject(TranslocoService);

  readonly slug = input.required<string>();
  readonly bookCare = output<number>();

  readonly posts = signal<PostResponse[]>([]);
  readonly loading = signal(true);
  readonly currentIndex = signal(0);

  private carouselIndices: Record<number, number> = {};
  private baSplits: Record<number, number> = {};
  private baDragging: Record<number, boolean> = {};

  readonly snapScroll = viewChild<ElementRef<HTMLElement>>('snapScroll');

  readonly isPro = signal(false);

  constructor() {
    effect(() => {
      const slugVal = this.slug();
      if (slugVal) {
        this.loadPosts(slugVal);
      }
    });

    const user = this.authService.user();
    this.isPro.set(user?.role === Role.PRO || user?.role === Role.ADMIN);
  }

  private loadPosts(slug: string): void {
    this.loading.set(true);
    this.postsService.listPublic(slug).subscribe({
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

  onScroll(): void {
    const el = this.snapScroll()?.nativeElement;
    if (!el) return;
    const idx = Math.round(el.scrollTop / el.clientHeight);
    this.currentIndex.set(idx);
  }

  // Before/After slider
  getBaSplit(postId: number): number {
    return this.baSplits[postId] ?? 50;
  }

  onBaDragStart(e: MouseEvent, postId: number): void {
    this.baDragging[postId] = true;
    this.updateBaSplit(e.clientX, postId, e.currentTarget as HTMLElement);
  }

  onBaDragMove(e: MouseEvent, postId: number): void {
    if (!this.baDragging[postId]) return;
    this.updateBaSplit(e.clientX, postId, e.currentTarget as HTMLElement);
  }

  onBaDragEnd(postId: number): void {
    this.baDragging[postId] = false;
  }

  onBaTouchStart(e: TouchEvent, postId: number): void {
    this.baDragging[postId] = true;
    if (e.touches.length > 0) {
      this.updateBaSplit(e.touches[0].clientX, postId, e.currentTarget as HTMLElement);
    }
  }

  onBaTouchMove(e: TouchEvent, postId: number): void {
    if (!this.baDragging[postId]) return;
    if (e.touches.length > 0) {
      this.updateBaSplit(e.touches[0].clientX, postId, e.currentTarget as HTMLElement);
    }
  }

  private updateBaSplit(clientX: number, postId: number, container: HTMLElement): void {
    const rect = container.getBoundingClientRect();
    const pct = ((clientX - rect.left) / rect.width) * 100;
    this.baSplits[postId] = Math.max(5, Math.min(95, pct));
  }

  // Carousel
  getCarouselIndex(postId: number): number {
    return this.carouselIndices[postId] ?? 0;
  }

  carouselPrev(postId: number, total: number): void {
    const cur = this.getCarouselIndex(postId);
    this.carouselIndices[postId] = cur > 0 ? cur - 1 : total - 1;
  }

  carouselNext(postId: number, total: number): void {
    const cur = this.getCarouselIndex(postId);
    this.carouselIndices[postId] = cur < total - 1 ? cur + 1 : 0;
  }

  // Actions
  onShare(post: PostResponse): void {
    if (!isPlatformBrowser(this.platformId)) return;

    const url = window.location.href;
    if (navigator.share) {
      navigator.share({ title: post.caption ?? 'Pretty Face', url }).catch(() => {});
    } else {
      navigator.clipboard.writeText(url).then(() => {
        // Could show a toast - for now silent copy
      });
    }
  }

  onBook(post: PostResponse): void {
    if (post.careId) {
      this.bookCare.emit(post.careId);
    }
  }

  onDelete(post: PostResponse): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const msg = this.translocoService.translate('posts.deleteConfirm');
    if (confirm(msg)) {
      this.postsService.delete(post.id).subscribe({
        next: () => {
          this.posts.update((list) => list.filter((p) => p.id !== post.id));
        },
      });
    }
  }

  openCreateModal(): void {
    const ref = this.dialog.open(CreatePostModalComponent, {
      width: '520px',
      maxHeight: '90vh',
      disableClose: false,
    });

    ref.afterClosed().subscribe((created: PostResponse | undefined) => {
      if (created) {
        this.posts.update((list) => [created, ...list]);
      }
    });
  }

  formatDate(isoString: string): string {
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString(this.translocoService.getActiveLang(), {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      });
    } catch {
      return isoString;
    }
  }
}
