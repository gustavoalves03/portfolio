import { Component, inject } from '@angular/core';
import { CategoriesStore } from './store/categories.store';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.scss',
  providers: [CategoriesStore],
})
export class CategoriesComponent {
  readonly store = inject(CategoriesStore);
}
