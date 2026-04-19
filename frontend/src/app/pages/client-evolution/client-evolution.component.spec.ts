import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, Subject, throwError } from 'rxjs';

import { ClientEvolutionComponent } from './client-evolution.component';
import { ClientTrackingService } from '../../features/tracking/client-tracking.service';
import { ClientHistoryResponse } from '../../features/tracking/tracking.model';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

function makeHistory(overrides: Partial<ClientHistoryResponse> = {}): ClientHistoryResponse {
  return {
    clientName: 'Marie D.',
    clientEmail: 'marie@test.fr',
    profile: {
      id: 1,
      userId: 42,
      notes: null,
      skinType: null,
      hairType: null,
      allergies: null,
      preferences: null,
      consentPhotos: false,
      consentPublicShare: false,
      consentGivenAt: null,
      createdAt: '2026-01-01T10:00:00',
      updatedAt: null,
      updatedByName: null,
    },
    visits: [
      {
        id: 10,
        clientProfileId: 1,
        bookingId: 100,
        careId: 1,
        careName: 'Soin visage',
        visitDate: '2026-02-01',
        practitionerNotes: null,
        productsUsed: null,
        satisfactionScore: null,
        satisfactionComment: null,
        createdAt: '2026-02-01T10:00:00',
        updatedAt: null,
        updatedByName: 'Sophie',
        photos: [
          { id: 1, photoType: 'BEFORE', imageUrl: '/uploads/v10-before.jpg', imageOrder: 0, uploadedByName: 'Sophie' },
          { id: 2, photoType: 'AFTER', imageUrl: '/uploads/v10-after.jpg', imageOrder: 1, uploadedByName: 'Sophie' },
        ],
      },
      {
        id: 11,
        clientProfileId: 1,
        bookingId: 101,
        careId: 1,
        careName: 'Soin hydratant',
        visitDate: '2026-03-01',
        practitionerNotes: null,
        productsUsed: null,
        satisfactionScore: null,
        satisfactionComment: null,
        createdAt: '2026-03-01T10:00:00',
        updatedAt: null,
        updatedByName: 'Sophie',
        photos: [
          { id: 3, photoType: 'AFTER', imageUrl: '/uploads/v11-after.jpg', imageOrder: 0, uploadedByName: 'Sophie' },
        ],
      },
      {
        id: 12,
        clientProfileId: 1,
        bookingId: null,
        careId: 2,
        careName: 'Massage',
        visitDate: '2026-01-15',
        practitionerNotes: null,
        productsUsed: null,
        satisfactionScore: null,
        satisfactionComment: null,
        createdAt: '2026-01-15T10:00:00',
        updatedAt: null,
        updatedByName: null,
        photos: [],
      },
    ],
    reminders: [],
    ...overrides,
  };
}

