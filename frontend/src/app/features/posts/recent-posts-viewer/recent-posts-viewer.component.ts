import {
  Component,
  inject,
  input,
  signal,
  ElementRef,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RecentPost } from '../posts.model';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

@Component({
  selector: 'app-recent-posts-viewer',
  standalone: true,
  imports: [MatIconModule, TranslocoPipe],
  template: `
    @if (posts().length > 0) {
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
                      (dragstart)="$event.preventDefault()"
                      (touchstart)="onBaTouchStart($event, post.id)"
                      (touchmove)="onBaTouchMove($event, post.id)"
                      (touchend)="onBaDragEnd(post.id)"
                    >
                      @if (post.afterImageUrl) {
                        <img [src]="imgUrl(post.afterImageUrl)" class="ba-img ba-after" alt="After" draggable="false" />
                      }
                      @if (post.beforeImageUrl) {
                        <img
                          [src]="imgUrl(post.beforeImageUrl)"
                          class="ba-img ba-before" draggable="false"
                          [style.clip-path]="'inset(0 ' + (100 - getBaSplit(post.id)) + '% 0 0)'"
                          alt="Before"
                        />
                      }
                      <div class="ba-handle" [style.left.%]="getBaSplit(post.id)">
                        <div class="ba-handle-line"></div>
                        <div class="ba-handle-circle"><mat-icon>swap_horiz</mat-icon></div>
                        <div class="ba-handle-line"></div>
                      </div>
                      <span class="ba-label ba-label-before">{{ 'posts.before' | transloco }}</span>
                      <span class="ba-label ba-label-after">{{ 'posts.after' | transloco }}</span>
                    </div>
                  }
                  @case ('PHOTO') {
                    @if (post.afterImageUrl) {
                      <img [src]="imgUrl(post.afterImageUrl)" class="photo-img" alt="" />
                    } @else if (post.beforeImageUrl) {
                      <img [src]="imgUrl(post.beforeImageUrl)" class="photo-img" alt="" />
                    }
                  }
                  @case ('CAROUSEL') {
                    <div class="carousel-container">
                      <div class="carousel-track"
                        [style.transform]="'translateX(-' + getCarouselIndex(post.id) * 100 + '%)'">
                        @for (url of post.carouselImageUrls; track url; let ci = $index) {
                          <img [src]="imgUrl(url)" class="carousel-slide" [alt]="'Slide ' + (ci + 1)" />
                        }
                      </div>
                      <div class="carousel-tap carousel-tap-left"
                        (click)="carouselPrev(post.id, post.carouselImageUrls.length)"></div>
                      <div class="carousel-tap carousel-tap-right"
                        (click)="carouselNext(post.id, post.carouselImageUrls.length)"></div>
                      @if (post.carouselImageUrls.length > 1) {
                        <div class="carousel-dots">
                          @for (url of post.carouselImageUrls; track url; let ci = $index) {
                            <div class="carousel-dot" [class.active]="getCarouselIndex(post.id) === ci"></div>
                          }
                        </div>
                      }
                    </div>
                  }
                }

                <!-- Top gradient + salon header -->
                <div class="snap-gradient-top"></div>
                <div class="salon-header" (click)="goToSalon(post); $event.stopPropagation()">
                  <div
                    class="salon-logo"
                    [style.background]="post.salonLogoUrl ? 'url(' + imgUrl(post.salonLogoUrl) + ') center/cover' : 'linear-gradient(135deg, #e8d5c4, #f0e0d0)'"
                  >
                    @if (!post.salonLogoUrl) {
                      <span class="salon-initial">{{ (post.salonName || '?').charAt(0) }}</span>
                    }
                  </div>
                  <div class="salon-meta">
                    <span class="salon-name">{{ post.salonName }}</span>
                    @if (post.salonCity) {
                      <span class="salon-city">{{ post.salonCity }}</span>
                    }
                  </div>
                </div>

                <!-- Bottom gradient + info -->
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
  `,
  styles: `
    .snap-viewport {
      position: relative;
      aspect-ratio: 3 / 4;
      max-height: 85vh;
      border-radius: 16px;
      overflow: hidden;
      background: #000;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
      margin: 0 auto;
    }
    @media (min-width: 768px) {
      .snap-viewport { max-width: 400px; }
    }

    .snap-scroll {
      height: 100%; overflow-y: scroll; scroll-snap-type: y mandatory;
      overscroll-behavior: contain; -webkit-overflow-scrolling: touch;
      scrollbar-width: none;
      &::-webkit-scrollbar { display: none; }
    }

    .snap-item { scroll-snap-align: start; height: 100%; width: 100%; position: relative; }
    .snap-media { position: relative; width: 100%; height: 100%; overflow: hidden; }
    .photo-img { width: 100%; height: 100%; object-fit: cover; }

    /* Before / After */
    .ba-container { position: relative; width: 100%; height: 100%; cursor: ew-resize; user-select: none; touch-action: none; }
    .ba-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; -webkit-user-drag: none; user-select: none; pointer-events: none; }
    .ba-before { z-index: 2; }
    .ba-after { z-index: 1; }
    .ba-handle { position: absolute; top: 0; bottom: 0; z-index: 3; display: flex; flex-direction: column; align-items: center; transform: translateX(-50%); pointer-events: none; }
    .ba-handle-line { flex: 1; width: 2px; background: #fff; box-shadow: 0 0 4px rgba(0,0,0,0.4); }
    .ba-handle-circle {
      width: 36px; height: 36px; border-radius: 50%; background: #fff;
      display: flex; align-items: center; justify-content: center;
      box-shadow: 0 2px 8px rgba(0,0,0,0.3);
      mat-icon { font-size: 20px; width: 20px; height: 20px; color: #333; }
    }
    .ba-label {
      position: absolute; top: 12px; z-index: 4; padding: 3px 10px; border-radius: 12px;
      font-size: 11px; font-weight: 600; text-transform: uppercase; color: #fff;
      background: rgba(0,0,0,0.45); pointer-events: none;
    }
    .ba-label-before { left: 12px; }
    .ba-label-after { right: 12px; }

    /* Carousel */
    .carousel-container { position: relative; width: 100%; height: 100%; overflow: hidden; }
    .carousel-track { display: flex; height: 100%; transition: transform 250ms ease; }
    .carousel-slide { min-width: 100%; width: 100%; height: 100%; object-fit: cover; }
    .carousel-tap { position: absolute; top: 0; bottom: 0; width: 50%; z-index: 2; cursor: pointer; }
    .carousel-tap-left { left: 0; }
    .carousel-tap-right { right: 0; }
    .carousel-dots { position: absolute; top: 10px; left: 50%; transform: translateX(-50%); display: flex; gap: 5px; z-index: 3; }
    .carousel-dot {
      width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,0.5); transition: background 150ms ease;
      &.active { background: #fff; }
    }

    /* Top gradient + salon header */
    .snap-gradient-top {
      position: absolute; top: 0; left: 0; right: 0; height: 30%; z-index: 5;
      background: linear-gradient(to bottom, rgba(0,0,0,0.5) 0%, transparent 100%);
      pointer-events: none;
    }
    .salon-header {
      position: absolute; top: 12px; left: 14px; right: 14px; z-index: 6;
      display: flex; align-items: center; gap: 8px; cursor: pointer;
    }
    .salon-logo {
      width: 32px; height: 32px; border-radius: 50%;
      border: 2px solid rgba(255,255,255,0.7);
      display: flex; align-items: center; justify-content: center; flex-shrink: 0; overflow: hidden;
    }
    .salon-initial { font-size: 13px; color: rgba(255,255,255,0.8); font-weight: 300; }
    .salon-meta { display: flex; flex-direction: column; }
    .salon-name { font-size: 13px; color: #fff; font-weight: 600; text-shadow: 0 1px 3px rgba(0,0,0,0.3); }
    .salon-city { font-size: 10px; color: rgba(255,255,255,0.7); }

    /* Bottom gradient + info */
    .snap-gradient {
      position: absolute; bottom: 0; left: 0; right: 0; height: 45%; z-index: 5;
      background: linear-gradient(to top, rgba(0,0,0,0.7) 0%, transparent 100%);
      pointer-events: none;
    }
    .snap-info {
      position: absolute; bottom: 16px; left: 16px; right: 50px; z-index: 6;
      display: flex; flex-direction: column; gap: 5px;
    }
    .post-type-pill {
      display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px;
      border-radius: 12px; background: rgba(255,255,255,0.15); backdrop-filter: blur(6px);
      color: #fff; font-size: 11px; font-weight: 500; width: fit-content;
      mat-icon { font-size: 14px; width: 14px; height: 14px; }
    }
    .post-caption { font-size: 13px; color: #fff; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
    .post-tags { display: flex; gap: 6px; flex-wrap: wrap; }
    .post-tag { padding: 2px 8px; border-radius: 10px; background: rgba(192,0,102,0.6); color: #fff; font-size: 11px; font-weight: 500; }
    .post-meta { font-size: 11px; color: rgba(255,255,255,0.5); }

    /* Progress dots */
    .snap-progress {
      position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
      display: flex; flex-direction: column; gap: 6px; z-index: 6;
    }
    .sp-dot {
      width: 6px; height: 6px; border-radius: 50%; background: rgba(255,255,255,0.35);
      transition: all 200ms ease;
      &.active { background: #fff; transform: scale(1.3); }
    }
  `,
})
export class RecentPostsViewerComponent {
  private readonly router = inject(Router);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly translocoService = inject(TranslocoService);

