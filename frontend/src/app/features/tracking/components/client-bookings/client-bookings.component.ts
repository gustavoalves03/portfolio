import { Component, effect, inject, input } from '@angular/core';
import { AppDatePipe } from '../../../../shared/pipes/app-date.pipe';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ClientBookingsStore } from './client-bookings.store';
import {
  CareBookingDetailed,
  CareBookingStatus,
} from '../../../bookings/models/bookings.model';
import {
  NoShowConfirmDialogComponent,
  NoShowConfirmData,
} from './no-show-confirm-dialog.component';
import { bottomSheetConfig } from '../../../../shared/uis/sheet-handle/bottom-sheet.config';

@Component({
  selector: 'app-client-bookings',
  standalone: true,
  imports: [
    AppDatePipe,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslocoPipe,
  ],
  providers: [ClientBookingsStore],
  template: `
    <div class="section">
      <div class="section-header">
        <span class="section-title">{{ 'tracking.bookings.title' | transloco }}</span>
      </div>

      @if (store.isPending()) {
        <div class="loading">
          <mat-spinner diameter="24"></mat-spinner>
        </div>
      } @else {
        <!-- Tabs -->
        <mat-button-toggle-group
          [value]="store.activeTab()"
          (change)="store.setActiveTab($event.value)"
          class="tabs">
          <mat-button-toggle value="upcoming">
            {{ 'tracking.bookings.upcoming' | transloco }} ({{ store.upcomingCount() }})
          </mat-button-toggle>
          <mat-button-toggle value="past">
            {{ 'tracking.bookings.past' | transloco }} ({{ store.pastCount() }})
          </mat-button-toggle>
        </mat-button-toggle-group>

        @if (store.activeTab() === 'upcoming') {
          <!-- Today's bookings -->
          @for (booking of store.todayBookings(); track booking.id) {
            <div class="booking-card today-card">
              <div class="today-badge">{{ 'tracking.bookings.today' | transloco }}</div>
              <div class="booking-content">
                <div class="booking-info">
                  <div class="care-name">{{ booking.care.name }}</div>
                  <div class="booking-meta">
                    {{ booking.appointmentTime.slice(0, 5) }} · {{ booking.care.duration }} min
                    @if (booking.employeeName) {
                      · {{ booking.employeeName }}
                    }
                  </div>
                </div>
                <span class="status-badge" [attr.data-status]="booking.status">
                  {{ 'tracking.bookings.status.' + booking.status | transloco }}
                </span>
              </div>
            </div>
          }
          <!-- Upcoming bookings -->
          @for (booking of store.upcomingBookings(); track booking.id) {
            <div class="booking-card">
              <div class="booking-content">
                <div class="booking-info">
                  <div class="care-name">{{ booking.care.name }}</div>
                  <div class="booking-meta">
                    {{ booking.appointmentDate | appDate }} · {{ booking.appointmentTime.slice(0, 5) }}
                    @if (booking.employeeName) {
                      · {{ booking.employeeName }}
                    }
                  </div>
                </div>
                <span class="status-badge" [attr.data-status]="booking.status">
                  {{ 'tracking.bookings.status.' + booking.status | transloco }}
                </span>
              </div>
            </div>
          }
          @if (store.upcomingCount() === 0) {
            <div class="empty">{{ 'tracking.bookings.empty' | transloco }}</div>
          }
        } @else {
          <!-- Past bookings -->
          @for (booking of store.pastBookings(); track booking.id) {
            <div class="booking-card past-card">
              <div class="booking-content">
                <div class="booking-info">
                  <div class="care-name">{{ booking.care.name }}</div>
                  <div class="booking-meta">
                    {{ booking.appointmentDate | appDate }} · {{ booking.appointmentTime.slice(0, 5) }}
                    @if (booking.employeeName) {
                      · {{ booking.employeeName }}
                    }
                  </div>
                </div>
                <div class="past-actions">
                  @if (booking.status === CareBookingStatus.CONFIRMED) {
                    <button class="no-show-btn" (click)="confirmNoShow(booking)">
                      {{ 'tracking.bookings.status.NO_SHOW' | transloco }}
                    </button>
                  } @else {
                    <span class="status-badge" [attr.data-status]="booking.status">
                      {{ 'tracking.bookings.status.' + booking.status | transloco }}
                    </span>
                  }
                </div>
              </div>
            </div>
          }
          @if (store.pastCount() === 0) {
            <div class="empty">{{ 'tracking.bookings.empty' | transloco }}</div>
          }
        }
      }
    </div>
  `,
  styles: [
    `
      .section {
        margin-bottom: 12px;
      }
      .section-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
      }
      .section-title {
        font-size: 13px;
        font-weight: 600;
        color: #333;
      }
      .loading {
        display: flex;
        justify-content: center;
        padding: 16px;
      }
      .tabs {
        width: 100%;
        margin-bottom: 10px;
        border-radius: 10px;
        overflow: hidden;

        ::ng-deep .mat-button-toggle {
          flex: 1;
          text-align: center;
          font-size: 12px;
          font-weight: 600;
        }
        ::ng-deep .mat-button-toggle-checked {
          background: #c06;
          color: white;
        }
      }
      .booking-card {
        background: white;
        border-radius: 10px;
        padding: 10px 12px;
        margin-bottom: 6px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
      }
      .today-card {
        border: 1.5px solid #c06;
        position: relative;
        margin-top: 8px;
      }
      .today-badge {
        position: absolute;
        top: -8px;
        left: 12px;
        background: #c06;
        color: white;
        font-size: 9px;
        font-weight: 700;
        padding: 1px 8px;
        border-radius: 4px;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
      .past-card {
        opacity: 0.75;
      }
      .booking-content {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .booking-info {
        flex: 1;
        min-width: 0;
      }
      .care-name {
        font-size: 13px;
        font-weight: 600;
        color: #1a1a2e;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .booking-meta {
        font-size: 11px;
        color: #6b7280;
        margin-top: 2px;
      }
      .status-badge {
        font-size: 10px;
        font-weight: 600;
        padding: 2px 8px;
        border-radius: 6px;
        white-space: nowrap;
        flex-shrink: 0;
      }
      .status-badge[data-status='CONFIRMED'] {
        color: #52b788;
        background: #ecfdf5;
      }
      .status-badge[data-status='PENDING'] {
        color: #fb923c;
        background: #fff7ed;
      }
      .status-badge[data-status='CANCELLED'] {
        color: #ef5350;
        background: #fef2f2;
      }
      .status-badge[data-status='NO_SHOW'] {
        color: #999;
        background: #f3f4f6;
      }
      .past-actions {
        flex-shrink: 0;
      }
      .no-show-btn {
        background: #dc2626;
        color: white;
        border: none;
        border-radius: 6px;
        font-size: 10px;
        font-weight: 600;
        padding: 4px 10px;
        cursor: pointer;
      }
      .no-show-btn:hover {
        background: #b91c1c;
      }
      .empty {
        text-align: center;
        padding: 24px;
        color: #9ca3af;
        font-size: 13px;
      }
    `,
  ],
})
export class ClientBookingsComponent {
  readonly store = inject(ClientBookingsStore);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);

  readonly userId = input.required<number>();
  readonly CareBookingStatus = CareBookingStatus;

  constructor() {
    effect(() => {
      const id = this.userId();
      if (id) {
        this.store.loadBookings(id);
      }
    });
  }

  confirmNoShow(booking: CareBookingDetailed): void {
    const dialogRef = this.dialog.open(NoShowConfirmDialogComponent, bottomSheetConfig({
      data: {
        careName: booking.care.name,
        appointmentDate: booking.appointmentDate,
      } satisfies NoShowConfirmData,
      width: '360px',
    }));

    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.store.markNoShow(booking);
        this.snackBar.open(
          this.transloco.translate('tracking.bookings.noShow.success'),
          undefined,
          { duration: 3000 }
        );
      }
    });
  }
}
