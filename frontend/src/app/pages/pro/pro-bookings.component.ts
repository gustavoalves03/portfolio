import { Component, ViewContainerRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';
import { BookingsService } from '../../features/bookings/services/bookings.service';
import { toYMD } from '../../core/utils/date-format';
import {
  BookingFilters,
  CareBookingDetailed,
  CareBookingStatus,
} from '../../features/bookings/models/bookings.model';
import { BookingStepperComponent } from '../../features/bookings/components/booking-stepper/booking-stepper.component';
import { bottomSheetConfig } from '../../shared/uis/sheet-handle/bottom-sheet.config';

interface DayCell {
  date: string;          // YYYY-MM-DD
  dayOfMonth: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  isInWeek: boolean;     // currently visible week
  hasBookings: boolean;
  busy: boolean;         // 5+ bookings
  isClosed: boolean;     // weekly closed (sunday for now)
}

interface WeekDay {
  date: string;          // YYYY-MM-DD
  dayOfMonth: number;
  dayName: string;       // 'Lun', 'Mar', ...
  isToday: boolean;
  isClosed: boolean;
  bookings: CareBookingDetailed[];
}

/** Hour range shown in the week grid. Matches typical salon opening hours. */
const GRID_START_HOUR = 8;
const GRID_END_HOUR = 20; // exclusive
const HOUR_HEIGHT_PX = 56;

const STATUS_COLORS: Record<string, string> = {
  CONFIRMED: 'confirmed',
  PENDING: 'pending',
  CANCELLED: 'cancelled',
  NO_SHOW: 'noshow',
};

@Component({
  selector: 'app-pro-bookings',
  standalone: true,
  imports: [MatIconModule, MatProgressSpinnerModule, TranslocoPipe],
  templateUrl: './pro-bookings.component.html',
  styleUrl: './pro-bookings.component.scss',
})
export class ProBookingsComponent {
  private readonly bookingsService = inject(BookingsService);
  private readonly i18n = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  // Required so dialogs inherit this component's injector chain (notably
  // DashboardStore from ProShellComponent, which CaresStore depends on).
  private readonly viewContainerRef = inject(ViewContainerRef);

  protected readonly CareBookingStatus = CareBookingStatus;
  protected readonly hourLabels = Array.from(
    { length: GRID_END_HOUR - GRID_START_HOUR },
    (_, i) => GRID_START_HOUR + i
  );
  protected readonly hourHeight = HOUR_HEIGHT_PX;

  // ── State ──────────────────────────────────────────────────────────────
  protected readonly bookings = signal<CareBookingDetailed[]>([]);
  protected readonly isLoading = signal(false);

  /** Monday of the currently visible week. */
  protected readonly currentWeekStart = signal<Date>(this.startOfWeek(new Date()));

  /** Anchor for the mini calendar (month view); follows currentWeekStart by default. */
  protected readonly currentMonth = signal<Date>(this.startOfMonth(new Date()));

  // ── Derived: days of the visible week ──────────────────────────────────
  protected readonly weekDays = computed<WeekDay[]>(() => {
    const start = this.currentWeekStart();
    const todayStr = toYMD(new Date());
    const lang = this.i18n.getActiveLang();
    const locale = lang === 'fr' ? 'fr-FR' : 'en-GB';
    const allBookings = this.bookings();

    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(d.getDate() + i);
      const ymd = toYMD(d);
      const dayBookings = allBookings.filter((b) => b.appointmentDate === ymd);
      const isClosed = d.getDay() === 0; // Sunday closed by default
      return {
        date: ymd,
        dayOfMonth: d.getDate(),
        dayName: d.toLocaleDateString(locale, { weekday: 'short' }),
        isToday: ymd === todayStr,
        isClosed,
        bookings: dayBookings.sort((a, b) =>
          a.appointmentTime.localeCompare(b.appointmentTime)
        ),
      };
    });
  });

  protected readonly weekLabel = computed(() => {
    const start = this.currentWeekStart();
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    const lang = this.i18n.getActiveLang();
    const locale = lang === 'fr' ? 'fr-FR' : 'en-GB';
    const startDay = start.getDate();
    const endDay = end.getDate();
    const month = end.toLocaleDateString(locale, { month: 'long' });
    const year = end.getFullYear();
    if (start.getMonth() === end.getMonth()) {
      return `${startDay} – ${endDay} ${month} ${year}`;
    }
    const startMonth = start.toLocaleDateString(locale, { month: 'short' });
    return `${startDay} ${startMonth} – ${endDay} ${month} ${year}`;
  });

  protected readonly weekNumber = computed(() => {
    const d = new Date(this.currentWeekStart());
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() + 4 - (d.getDay() || 7));
    const yearStart = new Date(d.getFullYear(), 0, 1);
    return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  });

  // ── Mini calendar ──────────────────────────────────────────────────────
  protected readonly monthLabel = computed(() => {
    const lang = this.i18n.getActiveLang();
    const locale = lang === 'fr' ? 'fr-FR' : 'en-GB';
    return this.currentMonth().toLocaleDateString(locale, {
      month: 'long',
      year: 'numeric',
    });
  });

  protected readonly miniCalDays = computed<DayCell[]>(() => {
    const month = this.currentMonth();
    const firstOfMonth = this.startOfMonth(month);
    const firstDay = (firstOfMonth.getDay() || 7) - 1; // Mon=0, Sun=6
    const start = new Date(firstOfMonth);
    start.setDate(start.getDate() - firstDay);

    const todayStr = toYMD(new Date());
    const weekStart = this.currentWeekStart();
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 6);
    const weekStartStr = toYMD(weekStart);
    const weekEndStr = toYMD(weekEnd);
    const allBookings = this.bookings();
    const bookingsByDate = new Map<string, number>();
    for (const b of allBookings) {
      bookingsByDate.set(b.appointmentDate, (bookingsByDate.get(b.appointmentDate) ?? 0) + 1);
    }

    return Array.from({ length: 42 }, (_, i) => {
      const d = new Date(start);
      d.setDate(d.getDate() + i);
      const ymd = toYMD(d);
      const count = bookingsByDate.get(ymd) ?? 0;
      return {
        date: ymd,
        dayOfMonth: d.getDate(),
        isCurrentMonth: d.getMonth() === month.getMonth(),
        isToday: ymd === todayStr,
        isInWeek: ymd >= weekStartStr && ymd <= weekEndStr,
        hasBookings: count > 0,
        busy: count >= 5,
        isClosed: d.getDay() === 0,
      };
    });
  });

  // ── Stats for the week (KPIs) ──────────────────────────────────────────
  private readonly weekBookings = computed(() =>
    this.weekDays().flatMap((d) => d.bookings)
  );

  private readonly nonCancelledBookings = computed(() =>
    this.weekBookings().filter((b) => b.status !== CareBookingStatus.CANCELLED)
  );

  protected readonly totalBookings = computed(() => this.nonCancelledBookings().length);

  protected readonly estimatedRevenue = computed(() => {
    const total = this.weekBookings()
      .filter(
        (b) => b.status !== CareBookingStatus.CANCELLED && b.status !== CareBookingStatus.NO_SHOW
      )
      .reduce((sum, b) => sum + b.care.price * b.quantity, 0);
    return this.formatPrice(total);
  });

  protected readonly occupiedTime = computed(() => {
    const totalMin = this.nonCancelledBookings().reduce(
      (sum, b) => sum + b.care.duration,
      0
    );
    const h = Math.floor(totalMin / 60);
    const m = totalMin % 60;
    if (h === 0) return `${m}min`;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  });

  protected readonly statusCounts = computed<Record<string, number>>(() => {
    const counts: Record<string, number> = {
      CONFIRMED: 0,
      PENDING: 0,
      CANCELLED: 0,
      NO_SHOW: 0,
    };
    for (const b of this.weekBookings()) {
      counts[b.status] = (counts[b.status] ?? 0) + 1;
    }
    return counts;
  });

  protected readonly statusFilters = signal<Record<string, boolean>>({
    CONFIRMED: true,
    PENDING: true,
    CANCELLED: true,
    NO_SHOW: true,
  });

  // ── Now line position ─────────────────────────────────────────────────
  protected readonly nowOffsetPx = computed(() => {
    const now = new Date();
    const minutes = (now.getHours() - GRID_START_HOUR) * 60 + now.getMinutes();
    if (minutes < 0 || minutes > (GRID_END_HOUR - GRID_START_HOUR) * 60) return null;
    return (minutes / 60) * HOUR_HEIGHT_PX;
  });

  // ── Booking position helpers ──────────────────────────────────────────
  protected bookingTopPx(b: CareBookingDetailed): number {
    const [h, m] = b.appointmentTime.split(':').map(Number);
    const minutes = (h - GRID_START_HOUR) * 60 + m;
    return (minutes / 60) * HOUR_HEIGHT_PX;
  }

  protected bookingHeightPx(b: CareBookingDetailed): number {
    return Math.max(20, (b.care.duration / 60) * HOUR_HEIGHT_PX);
  }

  protected isVisible(b: CareBookingDetailed): boolean {
    return this.statusFilters()[b.status] === true;
  }

  protected statusClass(status: string): string {
    return 's-' + (STATUS_COLORS[status] ?? 'confirmed');
  }

  // ── Loaders ───────────────────────────────────────────────────────────
  private readonly loadBookings = rxMethod<void>(
    pipe(
      tap(() => this.isLoading.set(true)),
      switchMap(() => {
        const filters = this.buildFilters();
        return this.bookingsService.listDetailed(filters, {
          page: 0,
          size: 200,
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

  // ── Navigation ────────────────────────────────────────────────────────
  protected prevWeek(): void {
    const d = new Date(this.currentWeekStart());
    d.setDate(d.getDate() - 7);
    this.currentWeekStart.set(d);
    this.currentMonth.set(this.startOfMonth(d));
    this.loadBookings();
  }

  protected nextWeek(): void {
    const d = new Date(this.currentWeekStart());
    d.setDate(d.getDate() + 7);
    this.currentWeekStart.set(d);
    this.currentMonth.set(this.startOfMonth(d));
    this.loadBookings();
  }

  protected goToday(): void {
    const today = new Date();
    this.currentWeekStart.set(this.startOfWeek(today));
    this.currentMonth.set(this.startOfMonth(today));
    this.loadBookings();
  }

  protected jumpToDate(ymd: string): void {
    const [y, m, d] = ymd.split('-').map(Number);
    const date = new Date(y, m - 1, d);
    this.currentWeekStart.set(this.startOfWeek(date));
    this.currentMonth.set(this.startOfMonth(date));
    this.loadBookings();
  }

  protected prevMonth(): void {
    const d = new Date(this.currentMonth());
    d.setMonth(d.getMonth() - 1);
    this.currentMonth.set(d);
  }

  protected nextMonth(): void {
    const d = new Date(this.currentMonth());
    d.setMonth(d.getMonth() + 1);
    this.currentMonth.set(d);
  }

  protected toggleStatusFilter(status: string): void {
    this.statusFilters.update((f) => ({ ...f, [status]: !f[status] }));
  }

  // ── Actions ───────────────────────────────────────────────────────────
  protected onAddBooking(): void {
    const dialogRef = this.dialog.open(BookingStepperComponent, bottomSheetConfig({
      viewContainerRef: this.viewContainerRef,
      maxHeight: '90vh',
      disableClose: false,
      autoFocus: true,
    }));

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.loadBookings();
      }
    });
  }

  protected openClient(userId: number): void {
    this.router.navigate(['/pro/clients', userId]);
  }

  protected openBooking(b: CareBookingDetailed): void {
    this.openClient(b.user.id);
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

  // ── Internals ─────────────────────────────────────────────────────────
  private buildFilters(): BookingFilters {
    // Load a window around the visible week so the mini cal can show dots
    // for adjacent weeks too.
    const start = new Date(this.currentWeekStart());
    start.setDate(start.getDate() - 14);
    const end = new Date(this.currentWeekStart());
    end.setDate(end.getDate() + 35);
    return {
      from: toYMD(start),
      to: toYMD(end),
    };
  }

  /** Monday 00:00 of the week containing `date`. */
  private startOfWeek(date: Date): Date {
    const d = new Date(date);
    const day = d.getDay() || 7; // 1..7
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() - (day - 1));
    return d;
  }

  /** First day 00:00 of the month containing `date`. */
  private startOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }
}
