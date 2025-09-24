import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { BookingsStore } from './store/bookings.store';

@Component({
  selector: 'app-bookings',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './bookings.component.html',
  styleUrl: './bookings.component.scss',
  providers: [BookingsStore],
})
export class BookingsComponent {
  readonly store = inject(BookingsStore);
}
