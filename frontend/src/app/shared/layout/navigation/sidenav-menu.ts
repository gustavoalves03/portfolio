import { Component, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { NAVIGATION_ROUTES, NavigationRoute } from './navigation-routes';

@Component({
  selector: 'app-sidenav-menu',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatListModule, MatIconModule],
  templateUrl: './sidenav-menu.html',
  styleUrl: './sidenav-menu.scss'
})
export class SidenavMenu {
  protected readonly routes = NAVIGATION_ROUTES;

  // Émet un événement quand on clique sur un lien pour fermer le menu
  readonly linkClicked = output<void>();

  protected onLinkClick(): void {
    this.linkClicked.emit();
  }
}
