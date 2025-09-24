import { Component, OnInit, inject, signal } from '@angular/core';
import { AsyncPipe, DatePipe, NgForOf, NgIf } from '@angular/common';
import { BookingsService } from './services/bookings.service';
import { CareBooking } from './models/bookings.model';

@Component({
  selector: 'app-bookings',
  standalone: true,
  imports: [NgIf, NgForOf, AsyncPipe, DatePipe],
  templateUrl: './bookings.html',
  styleUrl: './bookings.scss',
})
export class Bookings implements OnInit {
  private readonly service = inject(BookingsService);
  readonly bookings = signal<CareBooking[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.service.list({ page: 0, size: 50 }).subscribe({
      next: (page) => {
        this.bookings.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? "Erreur de chargement des réservations");
        this.loading.set(false);
      },
    });
  }
}

