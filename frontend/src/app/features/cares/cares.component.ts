import { Component, effect, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CaresStore } from './store/cares.store';
import { CategoriesStore } from '../categories/store/categories.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TableColumn, TableAction } from '../../shared/uis/crud-table/crud-table.models';
import { TranslocoPipe } from '@jsverse/transloco';
import { CreateCare } from './modals/create/create-care.component';

@Component({
  selector: 'app-cares',
  standalone: true,
  imports: [CrudTable, TranslocoPipe, MatSnackBarModule],
  templateUrl: './cares.component.html',
  styleUrl: './cares.component.scss',
  providers: [CaresStore, CategoriesStore]
})
export class CaresComponent {
  readonly store = inject(CaresStore);
  readonly categoriesStore = inject(CategoriesStore);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  // Configuration des colonnes avec traduction (en signal)
  readonly columns = signal<TableColumn[]>([
    { key: 'name', headerKey: 'cares.columns.name', align: 'left' },
    { key: 'description', headerKey: 'cares.columns.description', align: 'left' },
    { key: 'price', headerKey: 'cares.columns.price', type: 'currency', align: 'right' },
    { key: 'duration', headerKey: 'cares.columns.duration', align: 'center' }
  ]);

  // Configuration des actions (modifier, supprimer) (en signal)
  readonly actions = signal<TableAction[]>([
    {
      icon: 'edit',
      tooltipKey: 'actions.edit',
      color: 'primary',
      callback: (care: any) => this.onEditCare(care)
    },
    {
      icon: 'delete',
      tooltipKey: 'actions.delete',
      color: 'warn',
      callback: (care: any) => this.onDeleteCare(care)
    }
  ]);

  private readonly showErrorSnack = effect(() => {

  });

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

  onEditCare(care: any) {
    console.log('Edit care:', care);
    // TODO: Ouvrir modal d'édition
    this.snackBar.open(`Édition de ${care.name}`, 'OK', { duration: 2000 });
  }

  onDeleteCare(care: any) {
    if (confirm(`Êtes-vous sûr de vouloir supprimer "${care.name}" ?`)) {
      this.store.deleteCare(care.id);
    }
  }
}
