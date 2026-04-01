import { Component, computed, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { CaresStore } from './store/cares.store';
import { CategoriesStore } from '../categories/store/categories.store';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CreateCare } from './modals/create/create-care.component';
import { Care, CareStatus } from './models/cares.model';
import { DeleteCareComponent } from './modals/delete/delete-care.component';
import { CaresService } from './services/cares.service';
import { CreateCategoryComponent } from '../categories/modals/create/create-category.component';
import { ReassignCategoryDialogComponent } from '../categories/modals/reassign-category/reassign-category-dialog.component';
import { Category } from '../categories/models/categories.model';

const CATEGORY_COLORS = [
  '#f4e1d2', // sable
  '#f9d5d3', // rose poudré
  '#dce8d2', // sauge
  '#d5e5f0', // brume
  '#f0dde4', // nacre rosé
  '#e8ddd0', // beige doré
  '#d8e2dc', // menthe douce
  '#f0e6cc', // vanille
];

@Component({
  selector: 'app-cares',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatSnackBarModule,
    MatIconModule,
    MatMenuModule,
    MatCardModule,
    MatButtonModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    FormsModule,
  ],
  templateUrl: './cares.component.html',
  styleUrl: './cares.component.scss',
  providers: [CaresStore, CategoriesStore],
})
export class CaresComponent {
  readonly store = inject(CaresStore);
  readonly categoriesStore = inject(CategoriesStore);
  private caresService = inject(CaresService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private i18n = inject(TranslocoService);

  // Category filtering
  selectedCategoryId = signal<number | null>(null);

  // Search query
  searchQuery = signal('');

  filteredCares = computed(() => {
    const selectedId = this.selectedCategoryId();
    const query = this.searchQuery().toLowerCase().trim();
    let cares = this.store.availableCares();
    if (selectedId) {
      cares = cares.filter(c => c.category.id === selectedId);
    }
    if (query) {
      cares = cares.filter(
        c =>
          c.name.toLowerCase().includes(query) ||
          c.description?.toLowerCase().includes(query),
      );
    }
    return cares;
  });

  private readonly fallbackGradients = [
    'linear-gradient(135deg, #f3d5c0, #e8c4b0)',
    'linear-gradient(135deg, #d4b5d0, #c8a0c0)',
    'linear-gradient(135deg, #b5d4c0, #a0c8b0)',
    'linear-gradient(135deg, #c0d4f3, #b0c4e8)',
  ];

  constructor() {
    this.categoriesStore.getProCategories();
  }

  getCategoryColor(categoryId: number): string {
    return CATEGORY_COLORS[categoryId % CATEGORY_COLORS.length];
  }

  fallbackGradient(index: number): string {
    return this.fallbackGradients[index % this.fallbackGradients.length];
  }

  formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' \u20AC';
  }

  onToggleStatus(care: Care, checked: boolean): void {
    this.store.toggleCareStatus({
      id: care.id,
      status: checked ? CareStatus.ACTIVE : CareStatus.INACTIVE,
    });
    this.snackBar.open(this.i18n.translate('cares.toggleSuccess'), 'OK', { duration: 2000 });
  }

  onSelectCategory(categoryId: number): void {
    this.selectedCategoryId.update(current => (current === categoryId ? null : categoryId));
  }

  onAddCategory(): void {
    const dialogRef = this.dialog.open(CreateCategoryComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.categoriesStore.createProCategory(result);
        this.snackBar.open(this.i18n.translate('pro.categories.createSuccess'), 'OK', {
          duration: 3000,
        });
      }
    });
  }

  onEditCategory(category: Category): void {
    const dialogRef = this.dialog.open(CreateCategoryComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
      data: { category },
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.categoriesStore.updateProCategory({ id: category.id, payload: result });
        this.snackBar.open(this.i18n.translate('pro.categories.updateSuccess'), 'OK', {
          duration: 3000,
        });
      }
    });
  }

  onDeleteCategory(category: Category): void {
    const caresInCategory = this.store.cares().filter(c => c.category.id === category.id);

    if (caresInCategory.length > 0) {
      const dialogRef = this.dialog.open(ReassignCategoryDialogComponent, {
        width: '450px',
        disableClose: true,
        data: {
          categoryId: category.id,
          categoryName: category.name,
          careCount: caresInCategory.length,
          availableCategories: this.categoriesStore.categories(),
        },
      });

      dialogRef.afterClosed().subscribe(targetId => {
        if (targetId) {
          this.categoriesStore.deleteProCategory({ id: category.id, reassignTo: targetId });
          this.selectedCategoryId.set(null);
          this.store.getCares();
          this.snackBar.open(this.i18n.translate('pro.categories.deleteSuccess'), 'OK', {
            duration: 3000,
          });
        }
      });
    } else {
      this.categoriesStore.deleteProCategory({ id: category.id });
      this.selectedCategoryId.set(null);
      this.snackBar.open(this.i18n.translate('pro.categories.deleteSuccess'), 'OK', {
        duration: 3000,
      });
    }
  }

  onReorder(reorderedCares: Care[]): void {
    const orderedIds = reorderedCares.map(c => c.id);
    this.store.reorderCares(orderedIds);
    this.snackBar.open(this.i18n.translate('cares.reorderSuccess'), 'OK', { duration: 2000 });
  }

  onAddCare() {
    const dialogRef = this.dialog.open(CreateCare, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
      data: {
        categories: this.categoriesStore.categories(),
      },
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.store.createCare(result);
      }
    });
  }

  onEditCare(care: Care) {
    // Fetch fresh care details from backend to ensure images are up to date
    this.caresService.get(care.id).subscribe({
      next: freshCare => {
        console.log('[CaresComponent] Fresh care loaded for edit:', freshCare);
        console.log('[CaresComponent] Images:', freshCare.images);

        const dialogRef = this.dialog.open(CreateCare, {
          width: '500px',
          disableClose: false,
          autoFocus: true,
          data: {
            categories: this.categoriesStore.categories(),
            care: freshCare,
          },
        });

        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            const payload = {
              ...result,
              categoryId: Number(result.categoryId ?? freshCare.category.id),
            };
            this.store.updateCare({ id: freshCare.id, payload });
          }
        });
      },
      error: err => {
        console.error('Error loading care for edit:', err);
        this.snackBar.open('Erreur lors du chargement du soin', 'Fermer', {
          duration: 3000,
        });
      },
    });
  }

  onDeleteCare(care: Care) {
    const dialogRef = this.dialog.open(DeleteCareComponent, {
      width: '420px',
      disableClose: true,
      autoFocus: false,
      data: {
        careName: care.name,
      },
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.store.deleteCare(care.id);
      }
    });
  }

  readonly onViewCareDetails = (care: Care) => {
    // Fetch fresh care details from backend to ensure images are up to date
    this.caresService.get(care.id).subscribe({
      next: freshCare => {
        console.log('[CaresComponent] Fresh care loaded:', freshCare);
        console.log('[CaresComponent] Images:', freshCare.images);

        // Open dialog with fresh data
        this.dialog.open(CreateCare, {
          width: '500px',
          disableClose: false,
          autoFocus: false,
          data: {
            care: freshCare,
            categories: this.categoriesStore.categories(),
            viewOnly: true,
          },
        });
      },
      error: err => {
        console.error('Error loading care details:', err);
        this.snackBar.open('Erreur lors du chargement des détails', 'Fermer', {
          duration: 3000,
        });
      },
    });
  };
}
