import { Component, inject, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SidenavService } from '../navigation/sidenav.service';
import { SidenavOverlay } from '../navigation/sidenav-overlay';
import { BookingsDrawerComponent } from './bookings-drawer/bookings-drawer.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, SidenavOverlay, BookingsDrawerComponent],
  templateUrl: './header.html',
  styleUrl: './header.scss'
})
export class Header {
  protected readonly sidenavService = inject(SidenavService);
  protected readonly bookingsDrawer = viewChild.required(BookingsDrawerComponent);

  protected toggleSidenav(): void {
    this.sidenavService.toggle();
  }

  protected toggleBookingsDrawer(): void {
    this.bookingsDrawer().toggle();
  }
}
