import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { TenantReadiness } from '../../../features/dashboard/models/dashboard.model';
import { ProOnboardingWizardComponent } from './pro-onboarding-wizard.component';

function makeReadiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
  return {
    slug: 'demo',
    name: false,
    hasCategory: false,
    hasContact: false,
    hasLogo: false,
    hasActiveCare: false,
    hasOpeningHours: false,
    canPublish: false,
    status: 'DRAFT',
    ...overrides,
  };
}

describe('ProOnboardingWizardComponent', () => {
  let fixture: ComponentFixture<ProOnboardingWizardComponent>;
  let storeStub: any;
  let routerSpy: jasmine.SpyObj<Router>;

  function setup(initialReadiness: TenantReadiness | null) {
    const readinessSignal = signal<TenantReadiness | null>(initialReadiness);
    storeStub = {
      readiness: readinessSignal,
      isActive: signal(initialReadiness?.status === 'ACTIVE'),
      isDraft: signal(initialReadiness?.status === 'DRAFT'),
      canPublish: signal(initialReadiness?.canPublish ?? false),
      loadReadiness: jasmine.createSpy('loadReadiness'),
    };
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: 'http://t' },
        { provide: DashboardStore, useValue: storeStub },
        { provide: Router, useValue: routerSpy },
      ],
      imports: [
        ProOnboardingWizardComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(ProOnboardingWizardComponent);
    fixture.detectChanges();
    return readinessSignal;
  }

  it('starts at welcome when nothing is filled', () => {
    setup(makeReadiness());
    expect(fixture.nativeElement.querySelector('app-welcome-step')).not.toBeNull();
  });

  it('starts at the first non-done step when some readiness is true', () => {
    setup(makeReadiness({ name: true, hasContact: true }));
    // hasLogo is the first false → logo step
    expect(fixture.nativeElement.querySelector('app-logo-step')).not.toBeNull();
  });

  it('redirects to /pro/dashboard when status is ACTIVE', () => {
    setup(makeReadiness({ status: 'ACTIVE', canPublish: true }));
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
  });

  it('exit sets sessionStorage flag and navigates to dashboard', () => {
    setup(makeReadiness());
    sessionStorage.removeItem('pf_skipOnboarding');
    fixture.componentInstance['onExit']();
    expect(sessionStorage.getItem('pf_skipOnboarding')).toBe('1');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
  });
});
