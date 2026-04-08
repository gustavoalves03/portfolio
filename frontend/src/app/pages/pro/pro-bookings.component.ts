import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { BookingsService } from '../../features/bookings/services/bookings.service';
import {
  BookingFilters,
  CareBookingDetailed,
  CareBookingStatus,
} from '../../features/bookings/models/bookings.model';

type PeriodFilter = 'today' | 'week' | 'month';

interface DayGroup {
  date: string;
  label: string;
  isToday: boolean;
  isTomorrow: boolean;
  bookings: CareBookingDetailed[];
}

@Component({
  selector: 'app-pro-bookings',
  standalone: true,
  imports: [MatIconModule, MatProgressSpinnerModule, TranslocoPipe],
  template: `
    <div class="bookings-page">
      <h1 class="page-title">{{ 'pro.bookings.title' | transloco }}</h1>

      <!-- Period pills -->
      <div class="period-pills">
        <button
          class="period-pill"
          [class.active]="selectedPeriod() === 'today'"
          (click)="setPeriod('today')"
        >
          {{ 'pro.bookings.today' | transloco }}
        </button>
        <button
          class="period-pill"
          [class.active]="selectedPeriod() === 'week'"
          (click)="setPeriod('week')"
        >
          {{ 'pro.bookings.thisWeek' | transloco }}
        </button>
        <button
          class="period-pill"
          [class.active]="selectedPeriod() === 'month'"
          (click)="setPeriod('month')"
        >
          {{ 'pro.bookings.thisMonth' | transloco }}
        </button>
      </div>

      <!-- Summary bar -->
      <div class="summary-bar">
        <div class="summary-item">
          <span class="summary-value">{{ totalBookings() }}</span>
          <span class="summary-label">RDV</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ estimatedRevenue() }}</span>
          <span class="summary-label">{{ 'pro.bookings.estimated' | transloco }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ occupiedTime() }}</span>
          <span class="summary-label">{{ 'pro.bookings.occupied' | transloco }}</span>
        </div>
      </div>

      <!-- Loading -->
      @if (isLoading()) {
        <div class="loading-container">
          <mat-spinner diameter="40"></mat-spinner>
        </div>
      }

      <!-- Content -->
      @if (!isLoading()) {
        @if (dayGroups().length === 0) {
          <div class="empty-state">
            <mat-icon class="empty-icon">event_busy</mat-icon>
            <p class="empty-text">{{ 'pro.bookings.empty' | transloco }}</p>
          </div>
        } @else {
          @for (group of dayGroups(); track group.date) {
            <div class="day-group">
              <div class="day-header" [class.today]="group.isToday" [class.other]="!group.isToday">
                <span
                  class="day-dot"
                  [class.today]="group.isToday"
                  [class.other]="!group.isToday"
                ></span>
                {{ group.label }}
              </div>

              <div
                class="day-timeline"
                [class.other]="!group.isToday"
              >
                @for (booking of group.bookings; track booking.id) {
                  <div class="booking-row" [id]="'booking-' + booking.id" (click)="openClient(booking.user.id)">
                    <div class="booking-time">
                      <div class="booking-time-value">{{ formatTime(booking.appointmentTime) }}</div>
                      <div class="booking-time-duration">{{ booking.care.duration }}min</div>
                    </div>
                    <div
                      class="booking-card"
                      [class.status-confirmed]="booking.status === CareBookingStatus.CONFIRMED"
                      [class.status-pending]="booking.status === CareBookingStatus.PENDING"
                      [class.status-cancelled]="booking.status === CareBookingStatus.CANCELLED"
                      [class.status-no_show]="booking.status === CareBookingStatus.NO_SHOW"
                    >
                      <div class="card-top">
                        <span class="care-name">{{ booking.care.name }}</span>
                        <span
                          class="status-badge"
                          [class.status-confirmed]="booking.status === CareBookingStatus.CONFIRMED"
                          [class.status-pending]="booking.status === CareBookingStatus.PENDING"
                          [class.status-cancelled]="booking.status === CareBookingStatus.CANCELLED"
                          [class.status-no_show]="booking.status === CareBookingStatus.NO_SHOW"
                        >
                          {{ getStatusLabel(booking.status) }}
                        </span>
                      </div>
                      <div class="card-people">
                        <span class="client-name">{{ booking.user.name }}</span>
                        @if (booking.employeeName) {
                          <span class="people-sep">&middot;</span>
                          <span class="employee-name">{{ booking.employeeName }}</span>
                        }
                      </div>
                      <div class="card-bottom">
                        <span class="card-price">{{ formatPrice(booking.care.price) }}</span>
                        <span class="card-time-range">
                          {{ formatTime(booking.appointmentTime) }} — {{ formatEndTime(booking.appointmentTime, booking.care.duration) }}
                        </span>
                      </div>
                    </div>
                  </div>
                }
              </div>
            </div>
          }
        }
      }
    </div>
  `,
  styles: `
    .bookings-page {
      background: #f5f4f2;
      padding: 16px;
      max-width: 800px;
      margin: 0 auto;
    }

    .page-title {
      font-size: 18px;
      font-weight: 600;
      color: #333;
      margin: 0 0 16px;
    }

    /* Period pills */
    .period-pills {
      display: flex;
      gap: 4px;
      margin-bottom: 12px;
    }

    .period-pill {
      padding: 5px 14px;
      border-radius: 8px;
      border: none;
      font-size: 11px;
      font-weight: 500;
      cursor: pointer;
      transition: all 150ms;
      background: #fff;
      color: #888;
      border: 1px solid #e0e0e0;
    }

    .period-pill.active {
      background: #c06;
      color: #fff;
      border-color: #c06;
    }

    /* Summary bar */
    .summary-bar {
      background: #fff;
      border-radius: 10px;
      padding: 10px 14px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      display: flex;
      justify-content: space-around;
      text-align: center;
      margin-bottom: 16px;
    }

    .summary-item {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .summary-value {
      font-size: 18px;
      font-weight: 700;
      color: #333;
    }

    .summary-label {
      font-size: 10px;
      color: #999;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    /* Loading */
    .loading-container {
      display: flex;
      justify-content: center;
      padding: 48px 0;
    }

    /* Empty state */
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 48px 0;
      color: #999;
    }

    .empty-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
    }

    .empty-text {
      font-size: 14px;
      margin: 0;
    }

    /* Day groups */
    .day-group {
      margin-bottom: 16px;
    }

    .day-header {
      font-size: 12px;
      font-weight: 600;
      margin-bottom: 8px;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .day-header.today {
      color: #c06;
    }

    .day-header.other {
      color: #888;
    }

    .day-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .day-dot.today {
      background: #c06;
    }

    .day-dot.other {
      background: #ccc;
    }

    /* Timeline */
    .day-timeline {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding-left: 12px;
      border-left: 2px solid #f0e0e8;
    }

    .day-timeline.other {
      border-left-color: #e8e8e8;
    }

    /* Booking row */
    .booking-row {
      display: flex;
      gap: 10px;
      align-items: stretch;
      cursor: pointer;
    }

    .booking-time {
      width: 44px;
      text-align: right;
      flex-shrink: 0;
      padding-top: 10px;
    }

    .booking-time-value {
      font-size: 16px;
      font-weight: 700;
      color: #333;
      line-height: 1;
    }

    .booking-time-duration {
      font-size: 8px;
      color: #888;
    }

    /* Booking card */
    .booking-card {
      flex: 1;
      background: #fff;
      border-radius: 10px;
      padding: 10px 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      border-left: 3px solid #ccc;
    }

    .booking-card.status-confirmed {
      border-left-color: #52b788;
    }

    .booking-card.status-pending {
      border-left-color: #fb923c;
    }

    .booking-card.status-cancelled {
      border-left-color: #ef5350;
    }

    .booking-card.status-no_show {
      border-left-color: #999;
    }

    .card-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;
    }

    .care-name {
      font-size: 14px;
      font-weight: 600;
      color: #333;
    }

    .card-people {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 12px;
      color: #888;
      margin-bottom: 6px;
    }

    .client-name {
      color: #555;
      font-weight: 500;
    }

    .people-sep {
      color: #ccc;
    }

    .employee-name {
      color: #c06;
      font-weight: 500;
    }

    .card-bottom {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .card-price {
      font-size: 13px;
      font-weight: 600;
      color: #333;
    }

    .card-time-range {
      font-size: 10px;
      color: #aaa;
    }

    /* Status badge */
    .status-badge {
      padding: 2px 8px;
      border-radius: 6px;
      font-size: 9px;
      font-weight: 500;
    }

    .status-badge.status-confirmed {
      background: #f0fdf4;
      color: #166534;
    }

    .status-badge.status-pending {
      background: #fff7ed;
      color: #c2410c;
    }

    .status-badge.status-cancelled {
      background: #fef2f2;
      color: #dc2626;
    }

    .status-badge.status-no_show {
      background: #f5f5f5;
      color: #666;
    }

    /* Highlight fade animation after navigation from notification */
    .highlight-fade {
      background-color: #fdf2f8 !important;
      transition: background-color 2s ease-out;
    }
  `,
})
export class ProBookingsComponent {
  private readonly bookingsService = inject(BookingsService);
  private readonly i18n = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly CareBookingStatus = CareBookingStatus;

