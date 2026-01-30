import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MatBadgeModule } from '@angular/material/badge';
import { RouterLink } from '@angular/router';
import { BookingsService } from '../../../../features/bookings/services/bookings.service';
import { BookingFilters, CareBookingDetailed, CareBookingStatus } from '../../../../features/bookings/models/bookings.model';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';

type DateFilter = 'all' | 'today' | 'week' | 'month';

@Component({
  selector: 'app-bookings-drawer',
  standalone: true,
  imports: [
    CommonModule,
    MatSidenavModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatCardModule,
    MatBadgeModule,
    RouterLink
  ],
  templateUrl: './bookings-drawer.component.html',
  styleUrl: './bookings-drawer.component.scss'
})
export class BookingsDrawerComponent {
  private readonly bookingsService = inject(BookingsService);

  // Signals
  protected readonly isOpen = signal(false);
  protected readonly bookings = signal<CareBookingDetailed[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly isLoading = signal(false);

  // Filter signals
  protected readonly selectedStatus = signal<CareBookingStatus | undefined>(undefined);
  protected readonly selectedDateFilter = signal<DateFilter>('all');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);

  // Computed filters
  protected readonly filters = computed<BookingFilters>(() => {
    const dateFilter = this.selectedDateFilter();
    const filters: BookingFilters = {};

    if (this.selectedStatus()) {
      filters.status = this.selectedStatus();
    }

    const now = new Date();
    switch (dateFilter) {
      case 'today':
        filters.from = new Date(now.setHours(0, 0, 0, 0)).toISOString();
        filters.to = new Date(now.setHours(23, 59, 59, 999)).toISOString();
        break;
      case 'week':
        const weekStart = new Date(now);
        weekStart.setDate(now.getDate() - now.getDay());
        weekStart.setHours(0, 0, 0, 0);
        filters.from = weekStart.toISOString();
        break;
      case 'month':
        const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
        filters.from = monthStart.toISOString();
        break;
    }

    return filters;
  });

  // Computed badge count (pending bookings)
  protected readonly pendingCount = computed(() =>
    this.bookings().filter(b => b.status === CareBookingStatus.PENDING).length
  );

  // Load bookings with filters and pagination
  protected readonly loadBookings = rxMethod<void>(
    pipe(
      tap(() => this.isLoading.set(true)),
      switchMap(() => {
        const params = {
          page: this.pageIndex(),
          size: this.pageSize(),
          sort: 'createdAt,desc'
        };
        return this.bookingsService.listDetailed(this.filters(), params);
      }),
      tap({
        next: (page) => {
          this.bookings.set(page.content);
          this.totalElements.set(page.totalElements);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
        }
      })
    )
  );

  // Public methods
  toggle(): void {
    this.isOpen.update(v => !v);
    if (this.isOpen()) {
      this.loadBookings();
    }
  }

  close(): void {
    this.isOpen.set(false);
  }

  // Filter actions
  protected setStatusFilter(status: CareBookingStatus | undefined): void {
    this.selectedStatus.set(status);
    this.pageIndex.set(0); // Reset to first page
    this.loadBookings();
  }

  protected setDateFilter(filter: DateFilter): void {
    this.selectedDateFilter.set(filter);
    this.pageIndex.set(0); // Reset to first page
    this.loadBookings();
  }

  protected onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadBookings();
  }

  // Helpers
  protected getStatusLabel(status: CareBookingStatus): string {
    switch (status) {
      case CareBookingStatus.PENDING:
        return 'En attente';
      case CareBookingStatus.CONFIRMED:
        return 'Confirmé';
      case CareBookingStatus.CANCELLED:
        return 'Annulé';
      default:
        return status;
    }
  }

  protected getStatusClass(status: CareBookingStatus): string {
    switch (status) {
      case CareBookingStatus.PENDING:
        return 'status-pending';
      case CareBookingStatus.CONFIRMED:
        return 'status-confirmed';
      case CareBookingStatus.CANCELLED:
        return 'status-cancelled';
      default:
        return '';
    }
  }

  protected formatDate(isoDate: string): string {
    const date = new Date(isoDate);
    return new Intl.DateTimeFormat('fr-FR', {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  }

  protected formatPrice(price: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR'
    }).format(price / 100);
  }

  // Expose enum for template
  protected readonly CareBookingStatus = CareBookingStatus;
}
