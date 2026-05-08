import { Component, computed, inject, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { SalonClientService } from '../../../salon-clients/salon-client.service';
import { SalonClientResponse } from '../../../salon-clients/salon-client.model';
import { ClientCreateFormComponent } from '../client-create-form/client-create-form.component';

@Component({
  selector: 'app-step-client',
  standalone: true,
  imports: [
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
    ClientCreateFormComponent,
  ],
  template: `
    <div class="step-client">
      <h3>{{ 'booking.stepper.step3' | transloco }}</h3>

      @if (!showCreateForm()) {
        <div class="choice-cards">
          <div
            class="choice-card"
            data-testid="client-mode-existing"
            [class.active]="mode() === 'search'"
            (click)="mode.set('search')"
          >
            <mat-icon>search</mat-icon>
            <div class="choice-title">{{ 'booking.client.existing' | transloco }}</div>
            <div class="choice-desc">{{ 'booking.client.existingDesc' | transloco }}</div>
          </div>
          <div class="choice-card" data-testid="client-mode-new" [class.active]="mode() === 'create'" (click)="showCreateForm.set(true)">
            <mat-icon>person_add</mat-icon>
            <div class="choice-title">{{ 'booking.client.new' | transloco }}</div>
            <div class="choice-desc">{{ 'booking.client.newDesc' | transloco }}</div>
          </div>
        </div>

        @if (mode() === 'search') {
          <div class="search-section">
            <mat-form-field class="search-field">
              <mat-label>{{ 'booking.client.search' | transloco }}</mat-label>
              <input matInput data-testid="client-search-input" (input)="onSearch($event)" />
              <mat-icon matSuffix>search</mat-icon>
            </mat-form-field>

            <div class="client-list">
              @for (client of displayedClients(); track client.id) {
                <div
                  class="client-row"
                  data-testid="client-result"
                  [class.selected]="selectedClientId() === client.id"
                  (click)="selectClient(client.id)"
                >
                  <div class="client-avatar">{{ getInitials(client.name) }}</div>
                  <div class="client-info">
                    <div class="client-name">{{ client.name }}</div>
                    <div class="client-phone">{{ client.phone }}</div>
                  </div>
                </div>
              } @empty {
                <div class="empty-clients">Aucun client trouvé</div>
              }
            </div>
          </div>
        }

        @if (selectedClientId()) {
          <button class="btn-confirm" data-testid="step-confirm-btn" (click)="onConfirm()">
            {{ 'booking.stepper.confirm' | transloco }}
          </button>
        }
      }

      @if (showCreateForm()) {
        <app-client-create-form
          (created)="onClientCreated($event)"
          (cancel)="showCreateForm.set(false)"
        />
      }
    </div>
  `,
  styles: [
    `
      .step-client {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      h3 {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
        color: #333;
      }

      .choice-cards {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
      }

      .choice-card {
        border: 2px solid #e0e0e0;
        border-radius: 14px;
        padding: 20px;
        cursor: pointer;
        text-align: center;
        transition:
          border-color 200ms ease,
          background 200ms ease,
          box-shadow 200ms ease;
      }

      .choice-card:hover {
        border-color: #f0a0c0;
        box-shadow: 0 2px 8px rgba(204, 0, 102, 0.08);
      }

      .choice-card.active {
        border-color: var(--pf-rose);
        background: #fff5f7;
      }

      .choice-card mat-icon {
        font-size: 32px;
        width: 32px;
        height: 32px;
        color: var(--pf-rose);
        margin-bottom: 8px;
      }

      .choice-title {
        font-weight: 600;
        font-size: 15px;
        color: #333;
        margin-bottom: 4px;
      }

      .choice-desc {
        font-size: 13px;
        color: #888;
      }

      .search-section {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .search-field {
        width: 100%;
      }

      .client-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 240px;
        overflow-y: auto;
      }

      .client-row {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 10px 14px;
        border: 2px solid #e0e0e0;
        border-radius: 12px;
        cursor: pointer;
        transition:
          border-color 200ms ease,
          background 200ms ease;
      }

      .client-row:hover {
        border-color: #f0a0c0;
      }

      .client-row.selected {
        border-color: var(--pf-rose);
        background: #fff5f7;
      }

      .client-avatar {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: var(--pf-rose);
        color: white;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 600;
        font-size: 14px;
        flex-shrink: 0;
      }

      .client-info {
        display: flex;
        flex-direction: column;
      }

      .client-name {
        font-weight: 500;
        font-size: 14px;
        color: #333;
      }

      .client-phone {
        font-size: 12px;
        color: #888;
      }

      .empty-clients {
        text-align: center;
        color: #999;
        font-size: 13px;
        padding: 24px 0;
        font-style: italic;
      }

      .btn-confirm {
        background: var(--pf-rose);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 12px 24px;
        width: 100%;
        margin-top: 16px;
        font-size: 14px;
        font-weight: 500;
        cursor: pointer;
        transition: background 200ms ease;
      }

      .btn-confirm:hover {
        background: #a00554;
      }
    `,
  ],
})
export class StepClientComponent {
  private readonly salonClientService = inject(SalonClientService);

  readonly mode = signal<'search' | 'create'>('search');
  readonly showCreateForm = signal(false);
  readonly selectedClientId = signal<number | null>(null);
  readonly searchResults = signal<SalonClientResponse[]>([]);
  readonly recentClients = signal<SalonClientResponse[]>([]);
  readonly clientSelected = output<{ salonClientId: number }>();

  readonly displayedClients = computed(() =>
    this.searchResults().length > 0 ? this.searchResults() : this.recentClients()
  );

  constructor() {
    this.salonClientService.recent().subscribe((clients) => this.recentClients.set(clients));
  }

  onSearch(event: Event): void {
    const query = (event.target as HTMLInputElement).value;
    if (query.length >= 2) {
      this.salonClientService
        .search(query)
        .subscribe((results) => this.searchResults.set(results));
    } else {
      this.searchResults.set([]);
    }
  }

  selectClient(id: number): void {
    this.selectedClientId.set(id);
  }

  onConfirm(): void {
    const id = this.selectedClientId();
    if (id) {
      this.clientSelected.emit({ salonClientId: id });
    }
  }

  onClientCreated(client: SalonClientResponse): void {
    this.selectedClientId.set(client.id);
    this.clientSelected.emit({ salonClientId: client.id });
  }

  getInitials(name: string): string {
    return name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }
}
