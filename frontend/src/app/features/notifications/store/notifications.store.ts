import { computed, effect, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap, EMPTY, catchError } from 'rxjs';
import { NotificationsService } from '../services/notifications.service';
import { WebSocketService } from '../services/websocket.service';
import { NotificationResponse } from '../models/notification.model';
import { AuthService } from '../../../core/auth/auth.service';

type NotificationsState = {
  notifications: NotificationResponse[];
  unreadCount: number;
  latestNotification: NotificationResponse | null;
};

export const NotificationsStore = signalStore(
  withState<NotificationsState>({
    notifications: [],
    unreadCount: 0,
    latestNotification: null,
  }),
  withComputed((store) => ({
    hasUnread: computed(() => store.unreadCount() > 0),
    badgeLabel: computed(() => {
      const count = store.unreadCount();
      if (count === 0) return '';
      if (count > 99) return '99+';
      return count.toString();
    }),
  })),
  withMethods((store,
    notificationsService = inject(NotificationsService),
    webSocketService = inject(WebSocketService),
    authService = inject(AuthService),
  ) => ({
    loadUnreadCount: rxMethod<void>(
      pipe(
        switchMap(() => notificationsService.unreadCount()),
        tap((count) => patchState(store, { unreadCount: count })),
        catchError(() => EMPTY)
      )
    ),
    loadNotifications: rxMethod<void>(
      pipe(
        switchMap(() => notificationsService.list(undefined, 0, 50)),
        tap((page) => patchState(store, { notifications: page.content })),
        catchError(() => EMPTY)
      )
    ),
    markAsRead: rxMethod<number>(
      pipe(
        switchMap((id) =>
          notificationsService.markAsRead(id).pipe(
            tap(() =>
              patchState(store, {
                notifications: store.notifications().map((n) =>
                  n.id === id ? { ...n, read: true } : n
                ),
                unreadCount: Math.max(0, store.unreadCount() - 1),
              })
            ),
            catchError(() => EMPTY)
          )
        )
      )
    ),
    connectWebSocket(): void {
      webSocketService.connect();
      webSocketService.notification$.subscribe((notification) => {
        patchState(store, {
          notifications: [notification, ...store.notifications()],
          unreadCount: store.unreadCount() + 1,
          latestNotification: notification,
        });
        // Clear latest after 4s (toast duration + buffer)
        setTimeout(() => {
          if (store.latestNotification()?.id === notification.id) {
            patchState(store, { latestNotification: null });
          }
        }, 4000);
      });
    },
    disconnectWebSocket(): void {
      webSocketService.disconnect();
    },
    clearLatestNotification(): void {
      patchState(store, { latestNotification: null });
    },
  })),
  withHooks((store, authService = inject(AuthService)) => ({
    onInit() {
      effect(() => {
        if (authService.isAuthenticated()) {
          store.loadUnreadCount();
          store.connectWebSocket();
        } else {
          store.disconnectWebSocket();
          patchState(store, { notifications: [], unreadCount: 0, latestNotification: null });
        }
      });
    },
  }))
);
