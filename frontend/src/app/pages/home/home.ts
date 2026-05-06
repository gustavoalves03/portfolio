import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';
import { PostsService } from '../../features/posts/posts.service';
import { RecentPost } from '../../features/posts/posts.model';
import { RecentPostsViewerComponent } from '../../features/posts/recent-posts-viewer/recent-posts-viewer.component';
import { HeroVideoComponent } from '../../shared/uis/hero-video/hero-video.component';
import { SalonCarouselComponent } from '../../shared/uis/salon-carousel/salon-carousel.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatIconModule,
    RecentPostsViewerComponent,
    HeroVideoComponent,
    SalonCarouselComponent,
  ],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly router = inject(Router);
  private readonly discoveryService = inject(DiscoveryService);
  private readonly postsService = inject(PostsService);

  readonly searchQuery = signal('');

  readonly salons = toSignal(
    toObservable(this.searchQuery).pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) => this.discoveryService.searchSalons(null, q || null)),
    ),
    { initialValue: [] as SalonCard[] },
  );

  readonly recentPosts = toSignal(this.postsService.listRecentPublic(), {
    initialValue: [] as RecentPost[],
  });

  onSearch(): void {
    // Reactive — handled by toObservable(searchQuery).
  }

  onDiscoverAll(): void {
    this.router.navigate(['/discover']);
  }

  onProCta(): void {
    this.router.navigate(['/pricing']);
  }
}