  protected readonly bookings = signal<CareBookingDetailed[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly selectedPeriod = signal<PeriodFilter>('today');

  /** Computed: group bookings by appointmentDate */
  protected readonly dayGroups = computed<DayGroup[]>(() => {
    const all = this.bookings();
    if (!all.length) return [];

    const grouped = new Map<string, CareBookingDetailed[]>();
    for (const b of all) {
      const date = b.appointmentDate;
      if (!grouped.has(date)) grouped.set(date, []);
      grouped.get(date)!.push(b);
    }

    // Sort each group by appointmentTime
    for (const [, list] of grouped) {
      list.sort((a, b) => a.appointmentTime.localeCompare(b.appointmentTime));
    }

    // Sort days chronologically
    const sortedDates = [...grouped.keys()].sort();

    const todayStr = this.toLocalDateString(new Date());
    const tomorrowDate = new Date();
    tomorrowDate.setDate(tomorrowDate.getDate() + 1);
    const tomorrowStr = this.toLocalDateString(tomorrowDate);

    return sortedDates.map((date) => {
      const isToday = date === todayStr;
      const isTomorrow = date === tomorrowStr;

      return {
        date,
        label: this.buildDayLabel(date, isToday, isTomorrow),
        isToday,
        isTomorrow,
        bookings: grouped.get(date)!,
      };
    });
  });

  /** Computed: total bookings count */
  protected readonly totalBookings = computed(() => this.bookings().length);

  /** Computed: estimated revenue (sum of care.price in cents, formatted) */
  protected readonly estimatedRevenue = computed(() => {
    const total = this.bookings().reduce((sum, b) => sum + b.care.price, 0);
    return this.formatPrice(total);
  });

  /** Computed: occupied time formatted as "3h15" or "2h" */
  protected readonly occupiedTime = computed(() => {
    const totalMin = this.bookings().reduce((sum, b) => sum + b.care.duration, 0);
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    if (h === 0) return `${m}min`;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  });

  private readonly loadBookings = rxMethod<void>(
    pipe(
      tap(() => this.isLoading.set(true)),
      switchMap(() => {
        const filters = this.buildFilters();
        return this.bookingsService.listDetailed(filters, {
          page: 0,
          size: 100,
          sort: 'appointmentDate,asc',
        });
      }),
      tap({
        next: (page) => {
          this.bookings.set(page.content);
          this.isLoading.set(false);
        },
        error: () => {
          this.isLoading.set(false);
        },
      })
    )
  );

  constructor() {
    this.loadBookings();

    this.route.queryParams.subscribe((params) => {
      const id = params['highlight'];
      if (id) {
        setTimeout(() => {
          const el = document.getElementById('booking-' + id);
          if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            el.classList.add('highlight-fade');
            setTimeout(() => {
              el.style.backgroundColor = 'transparent';
            }, 100);
          }
        }, 500);
      }
    });
  }

