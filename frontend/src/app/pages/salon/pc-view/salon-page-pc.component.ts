import { Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  PublicSalonResponse,
  PublicCareDto,
} from '../../../features/salon-profile/models/salon-profile.model';
import { SalonPostsViewerComponent } from '../../../features/posts/salon-posts-viewer/salon-posts-viewer.component';

/** No opening hours in PublicSalonResponse, so we just show contact info. */

@Component({
  selector: 'app-salon-page-pc',
  standalone: true,
  imports: [TranslocoPipe, SalonPostsViewerComponent],
  templateUrl: './salon-page-pc.component.html',
  styleUrl: './salon-page-pc.component.scss',
})
export class SalonPagePcComponent {
  readonly salon = input.required<PublicSalonResponse>();
  readonly slug = input.required<string>();
  readonly isPreviewMode = input(false);

  readonly book = output<PublicCareDto>();
  readonly bookFromPost = output<number>();

  // ── Address helpers ───────────────────────────────────────────────────
  readonly fullAddress = computed(() => {
    const s = this.salon();
    const parts = [s.addressStreet, s.addressPostalCode, s.addressCity].filter(
      (p): p is string => !!p
    );
    return parts.join(', ');
  });

  // ── Hero image ────────────────────────────────────────────────────────
  readonly heroImage = computed(() => {
    const s = this.salon();
    return s.heroImageUrl || null;
  });

  // ── Care ranking helpers ─────────────────────────────────────────────
  protected formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h} h ${m.toString().padStart(2, '0')}` : `${h} h`;
  }

  protected formatPrice(cents: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(cents / 100);
  }

  private readonly fallbackGradients = [
    'linear-gradient(135deg, #fce4ed, #f5d0e0)',
    'linear-gradient(135deg, #f3d5c0, #e8c4b0)',
    'linear-gradient(135deg, #d4b5d0, #c8a0c0)',
    'linear-gradient(135deg, #c0d4f3, #b0c4e8)',
  ];

  protected fallbackGradient(index: number): string {
    return this.fallbackGradients[index % this.fallbackGradients.length];
  }

  protected onBook(care: PublicCareDto): void {
    if (this.isPreviewMode()) return;
    this.book.emit(care);
  }

  protected onBookFromPost(careId: number): void {
    if (this.isPreviewMode()) return;
    this.bookFromPost.emit(careId);
  }
}
