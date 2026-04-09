import { Component, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { PostsService } from '../../features/posts/posts.service';
import { RecentPost } from '../../features/posts/posts.model';
import { RecentPostsViewerComponent } from '../../features/posts/recent-posts-viewer/recent-posts-viewer.component';

@Component({
  selector: 'app-feed',
  standalone: true,
  imports: [RecentPostsViewerComponent, TranslocoPipe, MatIconModule],
  template: `
    <div class="feed-page">
      @if (isBrowser && posts().length > 0) {
        <app-recent-posts-viewer [posts]="posts()" />
      } @else if (isBrowser && posts().length === 0) {
        <div class="feed-empty">
          <mat-icon>photo_library</mat-icon>
          <p>{{ 'feed.empty' | transloco }}</p>
        </div>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .feed-page {
      max-width: 100%;
      margin: 0 auto;
      padding: 0;
    }

    @media (max-width: 767px) {
      :host ::ng-deep .snap-viewport {
        max-width: 100% !important;
        max-height: calc(100dvh - 56px - 80px) !important;
        border-radius: 0 !important;
        aspect-ratio: unset !important;
        height: calc(100dvh - 56px - 80px) !important;
        box-shadow: none !important;
      }
    }

    @media (min-width: 768px) {
      .feed-page {
        max-width: 500px;
        padding: 16px 0;
      }
    }

    .feed-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 50vh;
      color: #999;
      gap: 8px;
    }
  `,
})
export class FeedComponent {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly postsService = inject(PostsService);

  readonly isBrowser = isPlatformBrowser(this.platformId);

  readonly posts = toSignal(this.postsService.listRecentPublic(), {
    initialValue: [] as RecentPost[],
  });
}
