import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal, WritableSignal } from '@angular/core';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { patchState } from '@ngrx/signals';
import { DashboardStore } from '../../../features/dashboard/store/dashboard.store';
import { TenantReadiness } from '../../../features/dashboard/models/dashboard.model';
import { OnboardingIndicatorComponent } from './onboarding-indicator.component';
import { ONBOARDING_BREAKPOINT } from './breakpoint.token';

function readiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
  return {
    slug: 'demo',
    name: false,
    hasCategory: false,
    hasActiveCare: false,
    hasOpeningHours: false,
    canPublish: false,
    status: 'DRAFT',
    ...overrides,
  };
}

describe('OnboardingIndicatorComponent', () => {
  let fixture: ComponentFixture<OnboardingIndicatorComponent>;
  let store: InstanceType<typeof DashboardStore>;
  let isDesktop: WritableSignal<boolean>;

  function setup(initialDesktop: boolean) {
    isDesktop = signal(initialDesktop);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        DashboardStore,
        { provide: ONBOARDING_BREAKPOINT, useValue: () => isDesktop },
      ],
      imports: [
        OnboardingIndicatorComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    store = TestBed.inject(DashboardStore);
    fixture = TestBed.createComponent(OnboardingIndicatorComponent);
    fixture.detectChanges();
  }

  it('renders nothing when readiness is null', () => {
    setup(false);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="onboarding-pill"]')).toBeNull();
    expect(root.querySelector('[data-testid="onboarding-stepper"]')).toBeNull();
  });

  it('renders nothing when status is ACTIVE', () => {
    setup(false);
    patchState(store as any, { readiness: readiness({ status: 'ACTIVE', name: true }) });
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="onboarding-pill"]')).toBeNull();
    expect(root.querySelector('[data-testid="onboarding-stepper"]')).toBeNull();
  });

  it('renders pill on mobile when status is DRAFT', () => {
    setup(false);
    patchState(store as any, { readiness: readiness({ name: true }) });
    fixture.detectChanges();
    const pill = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="onboarding-pill"]'
    );
    expect(pill).not.toBeNull();
  });

  it('does not render pill on desktop', () => {
    setup(true);
    patchState(store as any, { readiness: readiness({ name: true }) });
    fixture.detectChanges();
    const pill = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="onboarding-pill"]'
    );
    expect(pill).toBeNull();
  });
});
