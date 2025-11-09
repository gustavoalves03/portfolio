import { Component, effect, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CaresStore } from './store/cares.store';
import { CategoriesStore } from '../categories/store/categories.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TranslocoPipe } from '@jsverse/transloco';
import { CreateCare } from './modals/create/create-care.component';
import { take } from 'rxjs';

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

  displayedColumns: string[] = ['name', 'description', 'price', 'duration'];

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
}
