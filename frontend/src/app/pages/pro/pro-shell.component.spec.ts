import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { NavigationEnd, Router, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Subject } from 'rxjs';
import { ProShellComponent } from './pro-shell.component';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';

const READINESS_STUB = {
  slug: 'demo',
  name: false,
  hasCategory: false,
  hasContact: false,
  hasLogo: false,
  hasActiveCare: false,
  hasOpeningHours: false,
  canPublish: false,
  status: 'DRAFT',
};

describe('ProShellComponent', () => {
  let fixture: ComponentFixture<ProShellComponent>;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
      imports: [
        ProShellComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(ProShellComponent);
    httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // Flush the initial readiness request triggered by the store's onInit hook
    httpTesting.expectOne((req) => req.url.endsWith('/api/pro/tenant/readiness')).flush(READINESS_STUB);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('provides DashboardStore at the component level', () => {
    const store = fixture.debugElement.injector.get(DashboardStore);
    expect(store).toBeTruthy();
  });

  it('renders an onboarding indicator and a router outlet', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('app-onboarding-indicator')).not.toBeNull();
    expect(root.querySelector('router-outlet')).not.toBeNull();
  });

  it('reloads readiness when navigating to /pro/dashboard', () => {
    // Simulate a NavigationEnd event toward /pro/dashboard
    const router = TestBed.inject(Router);
    const events = router.events as unknown as Subject<unknown>;
    events.next(new NavigationEnd(1, '/pro/dashboard', '/pro/dashboard'));

    // Expect a second readiness request triggered by the router subscription
    const req = httpTesting.expectOne((req) => req.url.endsWith('/api/pro/tenant/readiness'));
    expect(req.request.method).toBe('GET');
    req.flush({ ...READINESS_STUB, name: true });
  });
});
