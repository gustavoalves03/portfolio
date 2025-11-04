import { Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { CategoriesStore } from './store/categories.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TranslocoPipe } from '@jsverse/transloco';
import { CreateCategoryComponent } from './modals/create/create-category.component';

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [CrudTable, TranslocoPipe],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.scss',
  providers: [CategoriesStore],
})
export class CategoriesComponent {
  readonly store = inject(CategoriesStore);
  private dialog = inject(MatDialog);

  displayedColumns: string[] = ['name', 'description'];

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
}
