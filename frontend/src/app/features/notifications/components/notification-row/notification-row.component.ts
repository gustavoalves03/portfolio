import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { NotificationResponse } from '../../models/notification.model';
import { AppDateTimePipe } from '../../../../shared/pipes/app-datetime.pipe';
import { SwipeLeftDirective } from './swipe-left.directive';

@Component({
  selector: 'app-notification-row',
  standalone: true,
  imports: [CommonModule, MatIconModule, AppDateTimePipe, SwipeLeftDirective],
  template: `
    <div class="notif-wrap">
      <div class="notif-bg">
        <mat-icon>check</mat-icon>
      </div>
      <div class="notif-card"
           appSwipeLeft
           (swipeLeftCommitted)="swipedLeft.emit()"
           (click)="rowClick.emit()">
        <div class="icon-cell">
          <mat-icon>notifications</mat-icon>
        </div>
        <div class="content">
          <div class="title">{{ notification().title }}</div>
          <div class="message">{{ notification().message }}</div>
          <div class="time">{{ notification().createdAt | appDateTime }}</div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .notif-wrap { position: relative; overflow: hidden; border-radius: 10px; margin-bottom: 8px; }
    .notif-bg {
      position: absolute; inset: 0;
      display: flex; align-items: center; justify-content: flex-end;
      padding-right: 14px;
      background: linear-gradient(270deg, #a7f3d0 0%, #6ee7b7 100%);
      color: #065f46;
      z-index: 1;
    }
    .notif-bg mat-icon { font-size: 22px; width: 22px; height: 22px; }
    .notif-card {
      position: relative; z-index: 2;
      background: white;
      padding: 10px 12px;
      display: flex; gap: 10px;
      box-shadow: 0 1px 2px rgba(0,0,0,0.04);
      cursor: pointer;
      touch-action: pan-y;
    }
    .icon-cell {
      width: 34px; height: 34px;
      border-radius: 50%;
      background: #fff0f5;
      color: var(--pf-rose);
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
    }
    .icon-cell mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .content { flex: 1; min-width: 0; }
    .title { font-size: 13px; font-weight: 600; color: #333; }
    .message { font-size: 12px; color: #666; margin-top: 2px; }
    .time { font-size: 11px; color: #999; margin-top: 3px; }
  `],
})
export class NotificationRowComponent {
  readonly notification = input.required<NotificationResponse>();
  readonly swipedLeft = output<void>();
  readonly rowClick = output<void>();
}
