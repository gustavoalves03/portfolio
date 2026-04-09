import {
  Component,
  inject,
  signal,
  computed,
  PLATFORM_ID,
  ChangeDetectionStrategy,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ClientTrackingService } from '../../features/tracking/client-tracking.service';
import {
  ClientHistoryResponse,
  VisitRecordResponse,
} from '../../features/tracking/tracking.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { RateVisitDialogComponent } from './rate-visit-dialog.component';

@Component({
  selector: 'app-client-evolution',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatFormFieldModule,
    MatDialogModule,
    TranslocoPipe,
  ],
  template: `
    @if (loading()) {
      <div class="loading-container">
        <mat-spinner diameter="48"></mat-spinner>
      </div>
    } @else if (error()) {
      <div class="error-container">
        <mat-icon>error_outline</mat-icon>
        <p>{{ error() }}</p>
      </div>
    } @else {
      <div class="evolution-page">
        <!-- Header -->
        <header class="evo-header">
          <h1>{{ 'evolution.title' | transloco }}</h1>
          <div class="evo-stats">
            <div class="stat">
              <span class="stat-value">{{ data()?.visits?.length ?? 0 }}</span>
              <span class="stat-label">{{ 'evolution.visits' | transloco }}</span>
            </div>
          </div>
        </header>

        <!-- Recommendation banner -->
        @if (firstReminder(); as rem) {
          <div class="reminder-banner">
            <mat-icon>event_available</mat-icon>
            <span>
              {{ 'evolution.reminder' | transloco }}
              {{ weeksUntil(rem.recommendedDate) }} {{ 'evolution.weeks' | transloco }}
              @if (rem.careName) {
                ({{ rem.careName }})
              }
            </span>
          </div>
        }

        <!-- Comparison Slider Section -->
        @if (visitsWithPhotos().length > 0) {
          <section class="comparison-section">
            <h2>{{ 'evolution.compare' | transloco }}</h2>

            <!-- Date selectors -->
            <div class="date-selectors">
              <mat-form-field appearance="outline" class="date-select">
                <mat-select
                  [value]="leftVisitId()"
                  (selectionChange)="leftVisitId.set($event.value)"
                >
                  @for (v of visitsWithPhotos(); track v.id) {
                    <mat-option [value]="v.id">
                      {{ formatDate(v.visitDate) }} - {{ v.careName }}
                    </mat-option>
                  }
                </mat-select>
              </mat-form-field>

              <span class="vs-label">{{ 'evolution.vs' | transloco }}</span>

              <mat-form-field appearance="outline" class="date-select">
                <mat-select
                  [value]="rightVisitId()"
                  (selectionChange)="rightVisitId.set($event.value)"
                >
                  @for (v of visitsWithPhotos(); track v.id) {
                    <mat-option [value]="v.id">
                      {{ formatDate(v.visitDate) }} - {{ v.careName }}
                    </mat-option>
                  }
                </mat-select>
              </mat-form-field>
            </div>

            <!-- Before/After slider -->
            @if (leftPhoto() && rightPhoto()) {
              <div
                class="ba-container"
                (mousedown)="onDragStart($event)"
                (mousemove)="onDragMove($event)"
                (mouseup)="onDragEnd()"
                (mouseleave)="onDragEnd()"
                (dragstart)="$event.preventDefault()"
                (touchstart)="onTouchStart($event)"
                (touchmove)="onTouchMove($event)"
                (touchend)="onDragEnd()"
              >
                <img
                  [src]="imgUrl(rightPhoto()!)"
                  class="ba-img ba-after"
                  alt="After"
                  draggable="false"
                />
                <img
                  [src]="imgUrl(leftPhoto()!)"
                  class="ba-img ba-before"
                  [style.clip-path]="'inset(0 ' + (100 - sliderPosition()) + '% 0 0)'"
                  alt="Before"
                  draggable="false"
                />
                <div class="ba-handle" [style.left.%]="sliderPosition()">
                  <div class="ba-handle-line"></div>
                  <div class="ba-handle-circle">
                    <mat-icon>swap_horiz</mat-icon>
                  </div>
                  <div class="ba-handle-line"></div>
                </div>
                <span class="ba-label ba-label-left">{{ leftDateLabel() }}</span>
                <span class="ba-label ba-label-right">{{ rightDateLabel() }}</span>
              </div>
            } @else {
              <div class="no-photos-hint">
                <mat-icon>photo_library</mat-icon>
                <p>{{ 'evolution.noPhotos' | transloco }}</p>
              </div>
            }
          </section>

          <!-- Mini timeline -->
          <section class="timeline-section">
            <div class="timeline-scroll">
              @for (v of visitsWithPhotos(); track v.id) {
                <button
                  class="timeline-thumb"
                  [class.active]="leftVisitId() === v.id || rightVisitId() === v.id"
                  (click)="onTimelineClick(v)"
                >
                  <img [src]="imgUrl(getVisitThumb(v))" [alt]="v.careName" />
                  <span class="thumb-date">{{ formatShortDate(v.visitDate) }}</span>
                </button>
              }
            </div>
          </section>
        } @else {
          <div class="empty-state">
            <mat-icon>auto_awesome</mat-icon>
            @if (data()?.visits?.length === 0) {
              <p>{{ 'evolution.noVisits' | transloco }}</p>
            } @else {
              <p>{{ 'evolution.noPhotos' | transloco }}</p>
            }
          </div>
        }

        <!-- Share button -->
        @if (visitsWithPhotos().length > 0) {
          <div class="share-section">
            <button mat-flat-button class="share-btn" (click)="onShare()">
              <mat-icon>share</mat-icon>
              {{ 'evolution.share' | transloco }}
            </button>
            @if (shareSuccess()) {
              <span class="share-toast">{{ 'evolution.shareSuccess' | transloco }}</span>
            }
          </div>
        }

        <!-- Visit satisfaction list -->
        @if (data()?.visits?.length) {
          <section class="visits-section">
            @for (visit of data()!.visits; track visit.id) {
              <div class="visit-card">
                <div class="visit-info">
                  <div class="visit-date">{{ formatDate(visit.visitDate) }}</div>
                  <div class="visit-care">{{ visit.careName }}</div>
                </div>
                <div class="visit-rating">
                  @if (visit.satisfactionScore) {
                    <div class="stars-display">
                      @for (star of starsArray; track star) {
                        <mat-icon class="star-filled">
                          {{ star <= visit.satisfactionScore! ? 'star' : 'star_border' }}
                        </mat-icon>
                      }
                    </div>
                  } @else {
                    <button
                      mat-stroked-button
                      class="rate-btn"
                      (click)="openRateDialog(visit)"
                    >
                      <mat-icon>star_border</mat-icon>
                      {{ 'evolution.rate' | transloco }}
                    </button>
                  }
                </div>
              </div>
            }
          </section>
        }

        <!-- RGPD Consent Section -->
        <section class="consent-section">
          <h2>{{ 'evolution.consent.title' | transloco }}</h2>

          <div class="consent-toggle">
            <div class="toggle-info">
              <span class="toggle-label">{{ 'evolution.consent.allowPhotos' | transloco }}</span>
              <span class="toggle-desc">{{ 'evolution.consent.allowPhotosDesc' | transloco }}</span>
            </div>
            <mat-slide-toggle
              [checked]="consentPhotos()"
              (change)="onConsentPhotosChange($event.checked)"
            ></mat-slide-toggle>
          </div>

          <div class="consent-toggle">
            <div class="toggle-info">
              <span class="toggle-label">{{ 'evolution.consent.allowPublicShare' | transloco }}</span>
              <span class="toggle-desc">{{ 'evolution.consent.allowPublicShareDesc' | transloco }}</span>
            </div>
            <mat-slide-toggle
              [checked]="consentPublicShare()"
              (change)="onConsentPublicShareChange($event.checked)"
            ></mat-slide-toggle>
          </div>

          <div class="consent-delete">
            <p class="rgpd-note">{{ 'evolution.consent.rgpdNote' | transloco }}</p>
            <button
              mat-flat-button
              color="warn"
              class="delete-btn"
              (click)="onDeletePhotos()"
            >
              <mat-icon>delete_forever</mat-icon>
              {{ 'evolution.consent.deleteAll' | transloco }}
            </button>
          </div>
        </section>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      max-width: 600px;
      margin: 0 auto;
      padding: 16px;
    }

    .loading-container,
    .error-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      padding: 80px 0;
      color: #999;

      mat-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
      }
    }

    .evolution-page {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    /* Header */
    .evo-header {
      text-align: center;

      h1 {
        font-size: 24px;
        font-weight: 600;
        color: var(--mat-sys-on-surface, #1a1a1a);
        margin: 0 0 12px;
      }
    }

    .evo-stats {
      display: flex;
      justify-content: center;
      gap: 32px;
    }

    .stat {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
    }

    .stat-value {
      font-size: 28px;
      font-weight: 700;
      color: var(--mat-sys-primary, #a8385d);
    }

    .stat-label {
      font-size: 12px;
      color: #888;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    /* Reminder */
    .reminder-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      background: var(--mat-sys-primary-container, #fce4ec);
      color: var(--mat-sys-on-primary-container, #5c1030);
      border-radius: 12px;
      font-size: 14px;

      mat-icon {
        color: var(--mat-sys-primary, #a8385d);
        flex-shrink: 0;
      }
    }

    /* Comparison */
    .comparison-section {
      h2 {
        font-size: 16px;
        font-weight: 600;
        margin: 0 0 12px;
        color: var(--mat-sys-on-surface, #1a1a1a);
      }
    }

    .date-selectors {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
    }

    .date-select {
      flex: 1;
    }

    .vs-label {
      font-size: 13px;
      font-weight: 600;
      color: #888;
      flex-shrink: 0;
    }

    /* Before/After slider */
    .ba-container {
      position: relative;
      width: 100%;
      aspect-ratio: 3 / 4;
      border-radius: 16px;
      overflow: hidden;
      cursor: ew-resize;
      user-select: none;
      touch-action: none;
      background: #111;
    }

    .ba-img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      -webkit-user-drag: none;
      user-select: none;
      pointer-events: none;
    }

    .ba-before {
      z-index: 2;
    }

    .ba-after {
      z-index: 1;
    }

    .ba-handle {
      position: absolute;
      top: 0;
      bottom: 0;
      z-index: 3;
      display: flex;
      flex-direction: column;
      align-items: center;
      transform: translateX(-50%);
      pointer-events: none;
    }

    .ba-handle-line {
      flex: 1;
      width: 2px;
      background: #fff;
      box-shadow: 0 0 4px rgba(0, 0, 0, 0.4);
    }

    .ba-handle-circle {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);

      mat-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
        color: #333;
      }
    }

    .ba-label {
      position: absolute;
      top: 12px;
      z-index: 4;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      color: #fff;
      background: rgba(0, 0, 0, 0.5);
      pointer-events: none;
    }

    .ba-label-left {
      left: 12px;
    }

    .ba-label-right {
      right: 12px;
    }

    .no-photos-hint {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 40px 0;
      color: #aaa;

      mat-icon {
        font-size: 40px;
        width: 40px;
        height: 40px;
      }

      p {
        margin: 0;
        font-size: 14px;
      }
    }

    /* Timeline */
    .timeline-section {
      overflow: hidden;
    }

    .timeline-scroll {
      display: flex;
      gap: 10px;
      overflow-x: auto;
      padding: 4px 0;
      scrollbar-width: none;

      &::-webkit-scrollbar {
        display: none;
      }
    }

    .timeline-thumb {
      flex-shrink: 0;
      width: 64px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      background: none;
      border: 2px solid transparent;
      border-radius: 10px;
      padding: 2px;
      cursor: pointer;
      transition: border-color 200ms ease;

      &.active {
        border-color: var(--mat-sys-primary, #a8385d);
      }

      img {
        width: 56px;
        height: 56px;
        border-radius: 8px;
        object-fit: cover;
      }
    }

    .thumb-date {
      font-size: 10px;
      color: #888;
      white-space: nowrap;
    }

    /* Share */
    .share-section {
      display: flex;
      align-items: center;
      gap: 12px;
      justify-content: center;
    }

    .share-btn {
      background: var(--mat-sys-primary, #a8385d) !important;
      color: #fff !important;
      border-radius: 24px !important;
      padding: 0 24px !important;

      mat-icon {
        margin-right: 6px;
      }
    }

    .share-toast {
      font-size: 13px;
      color: var(--mat-sys-primary, #a8385d);
      font-weight: 500;
      animation: fadeIn 200ms ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    /* Empty state */
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 60px 0;
      color: #bbb;

      mat-icon {
        font-size: 56px;
        width: 56px;
        height: 56px;
      }

      p {
        margin: 0;
        font-size: 15px;
      }
    }

    /* Visits list */
    .visits-section {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .visit-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      background: var(--mat-sys-surface-container-low, #f8f8f8);
      border-radius: 12px;
      gap: 12px;
    }

    .visit-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }

    .visit-date {
      font-size: 13px;
      font-weight: 600;
      color: var(--mat-sys-on-surface, #1a1a1a);
    }

    .visit-care {
      font-size: 12px;
      color: var(--mat-sys-primary, #a8385d);
    }

    .visit-salon {
      font-size: 11px;
      color: #999;
    }

    .visit-rating {
      flex-shrink: 0;
    }

    .stars-display {
      display: flex;
      gap: 1px;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #ffc107;
      }
    }

    .rate-btn {
      font-size: 12px !important;
      border-radius: 20px !important;

      mat-icon {
        font-size: 16px;
        width: 16px;
        height: 16px;
        margin-right: 4px;
      }
    }

    /* Consent / RGPD */
    .consent-section {
      padding-top: 8px;
      border-top: 1px solid var(--mat-sys-outline-variant, #e0e0e0);

      h2 {
        font-size: 16px;
        font-weight: 600;
        margin: 0 0 16px;
        color: var(--mat-sys-on-surface, #1a1a1a);
      }
    }

    .consent-toggle {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 0;
      gap: 16px;

      & + .consent-toggle {
        border-top: 1px solid var(--mat-sys-outline-variant, #eee);
      }
    }

    .toggle-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .toggle-label {
      font-size: 14px;
      font-weight: 500;
      color: var(--mat-sys-on-surface, #1a1a1a);
    }

    .toggle-desc {
      font-size: 12px;
      color: #888;
    }

    .consent-delete {
      margin-top: 20px;
      padding-top: 16px;
      border-top: 1px solid var(--mat-sys-outline-variant, #eee);
    }

    .rgpd-note {
      font-size: 12px;
      color: #999;
      margin: 0 0 12px;
      font-style: italic;
    }

    .delete-btn {
      border-radius: 24px !important;

      mat-icon {
        margin-right: 6px;
      }
    }
  `,
})
export class ClientEvolutionComponent {
  private readonly trackingService = inject(ClientTrackingService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly dialog = inject(MatDialog);
  private readonly transloco = inject(TranslocoService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly data = signal<ClientHistoryResponse | null>(null);

  readonly consentPhotos = signal(false);
  readonly consentPublicShare = signal(false);

  readonly sliderPosition = signal(50);
  private dragging = false;

  readonly leftVisitId = signal<number | null>(null);
  readonly rightVisitId = signal<number | null>(null);

  readonly shareSuccess = signal(false);

  readonly starsArray = [1, 2, 3, 4, 5];

  readonly visitsWithPhotos = computed(() => {
    const d = this.data();
    if (!d) return [];
    return d.visits.filter((v) => v.photos && v.photos.length > 0);
  });

  readonly firstReminder = computed(() => {
    const d = this.data();
    if (!d?.reminders?.length) return null;
    // Find the first unsent reminder in the future
    const now = new Date();
    return (
      d.reminders.find((r) => !r.sent && new Date(r.recommendedDate) > now) ??
      d.reminders[0] ??
      null
    );
  });

  readonly leftVisit = computed(() => {
    const id = this.leftVisitId();
    return this.visitsWithPhotos().find((v) => v.id === id) ?? null;
  });

  readonly rightVisit = computed(() => {
    const id = this.rightVisitId();
    return this.visitsWithPhotos().find((v) => v.id === id) ?? null;
  });

  readonly leftPhoto = computed(() => {
    const visit = this.leftVisit();
    if (!visit) return null;
    const before = visit.photos.find((p) => p.photoType === 'BEFORE');
    return before?.imageUrl ?? visit.photos[0]?.imageUrl ?? null;
  });

  readonly rightPhoto = computed(() => {
    const visit = this.rightVisit();
    if (!visit) return null;
    const after = visit.photos.find((p) => p.photoType === 'AFTER');
    return after?.imageUrl ?? visit.photos[0]?.imageUrl ?? null;
  });

  readonly leftDateLabel = computed(() => {
    const visit = this.leftVisit();
    return visit ? this.formatMonthYear(visit.visitDate) : '';
  });

  readonly rightDateLabel = computed(() => {
    const visit = this.rightVisit();
    return visit ? this.formatMonthYear(visit.visitDate) : '';
  });

  constructor() {
    this.loadHistory();
  }

  private loadHistory(): void {
    this.loading.set(true);
    this.trackingService.getMyHistory().subscribe({
      next: (history) => {
        this.data.set(history);
        this.consentPhotos.set(history.profile.consentPhotos);
        this.consentPublicShare.set(history.profile.consentPublicShare);

        // Pre-select first and last visits with photos for comparison
        const withPhotos = history.visits.filter((v) => v.photos?.length > 0);
        if (withPhotos.length >= 2) {
          this.leftVisitId.set(withPhotos[withPhotos.length - 1].id);
          this.rightVisitId.set(withPhotos[0].id);
        } else if (withPhotos.length === 1) {
          this.leftVisitId.set(withPhotos[0].id);
          this.rightVisitId.set(withPhotos[0].id);
        }

        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load history');
        this.loading.set(false);
      },
    });
  }

  // -- Slider drag logic --

  onDragStart(e: MouseEvent): void {
    e.preventDefault();
    this.dragging = true;
    this.updateSliderFromEvent(e.clientX, e.currentTarget as HTMLElement);
  }

  onDragMove(e: MouseEvent): void {
    if (!this.dragging) return;
    this.updateSliderFromEvent(e.clientX, e.currentTarget as HTMLElement);
  }

  onDragEnd(): void {
    this.dragging = false;
  }

  onTouchStart(e: TouchEvent): void {
    this.dragging = true;
    if (e.touches.length > 0) {
      this.updateSliderFromEvent(e.touches[0].clientX, e.currentTarget as HTMLElement);
    }
  }

  onTouchMove(e: TouchEvent): void {
    if (!this.dragging) return;
    if (e.touches.length > 0) {
      this.updateSliderFromEvent(e.touches[0].clientX, e.currentTarget as HTMLElement);
    }
  }

  private updateSliderFromEvent(clientX: number, container: HTMLElement): void {
    const rect = container.getBoundingClientRect();
    const pct = ((clientX - rect.left) / rect.width) * 100;
    this.sliderPosition.set(Math.max(5, Math.min(95, pct)));
  }

  // -- Timeline --

  onTimelineClick(visit: VisitRecordResponse): void {
    // If left is empty or same as right, set left; otherwise set right
    if (!this.leftVisitId() || this.leftVisitId() === this.rightVisitId()) {
      this.leftVisitId.set(visit.id);
    } else {
      this.rightVisitId.set(visit.id);
    }
    this.sliderPosition.set(50);
  }

  getVisitThumb(visit: VisitRecordResponse): string {
    const photo = visit.photos.find((p) => p.photoType === 'AFTER') ?? visit.photos[0];
    return photo?.imageUrl ?? '';
  }

  // -- Share --

  onShare(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const url = window.location.href;
    if (navigator.share) {
      navigator
        .share({ title: this.transloco.translate('evolution.title'), url })
        .catch(() => {});
    } else {
      navigator.clipboard.writeText(url).then(() => {
        this.shareSuccess.set(true);
        setTimeout(() => this.shareSuccess.set(false), 2000);
      });
    }
  }

  // -- Rating --

  openRateDialog(visit: VisitRecordResponse): void {
    const ref = this.dialog.open(RateVisitDialogComponent, {
      width: '400px',
      data: { visitId: visit.id, careName: visit.careName },
    });

    ref.afterClosed().subscribe((result: { score: number; comment: string } | undefined) => {
      if (result) {
        this.trackingService.rateVisit(visit.id, result).subscribe({
          next: () => {
            // Update the visit in local state
            this.data.update((d) => {
              if (!d) return d;
              return {
                ...d,
                visits: d.visits.map((v) =>
                  v.id === visit.id
                    ? {
                        ...v,
                        satisfactionScore: result.score,
                        satisfactionComment: result.comment,
                      }
                    : v
                ),
              };
            });
          },
        });
      }
    });
  }

  // -- Consent --

  onConsentPhotosChange(checked: boolean): void {
    this.consentPhotos.set(checked);
    this.trackingService
      .updateMyConsent({
        consentPhotos: checked,
        consentPublicShare: this.consentPublicShare(),
      })
      .subscribe();
  }

  onConsentPublicShareChange(checked: boolean): void {
    this.consentPublicShare.set(checked);
    this.trackingService
      .updateMyConsent({
        consentPhotos: this.consentPhotos(),
        consentPublicShare: checked,
      })
      .subscribe();
  }

  onDeletePhotos(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const msg = this.transloco.translate('evolution.consent.deleteConfirm');
    if (confirm(msg)) {
      this.trackingService.deleteMyPhotos().subscribe({
        next: () => {
          // Clear photos from local state
          this.data.update((d) => {
            if (!d) return d;
            return {
              ...d,
              visits: d.visits.map((v) => ({ ...v, photos: [] })),
            };
          });
          this.leftVisitId.set(null);
          this.rightVisitId.set(null);
        },
      });
    }
  }

  // -- Helpers --

  imgUrl(path: string): string {
    if (!path) return '';
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    return path.startsWith('http') || path.startsWith('data:') ? path : base + path;
  }

  formatDate(dateStr: string): string {
    try {
      const d = new Date(dateStr + (dateStr.includes('T') ? '' : 'T00:00:00'));
      return d.toLocaleDateString(this.transloco.getActiveLang(), {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      });
    } catch {
      return dateStr;
    }
  }

  formatShortDate(dateStr: string): string {
    try {
      const d = new Date(dateStr + (dateStr.includes('T') ? '' : 'T00:00:00'));
      return d.toLocaleDateString(this.transloco.getActiveLang(), {
        day: 'numeric',
        month: 'short',
      });
    } catch {
      return dateStr;
    }
  }

  formatMonthYear(dateStr: string): string {
    try {
      const d = new Date(dateStr + (dateStr.includes('T') ? '' : 'T00:00:00'));
      return d.toLocaleDateString(this.transloco.getActiveLang(), {
        month: 'long',
        year: 'numeric',
      });
    } catch {
      return dateStr;
    }
  }

  weeksUntil(dateStr: string): number {
    try {
      const target = new Date(dateStr + (dateStr.includes('T') ? '' : 'T00:00:00'));
      const now = new Date();
      const diffMs = target.getTime() - now.getTime();
      return Math.max(0, Math.round(diffMs / (7 * 24 * 60 * 60 * 1000)));
    } catch {
      return 0;
    }
  }
}
