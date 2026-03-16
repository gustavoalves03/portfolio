import { Component, computed, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { CaresStore } from './store/cares.store';
import { CategoriesStore } from '../categories/store/categories.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TableColumn, TableAction } from '../../shared/uis/crud-table/crud-table.models';
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
  imports: [CrudTable, TranslocoPipe, MatSnackBarModule, MatIconModule, MatMenuModule],
  templateUrl: './cares.component.html',
  styleUrl: './cares.component.scss',
  providers: [CaresStore, CategoriesStore]
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

  filteredCares = computed(() => {
    const selectedId = this.selectedCategoryId();
    const cares = this.store.availableCares();
    return selectedId ? cares.filter(c => c.category.id === selectedId) : cares;
  });

  // Configuration des colonnes avec traduction (en signal)
  readonly columns = signal<TableColumn[]>([
    { key: 'name', headerKey: 'cares.columns.name', align: 'left' },
    {
      key: 'description',
      headerKey: 'cares.columns.description',
      align: 'left',
      valueGetter: (care: Care) => this.truncateDescription(care.description)
    },
    { key: 'price', headerKey: 'cares.columns.price', type: 'currency', align: 'right' },
    {
      key: 'status',
      headerKey: 'cares.columns.status',
      align: 'center',
      valueGetter: (care: Care) => this.translateStatus(care.status)
    },
    { key: 'duration', headerKey: 'cares.columns.duration', align: 'center' }
  ]);

  // Configuration des actions (modifier, supprimer) (en signal)
  readonly actions = signal<TableAction[]>([
    {
      icon: 'edit',
      tooltipKey: 'actions.edit',
      color: 'primary',
      callback: (care: Care) => this.onEditCare(care)
    },
    {
      icon: 'delete',
      tooltipKey: 'actions.delete',
      color: 'warn',
      callback: (care: Care) => this.onDeleteCare(care)
    }
  ]);

  constructor() {
    this.categoriesStore.getProCategories();
  }

  getCategoryColor(categoryId: number): string {
    return CATEGORY_COLORS[categoryId % CATEGORY_COLORS.length];
  }

  onSelectCategory(categoryId: number): void {
    this.selectedCategoryId.update(current =>
      current === categoryId ? null : categoryId
    );
  }

  onAddCategory(): void {
    const dialogRef = this.dialog.open(CreateCategoryComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.categoriesStore.createProCategory(result);
        this.snackBar.open(this.i18n.translate('pro.categories.createSuccess'), 'OK', { duration: 3000 });
      }
    });
  }

  onEditCategory(category: Category): void {
    const dialogRef = this.dialog.open(CreateCategoryComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
      data: { category }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.categoriesStore.updateProCategory({ id: category.id, payload: result });
        this.snackBar.open(this.i18n.translate('pro.categories.updateSuccess'), 'OK', { duration: 3000 });
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
          availableCategories: this.categoriesStore.categories()
        }
      });

      dialogRef.afterClosed().subscribe(targetId => {
        if (targetId) {
          this.categoriesStore.deleteProCategory({ id: category.id, reassignTo: targetId });
          this.selectedCategoryId.set(null);
          this.store.getCares();
          this.snackBar.open(this.i18n.translate('pro.categories.deleteSuccess'), 'OK', { duration: 3000 });
        }
      });
    } else {
      this.categoriesStore.deleteProCategory({ id: category.id });
      this.selectedCategoryId.set(null);
      this.snackBar.open(this.i18n.translate('pro.categories.deleteSuccess'), 'OK', { duration: 3000 });
    }
  }

  onAddCare() {
    const dialogRef = this.dialog.open(CreateCare, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
      data: {
        categories: this.categoriesStore.categories()
      }
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
      next: (freshCare) => {
        console.log('[CaresComponent] Fresh care loaded for edit:', freshCare);
        console.log('[CaresComponent] Images:', freshCare.images);

        const dialogRef = this.dialog.open(CreateCare, {
          width: '500px',
          disableClose: false,
          autoFocus: true,
          data: {
            categories: this.categoriesStore.categories(),
            care: freshCare
          }
        });

        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            const payload = {
              ...result,
              categoryId: Number(result.categoryId ?? freshCare.category.id)
            };
            this.store.updateCare({ id: freshCare.id, payload });
          }
        });
      },
      error: (err) => {
        console.error('Error loading care for edit:', err);
        this.snackBar.open('Erreur lors du chargement du soin', 'Fermer', {
          duration: 3000
        });
      }
    });
  }

  onDeleteCare(care: Care) {
    const dialogRef = this.dialog.open(DeleteCareComponent, {
      width: '420px',
      disableClose: true,
      autoFocus: false,
      data: {
        careName: care.name
      }
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
      next: (freshCare) => {
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
            viewOnly: true
          }
        });
      },
      error: (err) => {
        console.error('Error loading care details:', err);
        this.snackBar.open('Erreur lors du chargement des détails', 'Fermer', {
          duration: 3000
        });
      }
    });
  };

  private translateStatus(status: CareStatus): string {
    return this.i18n.translate(`cares.status.${status}`) || status;
  }

  private truncateDescription(description: string): string {
    const maxLength = 60;
    if (!description || description.length <= maxLength) {
      return description;
    }
    return description.substring(0, maxLength) + '...';
  }
}
