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
    if (this.client?.active) return; // Already connected

    const token = this.authService.getToken();
    if (!token) return;

    const wsUrl = this.apiBaseUrl.replace(/^http/, 'ws') + '/ws';

    this.client = new Client({
      brokerURL: wsUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 1000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (str) => console.log('[STOMP]', str),
      onConnect: () => {
        console.log('[WS] Connected, subscribing to /user/queue/notifications');
        this.client!.subscribe('/user/queue/notifications', (message: IMessage) => {
          console.log('[WS] Notification received:', message.body);
          const notification: NotificationResponse = JSON.parse(message.body);
          this.notification$.next(notification);
        });
      },
      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message']);
      },
      onWebSocketClose: (event) => {
        console.warn('[WS] Connection closed:', event);
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
