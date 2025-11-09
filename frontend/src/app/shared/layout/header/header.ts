import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SidenavService } from '../navigation/sidenav.service';
import { SidenavOverlay } from '../navigation/sidenav-overlay';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, SidenavOverlay],
  templateUrl: './header.html',
  styleUrl: './header.scss'
})
export class Header {
  protected readonly sidenavService = inject(SidenavService);

  protected toggleSidenav(): void {
    this.sidenavService.toggle();
  }
}

