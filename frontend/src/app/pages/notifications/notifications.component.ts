import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { NotificationsStore } from '../../features/notifications/store/notifications.store';
import { NotificationResponse } from '../../features/notifications/models/notification.model';
import { AppDateTimePipe } from '../../shared/pipes/app-datetime.pipe';
import { SalonClientService } from '../../features/salon-clients/salon-client.service';

@Component({
  selector: 'app-notifications-page',
  standalone: true,
  imports: [TranslocoPipe, AppDateTimePipe, MatIconModule],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent {
  protected readonly store = inject(NotificationsStore);
  private readonly router = inject(Router);
  private readonly salonClientService = inject(SalonClientService);

  constructor() {
    this.store.loadInitial();
  }

  onNotificationClick(notification: NotificationResponse): void {
    if (!notification.read) {
      this.store.markAsRead(notification.id);
    }
    this.navigateToReference(notification);
  }

  onLinkClient(notif: NotificationResponse): void {
    this.store.markAsRead(notif.id);
    this.router.navigate(['/pro/clients', notif.referenceId]);
  }

  private navigateToReference(notification: NotificationResponse): void {
    switch (notification.referenceType) {
      case 'BOOKING':
        this.router.navigate(['/pro/bookings'], {
          queryParams: { highlight: notification.referenceId },
        });
        break;
      case 'SALON_CLIENT':
        this.router.navigate(['/pro/clients', notification.referenceId]);
        break;
      default:
        break;
    }
  }
}
