import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { ClientBookingsStore } from '../../features/client-bookings/client-bookings.store';

@Component({
  selector: 'app-client-bookings',
  standalone: true,
  imports: [MatTabsModule, MatButtonModule, MatProgressSpinnerModule, TranslocoPipe],
  providers: [ClientBookingsStore],
  templateUrl: './client-bookings.component.html',
  styleUrl: './client-bookings.component.scss',
})
export class ClientBookingsComponent {
  protected readonly store = inject(ClientBookingsStore);
  private readonly router = inject(Router);

  onTabChange(index: number): void {
    if (index === 1) {
      this.store.loadPast();
    }
  }

  onDiscover(): void {
    this.router.navigate(['/discover']);
  }

  onSalonClick(slug: string): void {
    this.router.navigate(['/salon', slug]);
  }

  formatDuration(minutes: number): string {
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${h}h`;
  }

  formatPrice(cents: number): string {
    return (cents / 100).toFixed(2).replace('.', ',') + ' \u20AC';
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr + 'T00:00:00');
    return date.toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }
}