  readonly posts = input.required<RecentPost[]>();
  readonly currentIndex = signal(0);
  readonly snapScroll = viewChild<ElementRef<HTMLElement>>('snapScroll');

  private carouselIndices: Record<number, number> = {};
  private baSplits: Record<number, number> = {};
  private baDragging: Record<number, boolean> = {};

  onScroll(): void {
    const el = this.snapScroll()?.nativeElement;
    if (!el) return;
    this.currentIndex.set(Math.round(el.scrollTop / el.clientHeight));
  }

  goToSalon(post: RecentPost): void {
    if (post.salonSlug) this.router.navigate(['/salon', post.salonSlug]);
  }

  imgUrl(path: string | null): string {
    if (!path) return '';
    if (path.startsWith('http')) return path;
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return base + path;
  }

  formatDate(isoString: string): string {
    try {
      return new Date(isoString).toLocaleDateString(this.translocoService.getActiveLang(), {
        day: 'numeric', month: 'short', year: 'numeric',
      });
    } catch { return isoString; }
  }

  // Before/After slider
  getBaSplit(postId: number): number { return this.baSplits[postId] ?? 50; }

  onBaDragStart(e: MouseEvent, postId: number): void {
    e.preventDefault();
    this.baDragging[postId] = true;
    this.updateBaSplit(e.clientX, postId, e.currentTarget as HTMLElement);
  }
  onBaDragMove(e: MouseEvent, postId: number): void {
    if (!this.baDragging[postId]) return;
    this.updateBaSplit(e.clientX, postId, e.currentTarget as HTMLElement);
  }
  onBaDragEnd(postId: number): void { this.baDragging[postId] = false; }
  onBaTouchStart(e: TouchEvent, postId: number): void {
    this.baDragging[postId] = true;
    if (e.touches.length > 0) this.updateBaSplit(e.touches[0].clientX, postId, e.currentTarget as HTMLElement);
  }
  onBaTouchMove(e: TouchEvent, postId: number): void {
    if (!this.baDragging[postId]) return;
    if (e.touches.length > 0) this.updateBaSplit(e.touches[0].clientX, postId, e.currentTarget as HTMLElement);
  }
  private updateBaSplit(clientX: number, postId: number, container: HTMLElement): void {
    const rect = container.getBoundingClientRect();
    this.baSplits[postId] = Math.max(5, Math.min(95, ((clientX - rect.left) / rect.width) * 100));
  }

  // Carousel
  getCarouselIndex(postId: number): number { return this.carouselIndices[postId] ?? 0; }
  carouselPrev(postId: number, total: number): void {
    const cur = this.getCarouselIndex(postId);
    this.carouselIndices[postId] = cur > 0 ? cur - 1 : total - 1;
  }
  carouselNext(postId: number, total: number): void {
    const cur = this.getCarouselIndex(postId);
    this.carouselIndices[postId] = cur < total - 1 ? cur + 1 : 0;
  }
}
