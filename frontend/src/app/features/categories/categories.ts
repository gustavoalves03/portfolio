import { Component, OnInit, inject, signal } from '@angular/core';
import { AsyncPipe, NgForOf, NgIf } from '@angular/common';
import { CategoriesService } from './services/categories.service';
import { Category } from './models/categories.model';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [NgIf, NgForOf, AsyncPipe],
  templateUrl: './categories.html',
  styleUrl: './categories.scss',
})
export class Categories implements OnInit {
  private readonly service = inject(CategoriesService);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.service.list({ page: 0, size: 50 }).subscribe({
      next: (page) => {
        this.categories.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Erreur de chargement des catégories');
        this.loading.set(false);
      },
    });
  }
}

