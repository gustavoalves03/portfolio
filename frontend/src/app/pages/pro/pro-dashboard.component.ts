import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { LowerCasePipe, SlicePipe } from '@angular/common';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { AnalyticsService } from '../../features/analytics/analytics.service';
import {
  AnalyticsResponse,
  EmployeeRanking,
  ClientRanking,
  CareRanking,
} from '../../features/analytics/analytics.model';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';

type Period = 'week' | 'month' | 'quarter' | 'year';
type EmployeeSortKey = 'revenue' | 'bookingCount' | 'attendanceRate';
type ClientSortKey = 'visitCount' | 'revenue' | 'attendanceRate';

@Component({
  selector: 'app-pro-dashboard',
  standalone: true,
  imports: [
    MatCardModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatFormFieldModule,
    RouterLink,
    SlicePipe,
    LowerCasePipe,
    TranslocoPipe,
    BaseChartDirective,
  ],
  providers: [DashboardStore, provideCharts(withDefaultRegisterables())],
  templateUrl: './pro-dashboard.component.html',
  styleUrl: './pro-dashboard.component.scss',
})
export class ProDashboardComponent {
  // Existing dashboard store for readiness/publish
  readonly store = inject(DashboardStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly analyticsService = inject(AnalyticsService);

  // Analytics state
  readonly period = signal<Period>('week');
  readonly selectedEmployeeId = signal<number | undefined>(undefined);
  readonly selectedCareId = signal<number | undefined>(undefined);
  readonly analyticsData = signal<AnalyticsResponse | null>(null);
  readonly analyticsLoading = signal(false);
  readonly employeeSortKey = signal<EmployeeSortKey>('revenue');
  readonly clientSortKey = signal<ClientSortKey>('visitCount');
  readonly revenueChartType = signal<'bar' | 'line'>('bar');

  readonly periods: Period[] = ['week', 'month', 'quarter', 'year'];

  readonly salonUrl = computed(() => {
    const readiness = this.store.readiness();
    return readiness ? '/salon/' + readiness.slug : '';
  });

  // KPIs
  readonly kpis = computed(() => {
    const data = this.analyticsData();
    if (!data) return [];
    return [
      {
        key: 'bookings',
        value: String(data.totalBookings),
        trend: data.bookingsTrend,
        icon: 'calendar_today',
        color: 'rose',
      },
      {
        key: 'revenue',
        value: this.formatCurrency(data.totalRevenue),
        trend: data.revenueTrend,
        icon: 'payments',
        color: 'green',
      },
      {
        key: 'attendance',
        value: this.formatPercent(data.attendanceRate),
        trend: data.attendanceTrend,
        icon: 'how_to_reg',
        color: 'blue',
      },
      {
        key: 'occupancy',
        value: this.formatPercent(data.occupancyRate),
        trend: null,
        icon: 'event_seat',
        color: 'orange',
      },
      {
        key: 'avgBasket',
        value: this.formatCurrency(data.avgBasket),
        trend: null,
        icon: 'shopping_bag',
        color: 'purple',
      },
    ];
  });

  // Revenue chart data
  readonly revenueChartData = computed<ChartData>(() => {
    const data = this.analyticsData();
    if (!data) return { labels: [], datasets: [] };
    return {
      labels: data.revenuePerDay.map((p) => this.formatDateLabel(p.date)),
      datasets: [
        {
          data: data.revenuePerDay.map((p) => p.value / 100),
          label: this.transloco.translate('pro.dashboard.analytics.kpi.revenue'),
          backgroundColor: 'rgba(192, 0, 102, 0.2)',
          borderColor: '#c06',
          borderWidth: 2,
          borderRadius: 6,
          pointBackgroundColor: '#c06',
        },
      ],
    };
  });

  readonly revenueChartOptions = computed<ChartConfiguration['options']>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx) => `${(ctx.raw as number).toFixed(2)} \u20AC`,
        },
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          callback: (v) => `${v} \u20AC`,
        },
        grid: { color: 'rgba(0,0,0,0.04)' },
      },
      x: {
        grid: { display: false },
      },
    },
  }));

  // Status doughnut chart data
  readonly statusChartData = computed<ChartData<'doughnut'>>(() => {
    const data = this.analyticsData();
    if (!data) return { labels: [], datasets: [] };
    const statusKeys = Object.keys(data.statusBreakdown);
    const colors: Record<string, string> = {
      CONFIRMED: '#52b788',
      PENDING: '#f9a825',
      CANCELLED: '#ef5350',
      NO_SHOW: '#78909c',
    };
    return {
      labels: statusKeys.map((k) =>
        this.transloco.translate(`pro.dashboard.analytics.status.${k}`)
      ),
      datasets: [
        {
          data: statusKeys.map((k) => data.statusBreakdown[k]),
          backgroundColor: statusKeys.map((k) => colors[k] ?? '#ccc'),
          borderWidth: 0,
        },
      ],
    };
  });

  readonly statusChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '65%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: { padding: 16, usePointStyle: true, pointStyle: 'circle' },
      },
    },
  };

  // Heatmap
  readonly heatmapData = computed(() => {
    const data = this.analyticsData();
    if (!data) return { hours: [] as number[], days: [] as number[], cells: [] as { hour: number; day: number; count: number; opacity: number }[], maxCount: 0 };
    const hours = Object.keys(data.heatmap)
      .map(Number)
      .sort((a, b) => a - b);
    const daysSet = new Set<number>();
    for (const hourData of Object.values(data.heatmap)) {
      for (const day of Object.keys(hourData)) daysSet.add(Number(day));
    }
    const days = [...daysSet].sort((a, b) => a - b);
    let maxCount = 0;
    const cells: { hour: number; day: number; count: number; opacity: number }[] = [];
    for (const hour of hours) {
      for (const day of days) {
        const count = data.heatmap[String(hour)]?.[day] ?? 0;
        if (count > maxCount) maxCount = count;
        cells.push({ hour, day, count, opacity: 0 });
      }
    }
    for (const cell of cells) {
      cell.opacity = maxCount > 0 ? cell.count / maxCount : 0;
    }
    return { hours, days, cells, maxCount };
  });

  // Sorted rankings
  readonly sortedEmployees = computed(() => {
    const data = this.analyticsData();
    if (!data) return [];
    const key = this.employeeSortKey();
    return [...data.employeeRankings].sort((a, b) => b[key] - a[key]);
  });

  readonly sortedClients = computed(() => {
    const data = this.analyticsData();
    if (!data) return [];
    const key = this.clientSortKey();
    return [...data.clientRankings].sort((a, b) => b[key] - a[key]);
  });

  readonly sortedCares = computed(() => {
    const data = this.analyticsData();
    if (!data) return [];
    return [...data.careRankings].sort((a, b) => b.revenue - a.revenue);
  });

  constructor() {
    // Existing effects for publish/unpublish
    effect(() => {
      if (this.store.isActive()) {
        this.store.loadActivity();
      }
    });

    effect(() => {
      if (this.store.publishSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.dashboard.publishSuccess'),
          undefined,
          { duration: 3000 }
        );
      }
    });

    effect(() => {
      if (this.store.unpublishSuccess()) {
        this.snackBar.open(
          this.transloco.translate('pro.dashboard.unpublishSuccess'),
          undefined,
          { duration: 3000 }
        );
      }
    });

    // Fetch analytics when filters change
    effect(() => {
      const period = this.period();
      const employeeId = this.selectedEmployeeId();
      const careId = this.selectedCareId();

      // Only load analytics when salon is active
      if (this.store.isActive()) {
        this.loadAnalytics(period, employeeId, careId);
      }
    });
  }

  private loadAnalytics(period: Period, employeeId?: number, careId?: number): void {
    this.analyticsLoading.set(true);
    this.analyticsService.getAnalytics(period, employeeId, careId).subscribe({
      next: (data) => {
        this.analyticsData.set(data);
        this.analyticsLoading.set(false);
      },
      error: () => {
        this.analyticsLoading.set(false);
      },
    });
  }

  onPublish(): void {
    this.store.publish();
  }

  onUnpublish(): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: this.transloco.translate('pro.dashboard.unpublishConfirmTitle'),
        body: this.transloco.translate('pro.dashboard.unpublishConfirmBody'),
        action: this.transloco.translate('pro.dashboard.unpublishConfirmAction'),
      },
    });

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.store.unpublish();
      }
    });
  }

  setPeriod(p: Period): void {
    this.period.set(p);
  }

  setEmployeeSort(key: EmployeeSortKey): void {
    this.employeeSortKey.set(key);
  }

  setClientSort(key: ClientSortKey): void {
    this.clientSortKey.set(key);
  }

  toggleRevenueChartType(): void {
    this.revenueChartType.update((t) => (t === 'bar' ? 'line' : 'bar'));
  }

  formatCurrency(cents: number): string {
    return (cents / 100).toFixed(2) + ' \u20AC';
  }

  formatPercent(rate: number): string {
    return Math.round(rate * 100) + '%';
  }

  trendArrow(trend: number | null): string {
    if (trend === null) return '';
    return trend >= 0 ? '\u25B2' : '\u25BC';
  }

  trendClass(trend: number | null): string {
    if (trend === null) return '';
    return trend >= 0 ? 'trend-positive' : 'trend-negative';
  }

  trendValue(trend: number | null): string {
    if (trend === null) return '';
    const sign = trend >= 0 ? '+' : '';
    return `${sign}${Math.round(trend)}%`;
  }

  maxEmployeeValue(key: EmployeeSortKey): number {
    const employees = this.sortedEmployees();
    if (employees.length === 0) return 1;
    return Math.max(...employees.map((e) => e[key])) || 1;
  }

  maxCareValue(): number {
    const cares = this.sortedCares();
    if (cares.length === 0) return 1;
    return Math.max(...cares.map((c) => c.revenue)) || 1;
  }

  employeeBarWidth(employee: EmployeeRanking): number {
    const key = this.employeeSortKey();
    return (employee[key] / this.maxEmployeeValue(key)) * 100;
  }

  careBarWidth(care: CareRanking): number {
    return (care.revenue / this.maxCareValue()) * 100;
  }

  clientReliability(client: ClientRanking): number {
    return client.attendanceRate;
  }

  heatmapCellColor(opacity: number): string {
    if (opacity === 0) return '#f5f4f2';
    return `rgba(192, 0, 102, ${0.1 + opacity * 0.8})`;
  }

  dayLabel(day: number): string {
    return this.transloco.translate(`pro.availability.days.${day}`);
  }

  private formatDateLabel(date: string): string {
    // date format: "YYYY-MM-DD" -> "DD/MM"
    const parts = date.split('-');
    if (parts.length === 3) return `${parts[2]}/${parts[1]}`;
    return date;
  }
}
