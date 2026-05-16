import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SalonPostsViewerComponent } from './salon-posts-viewer.component';
import { AuthService } from '../../../core/auth/auth.service';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

describe('SalonPostsViewerComponent — bookingDisabled', () => {
  let component: SalonPostsViewerComponent;
  let fixture: ComponentFixture<SalonPostsViewerComponent>;
  let httpMock: HttpTestingController;

  function configure(opts: { authenticated: boolean; clientMode: boolean }) {
    const auth = jasmine.createSpyObj<AuthService>('AuthService', [
      'isAuthenticated',
      'isClientMode',
      'hasRole',
    ]);
    auth.isAuthenticated.and.returnValue(opts.authenticated);
    auth.isClientMode.and.returnValue(opts.clientMode);
    auth.hasRole.and.returnValue(false);

    TestBed.configureTestingModule({
      imports: [
        SalonPostsViewerComponent,
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
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
        { provide: AuthService, useValue: auth },
      ],
    });

    fixture = TestBed.createComponent(SalonPostsViewerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('slug', 'test-salon');
    httpMock = TestBed.inject(HttpTestingController);
  }

  function seedOnePostWithCare(): void {
    fixture.detectChanges();
    const req = httpMock.expectOne(r => r.url.includes('/api/salon/test-salon/posts'));
    req.flush({
      content: [
        {
          id: 1,
          tenantSlug: 'test-salon',
          tenantName: 'Test Salon',
          title: 'Mon post',
          body: 'Description',
          imageUrls: [],
          beforeImageUrl: null,
          afterImageUrl: null,
          careId: 42,
          careName: 'Soin du visage',
          createdAt: new Date().toISOString(),
        },
      ],
      totalElements: 1,
      number: 0,
      size: 10,
    });
    fixture.detectChanges();
  }

  it('exposes bookingDisabled=false for an unauthenticated visitor', () => {
    configure({ authenticated: false, clientMode: true });
    expect(component.bookingDisabled()).toBe(false);
  });

  it('exposes bookingDisabled=false in client mode', () => {
    configure({ authenticated: true, clientMode: true });
    expect(component.bookingDisabled()).toBe(false);
  });

  it('exposes bookingDisabled=true in pro mode (authenticated with active tenant)', () => {
    configure({ authenticated: true, clientMode: false });
    expect(component.bookingDisabled()).toBe(true);
  });

  it('hides the post book button when in pro mode', () => {
    configure({ authenticated: true, clientMode: false });
    seedOnePostWithCare();
    const btn = fixture.nativeElement.querySelector('.book-btn');
    expect(btn).withContext('post book button must be hidden in pro mode').toBeNull();
  });

  it('renders the post book button when in client mode', () => {
    configure({ authenticated: true, clientMode: true });
    seedOnePostWithCare();
    const btn = fixture.nativeElement.querySelector('.book-btn');
    expect(btn).withContext('post book button should be rendered in client mode').not.toBeNull();
  });
});
