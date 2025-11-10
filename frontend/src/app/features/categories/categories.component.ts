import { Component, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CategoriesStore } from './store/categories.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TableColumn, TableAction } from '../../shared/uis/crud-table/crud-table.models';
import { TranslocoPipe } from '@jsverse/transloco';
import { CreateCategoryComponent } from './modals/create/create-category.component';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [CrudTable, TranslocoPipe, MatSnackBarModule],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.scss',
  providers: [CategoriesStore],
})
export class CategoriesComponent {
  readonly store = inject(CategoriesStore);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  // Configuration des colonnes avec traduction
  readonly columns = signal<TableColumn[]>([
    { key: 'name', headerKey: 'categories.columns.name', align: 'left' },
    { key: 'description', headerKey: 'categories.columns.description', align: 'left' }
  ]);

  // Configuration des actions
  readonly actions = signal<TableAction[]>([
    {
      icon: 'edit',
      tooltipKey: 'actions.edit',
      color: 'primary',
      callback: (category: any) => this.onEditCategory(category)
    },
    {
      icon: 'delete',
      tooltipKey: 'actions.delete',
      color: 'warn',
      callback: (category: any) => this.onDeleteCategory(category)
    }
  ]);

  onAddCategory() {
    const dialogRef = this.dialog.open(CreateCategoryComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.store.createCategory(result);
      }
    });
  }

  onEditCategory(category: any) {
    console.log('Edit category:', category);
    this.snackBar.open(`Édition de ${category.name}`, 'OK', { duration: 2000 });
  }

  onDeleteCategory(category: any) {
    if (confirm(`Êtes-vous sûr de vouloir supprimer "${category.name}" ?`)) {
      this.store.deleteCategory(category.id);
    }
  }
}
