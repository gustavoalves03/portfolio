import { Component, effect, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { CaresStore } from './store/cares.store';
import { CategoriesStore } from '../categories/store/categories.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TableColumn, TableAction } from '../../shared/uis/crud-table/crud-table.models';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CreateCare } from './modals/create/create-care.component';
import { Care, CareStatus } from './models/cares.model';
import { DeleteCareComponent } from './modals/delete/delete-care.component';

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
  private i18n = inject(TranslocoService);

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

  onEditCare(care: Care) {
    const dialogRef = this.dialog.open(CreateCare, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
      data: {
        categories: this.categoriesStore.categories(),
        care
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        const payload = {
          ...result,
          categoryId: Number(result.categoryId ?? care.category.id)
        };
        this.store.updateCare({ id: care.id, payload });
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
    this.dialog.open(CreateCare, {
      width: '500px',
      disableClose: false,
      autoFocus: false,
      data: {
        care,
        categories: this.categoriesStore.categories(),
        viewOnly: true
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
