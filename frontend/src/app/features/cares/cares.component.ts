import { Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { CaresStore } from './store/cares.store';
import {CrudTable} from '../../shared/uis/crud-table/crud-table';
import { TranslocoPipe } from '@jsverse/transloco';
import {CreateCare} from './modals/create/create-care.component';

@Component({
  selector: 'app-cares',
  standalone: true,
  imports: [CrudTable, TranslocoPipe],
  templateUrl: './cares.component.html',
  styleUrl: './cares.component.scss',
  providers: [CaresStore]
})
export class CaresComponent {
  readonly store = inject(CaresStore);
  private dialog = inject(MatDialog);

  displayedColumns: string[] = [ 'Nom', 'Description', 'Prix', 'Durée', 'Actions'];

  onAddCare() {
    const dialogRef = this.dialog.open(CreateCare, {
      width: '500px',
      disableClose: false,
      autoFocus: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        console.log('Nouveau care:', result);
        // TODO: Appeler le store ou le service pour sauvegarder le care
        // this.store.addCare(result);
      }
    });
  }
}
