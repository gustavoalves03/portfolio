import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { SalonPageComponent } from './salon-page.component';
import { AuthService } from '../../core/auth/auth.service';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('SalonPageComponent', () => {
  let component: SalonPageComponent;
  let fixture: ComponentFixture<SalonPageComponent>;
  let httpMock: HttpTestingController;

  function configure(opts: { authenticated: boolean; clientMode: boolean }) {
    const auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated', 'isClientMode']);
    auth.isAuthenticated.and.returnValue(opts.authenticated);
    auth.isClientMode.and.returnValue(opts.clientMode);

    TestBed.configureTestingModule({
      imports: [
        SalonPageComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {}, en: {} },
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr', 'en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        provideTranslocoLocale({ defaultLocale: 'fr-FR', langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' } }),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: () => 'test-salon' },
              queryParamMap: { get: () => null },
            },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(SalonPageComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  }

  function seedSalonWithOneCare(): void {
    fixture.detectChanges();
    const req = httpMock.expectOne(r => r.url.endsWith('/api/salon/test-salon'));
    req.flush({
      slug: 'test-salon',
      name: 'Test Salon',
      status: 'ACTIVE',
      description: null,
      logoUrl: null,
      heroImageUrl: null,
      addressStreet: null,
      addressCity: null,
      addressPostalCode: null,
      addressCountry: null,
      phone: null,
      contactEmail: null,
      categories: [
        {
          name: 'Visage',
          cares: [
            {
              id: 1,
              name: 'Soin du visage',
              description: '',
              price: 50,
              duration: 60,
              imageUrls: [],
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
  }

  it('should create', () => {
    configure({ authenticated: false, clientMode: true });
    expect(component).toBeTruthy();
  });

  it('renders the booking button when not authenticated (visitor)', () => {
    configure({ authenticated: false, clientMode: true });
    seedSalonWithOneCare();
    const btn = fixture.nativeElement.querySelector('.book-btn');
    expect(btn).withContext('booking button should be rendered for visitor').not.toBeNull();
  });

  it('renders the booking button when authenticated in client mode', () => {
    configure({ authenticated: true, clientMode: true });
    seedSalonWithOneCare();
    const btn = fixture.nativeElement.querySelector('.book-btn');
    expect(btn).withContext('booking button should be rendered in client mode').not.toBeNull();
  });

  it('hides the booking button when authenticated with an active tenant (pro mode)', () => {
    configure({ authenticated: true, clientMode: false });
    seedSalonWithOneCare();
    const btn = fixture.nativeElement.querySelector('.book-btn');
    expect(btn).withContext('booking button must be hidden in pro mode').toBeNull();
  });
});
