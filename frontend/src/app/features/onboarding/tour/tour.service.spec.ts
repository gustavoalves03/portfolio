import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';
import { DashboardStore } from '../../dashboard/store/dashboard.store';
import { TenantReadiness } from '../../dashboard/models/dashboard.model';
import { TourService } from './tour.service';

function readiness(overrides: Partial<TenantReadiness> = {}): TenantReadiness {
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

describe('TourService', () => {
  let service: TourService;
  let router: jasmine.SpyObj<Router>;
  let readinessSig: ReturnType<typeof signal<TenantReadiness | null>>;

  function setup(initialReadiness: TenantReadiness | null) {
    readinessSig = signal<TenantReadiness | null>(initialReadiness);
    const storeStub = { readiness: readinessSig };
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl'], { url: '/pro/dashboard' });
    router.navigateByUrl.and.returnValue(Promise.resolve(true));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: 'http://t' },
        { provide: DashboardStore, useValue: storeStub },
        { provide: Router, useValue: router },
        TourService,
      ],
    });
    service = TestBed.inject(TourService);
  }

  it('start() is a no-op when readiness is null', () => {
    setup(null);
    service.start('logo');
    expect(service.active()).toBeFalse();
    expect(service.currentStep()).toBeNull();
  });

  it('start() is a no-op when tenant is ACTIVE and canPublish', () => {
    setup(
      readiness({
        status: 'ACTIVE',
        canPublish: true,
        name: true,
        hasContact: true,
        hasLogo: true,
        hasCategory: true,
        hasActiveCare: true,
        hasOpeningHours: true,
      })
    );
    service.start();
    expect(service.active()).toBeFalse();
  });

  it('start("logo") sets currentStep to the logo step and navigates to /pro/salon', async () => {
    setup(readiness());
    service.start('logo');
    // navigateByUrl returns a promise; flush it so the .then(() => set currentStep) resolves
    await Promise.resolve();
    expect(service.active()).toBeTrue();
    expect(service.currentStep()?.key).toBe('logo');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/pro/salon');
  });

  it('start() with no key picks the first missing condition', async () => {
    setup(readiness({ name: true })); // hasContact is the first missing
    service.start();
    await Promise.resolve();
    expect(service.currentStep()?.key).toBe('contact');
  });

  it('auto-advances 1500ms after readiness flips the current step to true', async () => {
    setup(readiness());
    jasmine.clock().install();
    try {
      service.start('name');
      await Promise.resolve();
      expect(service.currentStep()?.key).toBe('name');

      // Flip the name flag → effect should call advance(), setting inTransition=true
      readinessSig.set(readiness({ name: true }));
      TestBed.tick();
      expect(service.inTransition()).toBeTrue();

      // After 1500ms, advance() should pick the next missing (contact)
      jasmine.clock().tick(1500);
      // navigateByUrl returns a promise, let it flush
      await Promise.resolve();
      expect(service.inTransition()).toBeFalse();
      expect(service.currentStep()?.key).toBe('contact');
    } finally {
      jasmine.clock().uninstall();
    }
  });

  it('stop() resets all signals', async () => {
    setup(readiness());
    service.start('logo');
    await Promise.resolve();
    service.stop();
    expect(service.active()).toBeFalse();
    expect(service.currentStep()).toBeNull();
    expect(service.inTransition()).toBeFalse();
  });
});
