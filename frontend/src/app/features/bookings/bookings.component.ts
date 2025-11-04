import { Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { BookingsStore } from './store/bookings.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { CreateBookingComponent } from './modals/create/create-booking.component';

@Component({
  selector: 'app-bookings',
  standalone: true,
  imports: [CrudTable],
  templateUrl: './bookings.component.html',
  styleUrl: './bookings.component.scss',
  providers: [BookingsStore],
})
export class BookingsComponent {
  readonly store = inject(BookingsStore);
  private dialog = inject(MatDialog);

  displayedColumns: string[] = ['id', 'userId', 'careId', 'quantity', 'status', 'createdAt'];

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
}
