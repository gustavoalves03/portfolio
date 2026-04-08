import { Component, signal, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from './shared/layout/header/header';
import { Footer } from './shared/layout/footer/footer';
import { BottomNavComponent } from './shared/layout/bottom-nav/bottom-nav.component';
import { LangService } from './i18n/lang.service';
import { NotificationToastComponent } from './features/notifications/components/notification-toast/notification-toast.component';

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
}