  protected setPeriod(period: PeriodFilter): void {
    this.selectedPeriod.set(period);
    this.loadBookings();
  }

  protected openClient(userId: number): void {
    this.router.navigate(['/pro/clients', userId]);
  }

  protected getStatusLabel(status: CareBookingStatus): string {
    switch (status) {
      case CareBookingStatus.PENDING:
        return this.i18n.translate('drawer.pending');
      case CareBookingStatus.CONFIRMED:
        return this.i18n.translate('drawer.confirmed');
      case CareBookingStatus.CANCELLED:
        return this.i18n.translate('drawer.cancelled');
      case CareBookingStatus.NO_SHOW:
        return this.i18n.translate('pro.bookings.noShow');
      default:
        return status;
    }
  }

  protected formatTime(time: string): string {
    // time is "HH:mm:ss" or "HH:mm"
    const [h, m] = time.split(':');
    return `${h}:${m}`;
  }

  protected formatEndTime(startTime: string, durationMin: number): string {
    const [h, m] = startTime.split(':').map(Number);
    const totalMin = h * 60 + m + durationMin;
    const endH = Math.floor(totalMin / 60) % 24;
    const endM = totalMin % 60;
    return `${endH.toString().padStart(2, '0')}:${endM.toString().padStart(2, '0')}`;
  }

