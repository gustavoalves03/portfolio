import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of } from 'rxjs';
import { TenantSwitcherComponent } from './tenant-switcher.component';
import { AuthService } from '../../../../core/auth/auth.service';
import { TenantSummary } from '../../../../core/auth/auth.model';

describe('TenantSwitcherComponent', () => {
  let fixture: ComponentFixture<TenantSwitcherComponent>;
  let mockAuth: any;

  function setupAuth(opts: {
    isAuthenticated?: boolean;
    availableTenants?: TenantSummary[];
    activeTenantId?: number | null;
  } = {}): void {
    const authenticated = signal(opts.isAuthenticated ?? true);
    const tenants = signal<TenantSummary[]>(opts.availableTenants ?? []);
    const active = signal<number | null>(opts.activeTenantId ?? null);

    mockAuth = {
      isAuthenticated: authenticated,
      availableTenants: tenants,
      activeTenantId: active,
      switchTenant: jasmine.createSpy('switchTenant').and.returnValue(of({} as any)),
      navigateByRole: jasmine.createSpy('navigateByRole'),
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [
        TenantSwitcherComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              common: { clientMode: 'Mode client' },
              errors: { tenantSwitchFailed: 'Erreur' },
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: mockAuth },
      ],
    });
    fixture = TestBed.createComponent(TenantSwitcherComponent);
    fixture.detectChanges();
  }

  it('is hidden when availableTenants is empty', () => {
    setupAuth({ availableTenants: [] });
    const chip = fixture.nativeElement.querySelector('[data-testid="tenant-switcher-chip"]');
    expect(chip).toBeNull();
  });

  it('is visible when user has at least one tenant', () => {
    setupAuth({
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      activeTenantId: 42,
    });
    const chip = fixture.nativeElement.querySelector('[data-testid="tenant-switcher-chip"]');
    expect(chip).not.toBeNull();
  });

  it('calls auth.switchTenant when a tenant menu item is clicked', () => {
    setupAuth({
      availableTenants: [
        { id: 42, slug: 'salon-x', name: 'Salon X' },
        { id: 43, slug: 'salon-y', name: 'Salon Y' },
      ],
      activeTenantId: 42,
    });
    (fixture.componentInstance as any).switch(43);
    expect(mockAuth.switchTenant).toHaveBeenCalledWith(43);
  });

  it('calls auth.switchTenant(null) when client mode is clicked', () => {
    setupAuth({
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      activeTenantId: 42,
    });
    (fixture.componentInstance as any).switch(null);
    expect(mockAuth.switchTenant).toHaveBeenCalledWith(null);
  });

  it('skips the switch when clicking the already-active tenant', () => {
    setupAuth({
      availableTenants: [{ id: 42, slug: 'salon-x', name: 'Salon X' }],
      activeTenantId: 42,
    });
    (fixture.componentInstance as any).switch(42);
    expect(mockAuth.switchTenant).not.toHaveBeenCalled();
  });
});
