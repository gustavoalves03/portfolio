import { Component, signal, inject, afterNextRender, effect } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from './shared/layout/header/header';
import { Footer } from './shared/layout/footer/footer';
import { BottomNavComponent } from './shared/layout/bottom-nav/bottom-nav.component';
import { LangService } from './i18n/lang.service';
import { NotificationToastComponent } from './features/notifications/components/notification-toast/notification-toast.component';
import { NotificationsStore } from './features/notifications/store/notifications.store';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, Footer, BottomNavComponent, NotificationToastComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('app');

  // Initialize language/locale at app creation (CSR/SSR safe)
  private readonly _init = inject(LangService).init();

  private readonly notificationsStore = inject(NotificationsStore);
  private readonly authService = inject(AuthService);

  constructor() {
    // Only connect WebSocket on the browser (not during SSR)
    afterNextRender(() => {
      console.log('[App] afterNextRender - setting up notifications');
      effect(() => {
        const isAuth = this.authService.isAuthenticated();
        console.log('[App] auth effect fired, isAuthenticated:', isAuth);
        if (isAuth) {
          this.notificationsStore.loadUnreadCount();
          this.notificationsStore.connectWebSocket();
        } else {
          this.notificationsStore.disconnectWebSocket();
          this.notificationsStore.reset();
        }
      });
    });
  }
}
