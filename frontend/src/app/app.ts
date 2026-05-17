import { Component, signal, inject, effect, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { Header } from './shared/layout/header/header';
import { Footer } from './shared/layout/footer/footer';
import { BottomNavComponent } from './shared/layout/bottom-nav/bottom-nav.component';
import { VerifyEmailBannerComponent } from './shared/layout/verify-email-banner/verify-email-banner.component';
import { LangService } from './i18n/lang.service';
import { NotificationToastComponent } from './features/notifications/components/notification-toast/notification-toast.component';
import { NotificationsStore } from './features/notifications/store/notifications.store';
import { AuthService } from './core/auth/auth.service';
import { CookieBannerComponent } from './shared/components/cookie-banner/cookie-banner.component';
import { MobileMiniFooterComponent } from './shared/layout/mobile-mini-footer/mobile-mini-footer.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, Footer, BottomNavComponent, NotificationToastComponent, VerifyEmailBannerComponent, CookieBannerComponent, MobileMiniFooterComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('app');

  // Initialize language/locale at app creation (CSR/SSR safe)
  private readonly _init = inject(LangService).init();

  private readonly notificationsStore = inject(NotificationsStore);
  private readonly authService = inject(AuthService);
  private readonly platformId = inject(PLATFORM_ID);

  constructor() {
    const isBrowser = isPlatformBrowser(this.platformId);

    effect(() => {
      const isAuth = this.authService.isAuthenticated();
      if (isBrowser && isAuth) {
        this.notificationsStore.loadUnreadCount();
        this.notificationsStore.connectWebSocket();
      } else if (isBrowser && !isAuth) {
        this.notificationsStore.disconnectWebSocket();
        this.notificationsStore.reset();
      }
    });
  }
}
