import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { map, switchMap } from 'rxjs';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';

interface CategoryFilter {
  name: string;
  slug: string;
  emoji: string;
  color: string;
}

const CATEGORIES: CategoryFilter[] = [
  { name: 'Soins visage', slug: 'soins-visage', emoji: '💆', color: '#f4e1d2' },
  { name: 'Soins corps', slug: 'soins-corps', emoji: '🧖', color: '#d5e5f0' },
  { name: 'Ongles', slug: 'ongles', emoji: '💅', color: '#f9d5d3' },
  { name: 'Maquillage', slug: 'maquillage', emoji: '💄', color: '#dce8d2' },
  { name: 'Épilation', slug: 'epilation', emoji: '✨', color: '#e8daf0' },
];

const GRADIENTS = [
  'linear-gradient(135deg, #e8d5c4, #f0e0d0)',
  'linear-gradient(135deg, #d5e0d2, #e0ead5)',
  'linear-gradient(135deg, #d5d5e8, #e0e0f0)',
  'linear-gradient(135deg, #e0d5d8, #f0e5e8)',
  'linear-gradient(135deg, #f0e6cc, #f5ecd5)',
];

@Component({
  selector: 'app-discover-page',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './discover-page.component.html',
  styleUrl: './discover-page.component.scss',
})
export class DiscoverPageComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private discoveryService = inject(DiscoveryService);

  readonly categories = CATEGORIES;
  readonly searchQuery = signal('');

  readonly selectedCategory = toSignal(
    this.route.queryParamMap.pipe(map((p) => p.get('category'))),
    { initialValue: null as string | null }
  );

  readonly salons = toSignal(
    this.route.queryParamMap.pipe(
      switchMap((p) =>
        this.discoveryService.searchSalons(p.get('category'), p.get('q'))
      )
    ),
    { initialValue: [] as SalonCard[] }
  );

  getGradient(index: number): string {
    return GRADIENTS[index % GRADIENTS.length];
  }

  onCategoryClick(slug: string | null): void {
    this.router.navigate(['/discover'], {
      queryParams: slug ? { category: slug } : {},
    });
  }

  onSearch(): void {
    const q = this.searchQuery().trim();
    this.router.navigate(['/discover'], {
      queryParams: q ? { q } : {},
    });
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  truncate(text: string | null, max: number): string {
    if (!text) return '';
    const plain = text.replace(/<[^>]*>/g, '');
    return plain.length > max ? plain.substring(0, max) + '...' : plain;
  }
}
