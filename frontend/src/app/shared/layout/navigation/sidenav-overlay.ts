import { Component, inject, effect } from '@angular/core';
import { SidenavMenu } from './sidenav-menu';
import { SidenavService } from './sidenav.service';

@Component({
  selector: 'app-sidenav-overlay',
  standalone: true,
  imports: [SidenavMenu],
  templateUrl: './sidenav-overlay.html',
  styleUrl: './sidenav-overlay.scss'
})
export class SidenavOverlay {
  protected readonly sidenavService = inject(SidenavService);

  constructor() {
    // Empêcher le scroll du body quand le sidenav est ouvert
    effect(() => {
      if (typeof document !== 'undefined') {
        // Utiliser setTimeout pour éviter l'erreur ExpressionChangedAfterItHasBeenChecked
        setTimeout(() => {
          if (this.sidenavService.isOpen()) {
            document.body.style.overflow = 'hidden';
          } else {
            document.body.style.overflow = '';
          }
        }, 0);
      }
    });
  }

  protected closeOnBackdrop(): void {
    this.sidenavService.close();
  }
}
