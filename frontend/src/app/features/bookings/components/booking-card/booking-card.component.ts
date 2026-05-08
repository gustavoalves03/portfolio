import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { CareBookingDetailed, CareBookingStatus } from '../../models/bookings.model';

@Component({
  selector: 'app-booking-card',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  template: `
    <div class="card" (click)="cardClick.emit(booking())">
      <div class="time">{{ booking().appointmentTime.substring(0, 5) }}</div>
      <div class="info">
        <div class="care">{{ booking().care.name }}</div>
        <div class="people">
          <span class="client">{{ booking().salonClientName || booking().user.name }}</span>
          @if (booking().employeeName) {
            <span class="sep">·</span>
            <span class="employee">{{ booking().employeeName }}</span>
          }
        </div>
      </div>
      <span class="status" [class.ok]="booking().status === 'CONFIRMED'"
                         [class.pending]="booking().status === 'PENDING'"
                         [class.cancelled]="booking().status === 'CANCELLED'"
                         [class.noshow]="booking().status === 'NO_SHOW'">
        {{ 'bookings.status.' + booking().status | transloco }}
      </span>
    </div>
  `,
  styles: [`
    .card {
      background: white;
      border-radius: 10px;
      padding: 10px 12px;
      display: flex;
      gap: 10px;
      align-items: center;
      box-shadow: 0 1px 2px rgba(0,0,0,0.04);
      cursor: pointer;
      transition: background 150ms ease;
    }
    .card:hover { background: #fafafa; }
    .time { font-weight: 600; color: var(--pf-rose); font-size: 13px; min-width: 44px; }
    .info { flex: 1; min-width: 0; }
    .care { font-weight: 500; color: #333; font-size: 13px; }
    .people { font-size: 11px; color: #666; margin-top: 2px; }
    .sep { margin: 0 4px; color: #aaa; }
    .status {
      font-size: 9px;
      padding: 3px 7px;
      border-radius: 5px;
      font-weight: 600;
      text-transform: uppercase;
    }
    .status.ok { background: #dcfce7; color: #166534; }
    .status.pending { background: #fef3c7; color: #92400e; }
    .status.cancelled { background: #f3f4f6; color: #6b7280; }
    .status.noshow { background: #fee2e2; color: #991b1b; }
  `],
})
export class BookingCardComponent {
  readonly booking = input.required<CareBookingDetailed>();
  readonly cardClick = output<CareBookingDetailed>();
}
