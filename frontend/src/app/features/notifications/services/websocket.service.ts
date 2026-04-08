import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { NotificationResponse } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private readonly authService = inject(AuthService);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly platformId = inject(PLATFORM_ID);
  private client: Client | null = null;

  readonly notification$ = new Subject<NotificationResponse>();

  connect(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    const token = this.authService.getToken();
    if (!token) return;

    // Derive WS URL from API base URL
    const wsUrl = this.apiBaseUrl.replace(/^http/, 'ws') + '/ws';

    this.client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 1000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.client!.subscribe('/user/queue/notifications', (message: IMessage) => {
          const notification: NotificationResponse = JSON.parse(message.body);
          this.notification$.next(notification);
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
      },
      onWebSocketClose: () => {
        // Reconnection handled automatically by stompjs with exponential backoff
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    if (this.client?.active) {
      this.client.deactivate();
      this.client = null;
    }
  }

  isConnected(): boolean {
    return this.client?.active ?? false;
  }
}
