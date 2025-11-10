import { Component, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BookingsStore } from './store/bookings.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TableColumn, TableAction } from '../../shared/uis/crud-table/crud-table.models';
import { TranslocoPipe } from '@jsverse/transloco';
import { CreateBookingComponent } from './modals/create/create-booking.component';

@Component({
  selector: 'app-bookings',
  standalone: true,
  imports: [CrudTable, TranslocoPipe, MatSnackBarModule],
  templateUrl: './bookings.component.html',
  styleUrl: './bookings.component.scss',
  providers: [BookingsStore],
})
export class BookingsComponent {
  readonly store = inject(BookingsStore);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  // Configuration des colonnes avec traduction
  readonly columns = signal<TableColumn[]>([
    { key: 'id', headerKey: 'bookings.columns.id', align: 'center' },
    { key: 'userId', headerKey: 'bookings.columns.userId', align: 'left' },
    { key: 'careId', headerKey: 'bookings.columns.careId', align: 'left' },
    { key: 'quantity', headerKey: 'bookings.columns.quantity', align: 'center' },
    { key: 'status', headerKey: 'bookings.columns.status', align: 'center' },
    { key: 'createdAt', headerKey: 'bookings.columns.createdAt', align: 'center' }
  ]);

  // Configuration des actions
  readonly actions = signal<TableAction[]>([
    {
      icon: 'edit',
      tooltipKey: 'actions.edit',
      color: 'primary',
      callback: (booking: any) => this.onEditBooking(booking)
    },
    {
      icon: 'delete',
      tooltipKey: 'actions.delete',
      color: 'warn',
      callback: (booking: any) => this.onDeleteBooking(booking)
    }
  ]);

  onAddBooking() {
    const dialogRef = this.dialog.open(CreateBookingComponent, {
      width: '600px',
      disableClose: false,
      autoFocus: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.store.createBooking(result);
      }
    });
  }

  onEditBooking(booking: any) {
    console.log('Edit booking:', booking);
    this.snackBar.open(`Édition de la réservation #${booking.id}`, 'OK', { duration: 2000 });
  }

  onDeleteBooking(booking: any) {
    if (confirm(`Êtes-vous sûr de vouloir supprimer la réservation #${booking.id} ?`)) {
      this.store.deleteBooking(booking.id);
    }
  }
}
