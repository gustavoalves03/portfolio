import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

interface CategoryCard {
  name: string;
  slug: string;
  emoji: string;
  color: string;
  count: number;
}

interface FeaturedSalon {
  name: string;
  slug: string;
  category: string;
  city: string;
  rating: number;
  gradient: string;
}

const CATEGORIES: CategoryCard[] = [
  { name: 'Soins visage', slug: 'soins-visage', emoji: '💆', color: '#f4e1d2', count: 12 },
  { name: 'Ongles', slug: 'ongles', emoji: '💅', color: '#f9d5d3', count: 8 },
  { name: 'Coiffure', slug: 'coiffure', emoji: '✂️', color: '#dce8d2', count: 15 },
  { name: 'Épilation', slug: 'epilation', emoji: '🧖', color: '#d5e5f0', count: 6 },
];

const FEATURED_SALONS: FeaturedSalon[] = [
  { name: 'Atelier Lumière', slug: 'atelier-lumiere', category: 'Soins visage', city: 'Paris 11', rating: 4.8, gradient: 'linear-gradient(135deg, #e8d5c4, #f0e0d0)' },
  { name: 'Rose & Thé', slug: 'rose-et-the', category: 'Ongles', city: 'Lyon 6', rating: 4.9, gradient: 'linear-gradient(135deg, #d5e0d2, #e0ead5)' },
  { name: 'Belle Époque', slug: 'belle-epoque', category: 'Coiffure', city: 'Bordeaux', rating: 4.7, gradient: 'linear-gradient(135deg, #d5d5e8, #e0e0f0)' },
  { name: 'Douceur de Soi', slug: 'douceur-de-soi', category: 'Épilation', city: 'Nantes', rating: 4.6, gradient: 'linear-gradient(135deg, #e0d5d8, #f0e5e8)' },
  { name: "Les Mains d'Or", slug: 'les-mains-dor', category: 'Ongles', city: 'Toulouse', rating: 4.8, gradient: 'linear-gradient(135deg, #f0e6cc, #f5ecd5)' },
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

  readonly categories = CATEGORIES;
  readonly salons = FEATURED_SALONS;
  readonly searchQuery = signal('');

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
    this.router.navigate(['/register']);
  }
}
