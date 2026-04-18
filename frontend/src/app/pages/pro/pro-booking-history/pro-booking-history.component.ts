import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, afterNextRender, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { BookingHistoryStore } from './booking-history.store';
import { BookingCardComponent } from '../../../features/bookings/components/booking-card/booking-card.component';
import { CareBookingDetailed, CareBookingStatus } from '../../../features/bookings/models/bookings.model';
import { bottomSheetConfig } from '../../../shared/uis/sheet-handle/bottom-sheet.config';
import { PeriodFilterSheetComponent, PeriodResult } from './filters/period-filter-sheet.component';
import { StatusFilterSheetComponent } from './filters/status-filter-sheet.component';
import { EmployeeFilterSheetComponent } from './filters/employee-filter-sheet.component';

@Component({
  selector: 'app-pro-booking-history',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
    BookingCardComponent,
  ],
  providers: [BookingHistoryStore],
  template: `
    <div class="history-page">
      <header class="page-header">
        <button class="back" (click)="goBack()"><mat-icon>arrow_back</mat-icon></button>
        <h1>{{ 'pro.history.title' | transloco }}</h1>
      </header>

      <div class="search-box">
        <mat-icon>search</mat-icon>
        <input
          type="text"
          [placeholder]="'pro.history.search' | transloco"
          [ngModel]="store.filters().clientQuery"
          (ngModelChange)="store.searchClient($event)"
        />
      </div>

      <div class="filters-row">
        <button class="chip" (click)="openPeriod()">{{ periodLabel() }}</button>
        <button class="chip" (click)="openStatus()">{{ statusLabel() }}</button>
        <button class="chip" (click)="openEmployee()">{{ employeeLabel() }}</button>
      </div>

      @if (store.isPending() && store.items().length === 0) {
        <div class="loading"><mat-spinner diameter="32" /></div>
      } @else if (store.emptyState()) {
        <div class="empty">
          <mat-icon>history</mat-icon>
          <p>{{ 'pro.history.empty' | transloco }}</p>
        </div>
      } @else {
        @for (group of store.groupedByDay(); track group.date) {
          <div class="day-label">{{ group.label }}</div>
          @for (b of group.items; track b.id) {
            <app-booking-card [booking]="b" (cardClick)="openClient($event)" />
          }
        }
        <div #sentinel class="sentinel">
          @if (store.isPending()) {
            <mat-spinner diameter="20" />
          } @else if (!store.hasMore()) {
            <span class="end">{{ 'pro.history.endOfList' | transloco }}</span>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .history-page {
      padding: 12px 14px 40px;
      max-width: 720px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 8px;
      min-height: 100vh;
    }
    .page-header {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 0;
    }
    .page-header h1 {
      font-size: 18px; font-weight: 600; margin: 0; color: #333;
    }
    .back {
      background: none; border: none; color: #666; cursor: pointer;
      padding: 4px; display: flex; align-items: center;
    }
    .search-box {
      background: white; border-radius: 10px;
      padding: 8px 12px; display: flex; align-items: center; gap: 8px;
      box-shadow: 0 1px 2px rgba(0,0,0,0.04);
    }
    .search-box input {
      border: none; outline: none; font-size: 14px; flex: 1;
      background: transparent;
    }
    .search-box mat-icon { color: #999; font-size: 18px; width: 18px; height: 18px; }
    .filters-row {
      display: flex; gap: 6px; overflow-x: auto;
      padding: 4px 0 8px;
    }
    .chip {
      background: white;
      border: 1px solid #e5e5e5;
      padding: 6px 12px;
      border-radius: 16px;
      font-size: 12px;
      color: #555;
      font-weight: 500;
      white-space: nowrap;
      cursor: pointer;
    }
    .chip:hover { border-color: #c06; color: #c06; }
    .day-label {
      font-size: 11px; font-weight: 700; color: #666;
      text-transform: uppercase; letter-spacing: 0.5px;
      margin: 10px 2px 4px;
    }
    .empty {
      text-align: center; padding: 40px 0; color: #999;
      display: flex; flex-direction: column; align-items: center; gap: 10px;
    }
    .empty mat-icon { font-size: 48px; width: 48px; height: 48px; }
    .loading { display: flex; justify-content: center; padding: 30px 0; }
    .sentinel {
      min-height: 30px;
      display: flex; justify-content: center; align-items: center;
      padding: 16px 0;
    }
    .sentinel .end { font-size: 12px; color: #999; font-style: italic; }
  `],
})
export class ProBookingHistoryComponent implements AfterViewInit, OnDestroy {
  protected readonly store = inject(BookingHistoryStore);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  @ViewChild('sentinel') sentinel?: ElementRef<HTMLElement>;
  private observer?: IntersectionObserver;

  constructor() {
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

  protected periodLabel(): string {
    const f = this.store.filters();
    return `${formatDM(f.from)} → ${formatDM(f.to)}`;
  }

  protected statusLabel(): string {
    const n = this.store.filters().statuses.length;
    if (n === 4 || n === 0) return 'Statut: Tous';
    return `Statut: ${n}`;
  }

  protected employeeLabel(): string {
    return this.store.filters().employeeId === null ? 'Employé: Tous' : 'Employé: 1';
  }

  protected openClient(b: CareBookingDetailed): void {
    this.router.navigate(['/pro/clients', b.user.id]);
  }

  protected goBack(): void {
    this.router.navigate(['/pro/manage']);
  }

  protected openPeriod(): void {
    const ref = this.dialog.open<PeriodFilterSheetComponent, unknown, PeriodResult>(
      PeriodFilterSheetComponent,
      bottomSheetConfig(),
    );
    ref.afterClosed().subscribe((res) => {
      if (!res) return;
      this.store.updateFilters({ from: res.from, to: res.to });
    });
  }

  protected openStatus(): void {
    const ref = this.dialog.open<StatusFilterSheetComponent, unknown, CareBookingStatus[]>(
      StatusFilterSheetComponent,
      bottomSheetConfig({ data: { selected: this.store.filters().statuses } }),
    );
    ref.afterClosed().subscribe((selected) => {
      if (!selected) return;
      this.store.updateFilters({ statuses: selected });
    });
  }

  protected openEmployee(): void {
    const ref = this.dialog.open<EmployeeFilterSheetComponent, unknown, number | null>(
      EmployeeFilterSheetComponent,
      bottomSheetConfig({ data: { selected: this.store.filters().employeeId } }),
    );
    ref.afterClosed().subscribe((value) => {
      if (value === undefined) return;
      this.store.updateFilters({ employeeId: value });
    });
  }
}

function formatDM(ymd: string): string {
  const [, m, d] = ymd.split('-');
  return `${d}/${m}`;
}
