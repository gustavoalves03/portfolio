import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { SalonCard } from '../../features/discovery/discovery.model';

interface CategoryCard {
  name: string;
  slug: string;
  emoji: string;
  color: string;
}

const CATEGORIES: CategoryCard[] = [
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
  selector: 'app-home',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private router = inject(Router);
  private discoveryService = inject(DiscoveryService);

  readonly categories = CATEGORIES;
  readonly salons = toSignal(this.discoveryService.searchSalons(), { initialValue: [] as SalonCard[] });
  readonly searchQuery = signal('');

  getGradient(index: number): string {
    return GRADIENTS[index % GRADIENTS.length];
  }

  onSearch(): void {
    const q = this.searchQuery().trim();
    if (q) {
      this.router.navigate(['/discover'], { queryParams: { q } });
    }
  }

  onCategoryClick(slug: string): void {
    this.router.navigate(['/discover'], { queryParams: { category: slug } });
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  onProCta(): void {
    this.router.navigate(['/pricing']);
  }
}
