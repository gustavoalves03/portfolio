import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { NotificationsStore } from '../../features/notifications/store/notifications.store';
import { NotificationResponse } from '../../features/notifications/models/notification.model';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [TranslocoPipe, DatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent {
  protected readonly store = inject(NotificationsStore);
  private readonly router = inject(Router);

  constructor() {
    this.store.loadNotifications();
  }

  onNotificationClick(notification: NotificationResponse): void {
    if (!notification.read) {
      this.store.markAsRead(notification.id);
    }
    this.navigateToReference(notification);
  }

  private navigateToReference(notification: NotificationResponse): void {
    switch (notification.referenceType) {
      case 'BOOKING':
        this.router.navigate(['/pro/bookings'], {
          queryParams: { highlight: notification.referenceId },
        });
        break;
      default:
        break;
    }
  }
}
