import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, afterNextRender, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { BookingHistoryStore } from './booking-history.store';
import { CareBookingDetailed, CareBookingStatus } from '../../../features/bookings/models/bookings.model';
import { EmployeesStore } from '../../../features/employees/employees.store';

const ALL_STATUSES: CareBookingStatus[] = [
  CareBookingStatus.CONFIRMED,
  CareBookingStatus.PENDING,
  CareBookingStatus.CANCELLED,
  CareBookingStatus.NO_SHOW,
];

const STATUS_CLASSES: Record<string, string> = {
  CONFIRMED: 's-confirmed',
  PENDING: 's-pending',
  CANCELLED: 's-cancelled',
  NO_SHOW: 's-noshow',
};

type PeriodPreset = '7d' | '30d' | '90d' | '12m';

@Component({
  selector: 'app-pro-booking-history',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  providers: [BookingHistoryStore, EmployeesStore],
  templateUrl: './pro-booking-history.component.html',
  styleUrl: './pro-booking-history.component.scss',
})
export class ProBookingHistoryComponent implements AfterViewInit, OnDestroy {
  protected readonly store = inject(BookingHistoryStore);
  protected readonly employeesStore = inject(EmployeesStore);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslocoService);

  protected readonly statuses = ALL_STATUSES;

  @ViewChild('sentinel') sentinel?: ElementRef<HTMLElement>;
  private observer?: IntersectionObserver;

  // ── Aggregated stats ──────────────────────────────────────────────────
  protected readonly totalBookings = computed(() => this.store.items().length);

  protected readonly totalRevenue = computed(() => {
    const cents = this.store.items()
      .filter((b) =>
        b.status !== CareBookingStatus.CANCELLED && b.status !== CareBookingStatus.NO_SHOW
      )
      .reduce((sum, b) => sum + b.care.price * b.quantity, 0);
    return this.formatPrice(cents);
  });

  protected readonly attendanceRate = computed(() => {
    const items = this.store.items();
    if (items.length === 0) return '—';
    const honored = items.filter(
      (b) => b.status === CareBookingStatus.CONFIRMED
    ).length;
    return Math.round((honored / items.length) * 100) + ' %';
  });

  protected readonly cancelledCount = computed(
    () => this.store.items().filter((b) => b.status === CareBookingStatus.CANCELLED).length
  );

  protected readonly noShowCount = computed(
    () => this.store.items().filter((b) => b.status === CareBookingStatus.NO_SHOW).length
  );

  protected readonly statusCounts = computed(() => {
    const counts: Record<string, number> = {
      CONFIRMED: 0,
      PENDING: 0,
      CANCELLED: 0,
      NO_SHOW: 0,
    };
    for (const b of this.store.items()) {
      counts[b.status] = (counts[b.status] ?? 0) + 1;
    }
    return counts;
  });

  protected readonly periodLabelText = computed(() => {
    const f = this.store.filters();
    const days = this.daysBetween(f.from, f.to);
    return `${this.formatDateLabel(f.from)} → ${this.formatDateLabel(f.to)} · ${days}`;
  });

  protected readonly activePeriodPreset = computed<PeriodPreset | null>(() => {
    const f = this.store.filters();
    const days = this.daysBetween(f.from, f.to, /*asNumber*/ true) as number;
    if (Math.abs(days - 7) < 2) return '7d';
    if (Math.abs(days - 30) < 2) return '30d';
    if (Math.abs(days - 90) < 3) return '90d';
    if (Math.abs(days - 365) < 5) return '12m';
    return null;
  });

  // ── Lifecycle ─────────────────────────────────────────────────────────
  constructor() {
    this.employeesStore.loadEmployees();
    afterNextRender(() => this.setupObserver());
  }

  ngAfterViewInit(): void {
    this.setupObserver();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  private setupObserver(): void {
    if (typeof IntersectionObserver === 'undefined' || !this.sentinel) return;
    this.observer?.disconnect();
    this.observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) {
        this.store.loadNextPage();
      }
    }, { rootMargin: '200px' });
    this.observer.observe(this.sentinel.nativeElement);
  }

  // ── Filters actions ───────────────────────────────────────────────────
  protected setPeriodPreset(preset: PeriodPreset): void {
    const today = new Date();
    const from = new Date(today);
    switch (preset) {
      case '7d':  from.setDate(from.getDate() - 7); break;
      case '30d': from.setDate(from.getDate() - 30); break;
      case '90d': from.setDate(from.getDate() - 90); break;
      case '12m': from.setFullYear(from.getFullYear() - 1); break;
    }
    this.store.updateFilters({ from: this.toYMD(from), to: this.toYMD(today) });
  }

  protected setFromDate(value: string): void {
    if (!value) return;
    this.store.updateFilters({ from: value });
  }

  protected setToDate(value: string): void {
    if (!value) return;
    this.store.updateFilters({ to: value });
  }

  protected isStatusSelected(status: CareBookingStatus): boolean {
    return this.store.filters().statuses.includes(status);
  }

  protected toggleStatus(status: CareBookingStatus): void {
    const current = this.store.filters().statuses;
    const next = current.includes(status)
      ? current.filter((s) => s !== status)
      : [...current, status];
    this.store.updateFilters({ statuses: next });
  }

  protected setEmployee(value: string): void {
    const employeeId = value === '' ? null : Number(value);
    this.store.updateFilters({ employeeId });
  }

  protected resetFilters(): void {
    const today = new Date();
    const from = new Date(today);
    from.setDate(from.getDate() - 30);
    this.store.updateFilters({
      from: this.toYMD(from),
      to: this.toYMD(today),
      statuses: [...ALL_STATUSES],
      employeeId: null,
      clientQuery: '',
    });
  }

  // ── Booking actions ───────────────────────────────────────────────────
  protected openClient(b: CareBookingDetailed): void {
    this.router.navigate(['/pro/clients', b.user.id]);
  }

  // ── Display helpers ───────────────────────────────────────────────────
  protected statusLabel(status: CareBookingStatus): string {
    switch (status) {
      case CareBookingStatus.PENDING:   return this.i18n.translate('drawer.pending');
      case CareBookingStatus.CONFIRMED: return this.i18n.translate('drawer.confirmed');
      case CareBookingStatus.CANCELLED: return this.i18n.translate('drawer.cancelled');
      case CareBookingStatus.NO_SHOW:   return this.i18n.translate('pro.bookings.noShow');
      default: return status;
    }
  }

  protected statusBadgeClass(status: string): string {
    return 'badge-' + (STATUS_CLASSES[status]?.replace('s-', '') ?? 'confirmed');
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

  protected formatDuration(min: number): string {
    if (min < 60) return `${min} min`;
    const h = Math.floor(min / 60);
    const m = min % 60;
    return m > 0 ? `${h} h ${m.toString().padStart(2, '0')}` : `${h} h`;
  }

  protected formatPrice(cents: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
    }).format(cents / 100);
  }

  protected initials(name: string): string {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]?.toUpperCase() ?? '')
      .join('');
  }

  // ── Internal helpers ──────────────────────────────────────────────────
  private toYMD(d: Date): string {
    const y = d.getFullYear();
    const m = (d.getMonth() + 1).toString().padStart(2, '0');
    const day = d.getDate().toString().padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private daysBetween(fromYMD: string, toYMD: string, asNumber = false): string | number {
    const f = this.parseYMD(fromYMD);
    const t = this.parseYMD(toYMD);
    if (!f || !t) return asNumber ? 0 : '';
    const diff = Math.round((t.getTime() - f.getTime()) / 86400000);
    if (asNumber) return diff;
    return this.i18n.translate('pro.history.nDays', { n: diff });
  }

  private formatDateLabel(ymd: string): string {
    const d = this.parseYMD(ymd);
    if (!d) return '';
    const lang = this.i18n.getActiveLang();
    const locale = lang === 'fr' ? 'fr-FR' : 'en-GB';
    return d.toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' });
  }

  private parseYMD(ymd: string): Date | null {
    if (!ymd) return null;
    const [y, m, d] = ymd.split('-').map(Number);
    if (!y || !m || !d) return null;
    return new Date(y, m - 1, d);
  }

  /** Build day-group label "Jeudi 7 mai" with i18n today/yesterday markers. */
  protected dayLabel(ymd: string): { label: string; isToday: boolean } {
    const d = this.parseYMD(ymd);
    if (!d) return { label: ymd, isToday: false };
    const todayStr = this.toYMD(new Date());
    const yesterdayDate = new Date();
    yesterdayDate.setDate(yesterdayDate.getDate() - 1);
    const yesterdayStr = this.toYMD(yesterdayDate);

    const lang = this.i18n.getActiveLang();
    const locale = lang === 'fr' ? 'fr-FR' : 'en-GB';
    const dayName = d.toLocaleDateString(locale, { weekday: 'long' });
    const dayNum = d.getDate();
    const monthName = d.toLocaleDateString(locale, { month: 'long' });
    const formatted = `${dayName.charAt(0).toUpperCase() + dayName.slice(1)} ${dayNum} ${monthName}`;

    if (ymd === todayStr) {
      return {
        label: `${formatted} · ${this.i18n.translate('pro.bookings.today')}`,
        isToday: true,
      };
    }
    if (ymd === yesterdayStr) {
      return {
        label: `${formatted} · ${this.i18n.translate('pro.history.yesterday')}`,
        isToday: false,
      };
    }
    return { label: formatted, isToday: false };
  }

  /** Sum of bookings in a day group (for the day-row meta). */
  protected daySum(bookings: CareBookingDetailed[]): { count: number; revenue: string } {
    const cents = bookings
      .filter((b) =>
        b.status !== CareBookingStatus.CANCELLED && b.status !== CareBookingStatus.NO_SHOW
      )
      .reduce((sum, b) => sum + b.care.price * b.quantity, 0);
    return {
      count: bookings.length,
      revenue: this.formatPrice(cents),
    };
  }
}