  protected formatPrice(price: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
    }).format(price / 100);
  }

  private buildFilters(): BookingFilters {
    const today = new Date();
    const from = this.toLocalDateString(today);

    let to: string;
    switch (this.selectedPeriod()) {
      case 'today': {
        const d = new Date(today);
        d.setDate(d.getDate() + 1);
        to = this.toLocalDateString(d);
        break;
      }
      case 'week': {
        const d = new Date(today);
        d.setDate(d.getDate() + 7);
        to = this.toLocalDateString(d);
        break;
      }
      case 'month': {
        const d = new Date(today);
        d.setDate(d.getDate() + 30);
        to = this.toLocalDateString(d);
        break;
      }
    }

    return { from, to };
  }

  private buildDayLabel(dateStr: string, isToday: boolean, isTomorrow: boolean): string {
    // Parse as local date (dateStr is YYYY-MM-DD)
    const [y, m, d] = dateStr.split('-').map(Number);
    const date = new Date(y, m - 1, d);

    const lang = this.i18n.getActiveLang();
    const locale = lang === 'fr' ? 'fr-FR' : 'en-GB';

    const dayName = date.toLocaleDateString(locale, { weekday: 'long' });
    const dayNum = date.getDate();
    const monthName = date.toLocaleDateString(locale, { month: 'long' });

    const formatted =
      `${dayName.charAt(0).toUpperCase() + dayName.slice(1)} ${dayNum} ${monthName}`;

    if (isToday) {
      const todayLabel = this.i18n.translate('pro.bookings.today');
      return `${todayLabel} — ${formatted}`;
    }
    if (isTomorrow) {
      const tomorrowLabel = this.i18n.translate('pro.bookings.tomorrow');
      return `${tomorrowLabel} — ${formatted}`;
    }
    return formatted;
  }

  /** Format a Date to YYYY-MM-DD string in local timezone */
  private toLocalDateString(date: Date): string {
    const y = date.getFullYear();
    const m = (date.getMonth() + 1).toString().padStart(2, '0');
    const d = date.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
