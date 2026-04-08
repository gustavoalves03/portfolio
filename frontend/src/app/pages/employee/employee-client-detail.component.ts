import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { TrackingService } from '../../features/tracking/tracking.service';
import { ClientHistoryResponse, AccessLevel, PermissionsMap } from '../../features/tracking/tracking.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { ClientHeaderComponent } from '../../features/tracking/components/client-header/client-header.component';
import { ClientVisitsComponent } from '../../features/tracking/components/client-visits/client-visits.component';
import { ClientNotesComponent } from '../../features/tracking/components/client-notes/client-notes.component';
import { ClientInfoComponent } from '../../features/tracking/components/client-info/client-info.component';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-employee-client-detail',
  standalone: true,
  imports: [
    RouterLink, MatIconModule, MatProgressSpinnerModule, TranslocoPipe,
    ClientHeaderComponent, ClientVisitsComponent, ClientNotesComponent, ClientInfoComponent,
  ],
  template: `
    @if (loading()) {
      <div class="flex justify-center py-16">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (history()) {
      <div class="client-detail-page">
        <a routerLink="/employee/bookings" class="back-link">
          <mat-icon>arrow_back</mat-icon>
          <span>{{ 'common.back' | transloco }}</span>
        </a>

        <app-client-header
          [clientName]="history()!.clientName"
          [allergies]="history()!.profile.allergies"
          [visitCount]="history()!.visits.length"
          [createdAt]="history()!.profile.createdAt" />

        @if (permissions().VISITS !== 'NONE') {
          <app-client-visits
            [visits]="history()!.visits"
            [accessLevel]="permissions().VISITS"
            [apiBaseUrl]="apiBaseUrl"
            (createVisit)="onCreateVisit()" />
        }

        @if (permissions().PROFILE !== 'NONE') {
          <app-client-notes
            [notes]="history()!.profile.notes"
            [updatedByName]="history()!.profile.updatedByName"
            [updatedAt]="history()!.profile.updatedAt"
            [accessLevel]="permissions().PROFILE"
            (saveNotes)="onSaveNotes($event)" />

          <app-client-info
            [profile]="history()!.profile"
            [accessLevel]="permissions().PROFILE"
            (saveInfo)="onSaveInfo($event)" />
        }
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      background: #f5f4f2;
      min-height: 100vh;
      padding: 16px;
    }
    .client-detail-page {
      max-width: 600px;
      margin: 0 auto;
      padding-bottom: 80px;
    }
    .back-link {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: #6b7280;
      text-decoration: none;
      font-size: 13px;
      margin-bottom: 12px;
      mat-icon { font-size: 18px; width: 18px; height: 18px; }
      &:hover { color: #c06; }
    }
  `
})
export class EmployeeClientDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly trackingService = inject(TrackingService);
  private readonly snackBar = inject(MatSnackBar);
  readonly apiBaseUrl = inject(API_BASE_URL);

  loading = signal(true);
  history = signal<ClientHistoryResponse | null>(null);
  permissions = signal<PermissionsMap>({ PROFILE: 'NONE', VISITS: 'NONE', PHOTOS: 'NONE', REMINDERS: 'NONE' });

  private userId = 0;

  constructor() {
    this.route.params.subscribe(params => {
      this.userId = +params['userId'];
      this.loadData();
    });
  }

  private loadData(): void {
    this.loading.set(true);
    forkJoin({
      history: this.trackingService.getClientHistoryAsEmployee(this.userId),
      perms: this.trackingService.getMyPermissions(),
    }).subscribe({
      next: ({ history, perms }) => {
        this.history.set(history);
        this.permissions.set({
          PROFILE: (perms['PROFILE'] as AccessLevel) ?? 'NONE',
          VISITS: (perms['VISITS'] as AccessLevel) ?? 'NONE',
          PHOTOS: (perms['PHOTOS'] as AccessLevel) ?? 'NONE',
          REMINDERS: (perms['REMINDERS'] as AccessLevel) ?? 'NONE',
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onCreateVisit(): void {
    this.snackBar.open('Fonctionnalité à venir', '', { duration: 2000 });
  }

  onSaveNotes(notes: string): void {
    this.trackingService.updateProfile(this.userId, { notes, skinType: null, hairType: null, allergies: null, preferences: null })
      .subscribe(() => this.loadData());
  }

  onSaveInfo(info: any): void {
    this.trackingService.updateProfile(this.userId, info)
      .subscribe(() => this.loadData());
  }
}
