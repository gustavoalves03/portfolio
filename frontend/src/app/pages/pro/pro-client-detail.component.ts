import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { TrackingService } from '../../features/tracking/tracking.service';
import { CaresService } from '../../features/cares/services/cares.service';
import { Care } from '../../features/cares/models/cares.model';
import {
  ClientHistoryResponse,
  UpdateProfileRequest,
} from '../../features/tracking/tracking.model';
import { ClientHeaderComponent } from '../../features/tracking/components/client-header/client-header.component';
import { ClientVisitsComponent } from '../../features/tracking/components/client-visits/client-visits.component';
import { ClientNotesComponent } from '../../features/tracking/components/client-notes/client-notes.component';
import { ClientInfoComponent } from '../../features/tracking/components/client-info/client-info.component';

@Component({
  selector: 'app-pro-client-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    MatProgressSpinnerModule,
    ClientHeaderComponent,
    ClientVisitsComponent,
    ClientNotesComponent,
    ClientInfoComponent,
  ],
  template: `
    @if (loading()) {
      <div class="flex justify-center py-16">
        <mat-spinner diameter="40"></mat-spinner>
      </div>
    } @else if (history()) {
      <div class="client-detail-page">
        <a routerLink="/pro/dashboard" class="back-link">
          <mat-icon>arrow_back</mat-icon>
          <span>Retour</span>
        </a>

        <app-client-header
          [clientName]="history()!.clientName"
          [allergies]="history()!.profile.allergies"
          [visitCount]="history()!.visits.length"
          [createdAt]="history()!.profile.createdAt" />

        <app-client-visits
          [visits]="history()!.visits"
          [accessLevel]="'WRITE'"
          [apiBaseUrl]="apiBaseUrl"
          (createVisit)="onCreateVisit()" />

        <app-client-notes
          [notes]="history()!.profile.notes"
          [updatedByName]="history()!.profile.updatedByName"
          [updatedAt]="history()!.profile.updatedAt"
          [accessLevel]="'WRITE'"
          (saveNotes)="onSaveNotes($event)" />

        <app-client-info
          [profile]="history()!.profile"
          [accessLevel]="'WRITE'"
          (saveInfo)="onSaveInfo($event)" />
      </div>
    }
  `,
  styles: [`
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
  `],
})
export class ProClientDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly trackingService = inject(TrackingService);
  private readonly caresService = inject(CaresService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  readonly apiBaseUrl = inject(API_BASE_URL);

  readonly loading = signal(true);
  readonly history = signal<ClientHistoryResponse | null>(null);
  readonly cares = signal<Care[]>([]);

  private userId = 0;

  constructor() {
    this.route.params.subscribe((p) => {
      this.userId = +p['userId'];
      this.loadHistory(this.userId);
    });
    this.loadCares();
  }

  private loadHistory(userId: number): void {
    this.loading.set(true);
    this.trackingService.getClientHistory(userId).subscribe({
      next: (data) => {
        this.history.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  private loadCares(): void {
    this.caresService.listOrdered().subscribe({
      next: (cares) => this.cares.set(cares),
    });
  }

  onCreateVisit(): void {
    this.snackBar.open(
      this.transloco.translate('tracking.newVisit'),
      undefined,
      { duration: 3000 },
    );
  }

  onSaveNotes(notes: string): void {
    const profile = this.history()!.profile;
    this.trackingService
      .updateProfile(this.userId, {
        notes: notes || null,
        skinType: profile.skinType,
        hairType: profile.hairType,
        allergies: profile.allergies,
        preferences: profile.preferences,
      })
      .subscribe({
        next: (updatedProfile) => {
          const current = this.history()!;
          this.history.set({ ...current, profile: updatedProfile });
          this.snackBar.open(
            this.transloco.translate('tracking.saveSuccess'),
            undefined,
            { duration: 3000 },
          );
        },
      });
  }

  onSaveInfo(info: UpdateProfileRequest): void {
    this.trackingService.updateProfile(this.userId, info).subscribe({
      next: (updatedProfile) => {
        const current = this.history()!;
        this.history.set({ ...current, profile: updatedProfile });
        this.snackBar.open(
          this.transloco.translate('tracking.saveSuccess'),
          undefined,
          { duration: 3000 },
        );
      },
    });
  }
}