describe('ClientEvolutionComponent', () => {
  let trackingService: jasmine.SpyObj<ClientTrackingService>;
  let dialog: jasmine.SpyObj<MatDialog>;

  function setup(historyObs: any = of(makeHistory())) {
    trackingService = jasmine.createSpyObj<ClientTrackingService>('ClientTrackingService', [
      'getMyHistory',
      'updateMyConsent',
      'rateVisit',
      'deleteMyPhotos',
    ]);
    trackingService.getMyHistory.and.returnValue(historyObs);
    trackingService.updateMyConsent.and.returnValue(of(undefined));
    trackingService.rateVisit.and.returnValue(of(undefined));
    trackingService.deleteMyPhotos.and.returnValue(of(undefined));

    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    TestBed.configureTestingModule({
      imports: [
        ClientEvolutionComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              evolution: {
                title: 'Mon évolution',
                visits: 'visites',
                reminder: 'Prochain soin dans',
                weeks: 'semaines',
                compare: 'Comparer',
                vs: 'vs',
                noPhotos: 'Pas encore de photos',
                noVisits: 'Pas encore de visites',
                rate: 'Noter',
                share: 'Partager',
                shareSuccess: 'Lien copié',
                consent: {
                  title: 'Consentement',
                  allowPhotos: 'Autoriser les photos',
                  allowPhotosDesc: 'Description',
                  allowPublicShare: 'Partage public',
                  allowPublicShareDesc: 'Description',
                  rgpdNote: 'Note RGPD',
                  deleteAll: 'Tout supprimer',
                  deleteConfirm: 'Confirmer ?',
                },
              },
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideNoopAnimations(),
        { provide: ClientTrackingService, useValue: trackingService },
        { provide: MatDialog, useValue: dialog },
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
      ],
    });

    const fixture = TestBed.createComponent(ClientEvolutionComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('creates the component', () => {
    const fixture = setup();
    expect(fixture.componentInstance).toBeTruthy();
    expect(trackingService.getMyHistory).toHaveBeenCalledTimes(1);
  });

  it('shows a loading spinner before data arrives', () => {
    const pending = new Subject<ClientHistoryResponse>();
    const fixture = setup(pending.asObservable());

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.loading-container')).toBeTruthy();
    expect(fixture.componentInstance.loading()).toBeTrue();
  });

  it('renders content after data loads', () => {
    const fixture = setup();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.loading-container')).toBeFalsy();
    expect(host.querySelector('.evo-header')).toBeTruthy();

    // Visit count stat reflects visits.length
    const stat = host.querySelector('.stat-value');
    expect(stat?.textContent?.trim()).toBe('3');

    // Visits section rendered
    const cards = host.querySelectorAll('.visit-card');
    expect(cards.length).toBe(3);
  });

  it('pre-selects first and last visits with photos for comparison', () => {
    const fixture = setup();
    const c = fixture.componentInstance;
    // visitsWithPhotos returns [v10, v11] in source order
    expect(c.visitsWithPhotos().length).toBe(2);
    // With >= 2 photos-visits, left = last (v11), right = first (v10) from source array
    // Source code: withPhotos[withPhotos.length - 1].id → v11 (id=11), withPhotos[0].id → v10 (id=10)
    expect(c.leftVisitId()).toBe(11);
    expect(c.rightVisitId()).toBe(10);
  });

  it('shows an error message on HTTP failure', () => {
    const fixture = setup(throwError(() => new Error('boom')));
    const host = fixture.nativeElement as HTMLElement;

    expect(fixture.componentInstance.error()).toBe('Failed to load history');
    expect(fixture.componentInstance.loading()).toBeFalse();
    expect(host.querySelector('.error-container')).toBeTruthy();
  });

  it('opens the rate-visit dialog when openRateDialog is called', () => {
    const fixture = setup();
    const afterClosed$ = of(undefined);
    const ref = { afterClosed: () => afterClosed$ } as unknown as MatDialogRef<any>;
    const openSpy = jasmine
      .createSpy('open')
      .and.returnValue(ref);
    (fixture.componentInstance as any).dialog = { open: openSpy };

    const visit = fixture.componentInstance.data()!.visits[2]; // Massage, no photos
    fixture.componentInstance.openRateDialog(visit);

    expect(openSpy).toHaveBeenCalledTimes(1);
    const config = openSpy.calls.mostRecent().args[1];
    expect(config?.data).toEqual({ visitId: 12, careName: 'Massage' });
  });

  it('persists rating via service and updates local visit satisfaction', () => {
    const fixture = setup();
    const ref = {
      afterClosed: () => of({ score: 4, comment: 'Super' }),
    } as unknown as MatDialogRef<any>;
    (fixture.componentInstance as any).dialog = {
      open: jasmine.createSpy('open').and.returnValue(ref),
    };

    const visit = fixture.componentInstance.data()!.visits[0];
    fixture.componentInstance.openRateDialog(visit);

    expect(trackingService.rateVisit).toHaveBeenCalledWith(10, { score: 4, comment: 'Super' });
    const updated = fixture.componentInstance.data()!.visits.find((v) => v.id === 10);
    expect(updated?.satisfactionScore).toBe(4);
    expect(updated?.satisfactionComment).toBe('Super');
  });

  it('does not call rateVisit when dialog is dismissed', () => {
    const fixture = setup();
    const ref = { afterClosed: () => of(undefined) } as unknown as MatDialogRef<any>;
    (fixture.componentInstance as any).dialog = {
      open: jasmine.createSpy('open').and.returnValue(ref),
    };

    const visit = fixture.componentInstance.data()!.visits[0];
    fixture.componentInstance.openRateDialog(visit);

    expect(trackingService.rateVisit).not.toHaveBeenCalled();
  });

  it('updates consent through the service on toggle', () => {
    const fixture = setup();
    const c = fixture.componentInstance;

    c.onConsentPhotosChange(true);
    expect(c.consentPhotos()).toBeTrue();
    expect(trackingService.updateMyConsent).toHaveBeenCalledWith({
      consentPhotos: true,
      consentPublicShare: false,
    });

    c.onConsentPublicShareChange(true);
    expect(c.consentPublicShare()).toBeTrue();
    expect(trackingService.updateMyConsent).toHaveBeenCalledWith({
      consentPhotos: true,
      consentPublicShare: true,
    });
  });

  it('onTimelineClick swaps left/right visit ids', () => {
    const fixture = setup();
    const c = fixture.componentInstance;
    const v10 = c.data()!.visits[0]; // id 10
    const v11 = c.data()!.visits[1]; // id 11

    // Initial: left=11, right=10
    c.onTimelineClick(v10);
    // left !== right, so: right gets updated to v10.id=10 (already was 10)
    expect(c.rightVisitId()).toBe(10);

    // Reset slider to 50
    expect(c.sliderPosition()).toBe(50);

    // Force left === right scenario
    c.leftVisitId.set(10);
    c.rightVisitId.set(10);
    c.onTimelineClick(v11);
    // Since left === right, left is updated
    expect(c.leftVisitId()).toBe(11);
  });

  it('imgUrl prefixes relative paths with the API base', () => {
    const fixture = setup();
    const c = fixture.componentInstance;
    expect(c.imgUrl('/foo.jpg')).toBe('http://localhost:8080/foo.jpg');
    expect(c.imgUrl('http://cdn.example/x.jpg')).toBe('http://cdn.example/x.jpg');
    expect(c.imgUrl('data:image/png;base64,xxxx')).toBe('data:image/png;base64,xxxx');
    expect(c.imgUrl('')).toBe('');
  });

  it('weeksUntil returns a non-negative integer number of weeks', () => {
    const fixture = setup();
    const c = fixture.componentInstance;
    const inFuture = new Date(Date.now() + 21 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    expect(c.weeksUntil(inFuture)).toBe(3);

    const past = new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
    expect(c.weeksUntil(past)).toBe(0);
  });

  it('empty state is shown when there are no photos', () => {
    const noPhotos = makeHistory({
      visits: [
        {
          id: 50,
          clientProfileId: 1,
          bookingId: null,
          careId: 1,
          careName: 'Soin',
          visitDate: '2026-02-01',
          practitionerNotes: null,
          productsUsed: null,
          satisfactionScore: null,
          satisfactionComment: null,
          createdAt: '2026-02-01T10:00:00',
          updatedAt: null,
          updatedByName: null,
          photos: [],
        },
      ],
    });
    const fixture = setup(of(noPhotos));
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.empty-state')).toBeTruthy();
  });
});
