import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { PreviewShareComponent } from './preview-share.component';
import { PreviewTokenService } from '../services/preview-token.service';
import { PreviewTokenResponse } from '../models/preview-token.model';

function token(overrides: Partial<PreviewTokenResponse> = {}): PreviewTokenResponse {
  return {
    id: 1,
    token: 't',
    shareUrl: '/salon/demo?preview=t',
    createdAt: '2026-05-06T10:00:00',
    expiresAt: null,
    revokedAt: null,
    ...overrides,
  };
}

describe('PreviewShareComponent', () => {
  let fixture: ComponentFixture<PreviewShareComponent>;
  let serviceSpy: jasmine.SpyObj<PreviewTokenService>;

  beforeEach(() => {
    serviceSpy = jasmine.createSpyObj<PreviewTokenService>('PreviewTokenService', [
      'list',
      'create',
      'revoke',
    ]);
    serviceSpy.list.and.returnValue(of([]));
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: PreviewTokenService, useValue: serviceSpy },
      ],
      imports: [
        PreviewShareComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
  });

  it('loads tokens on init', () => {
    expect(serviceSpy.list).toHaveBeenCalled();
  });

  it('shows the empty state when there is no active token', () => {
    serviceSpy.list.and.returnValue(of([token({ revokedAt: '2026-05-06T11:00:00' })]));
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const empty = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="no-links"]');
    expect(empty).not.toBeNull();
  });

  it('shows one row per active token', () => {
    serviceSpy.list.and.returnValue(of([token({ id: 1 }), token({ id: 2, token: 'b' })]));
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll(
      '[data-testid="token-row"]',
    );
    expect(rows.length).toBe(2);
  });

  it('calls service.create when the generate button is clicked', () => {
    serviceSpy.create.and.returnValue(of(token({ id: 5, token: 'new' })));
    serviceSpy.list.and.returnValue(of([]));
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const btn = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '[data-testid="generate-btn"]',
    );
    btn?.click();
    expect(serviceSpy.create).toHaveBeenCalled();
  });

  it('calls service.revoke when the revoke button is clicked (after confirm)', () => {
    serviceSpy.list.and.returnValue(of([token({ id: 7 })]));
    serviceSpy.revoke.and.returnValue(of(undefined));
    spyOn(window, 'confirm').and.returnValue(true);
    fixture = TestBed.createComponent(PreviewShareComponent);
    fixture.detectChanges();
    const btn = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '[data-testid="revoke-btn-7"]',
    );
    btn?.click();
    expect(serviceSpy.revoke).toHaveBeenCalledWith(7);
  });
});
