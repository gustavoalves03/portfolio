import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SalonPagePcComponent } from './salon-page-pc.component';
import { PublicSalonResponse } from '../../../features/salon-profile/models/salon-profile.model';
import { API_BASE_URL } from '../../../core/config/api-base-url.token';

describe('SalonPagePcComponent', () => {
  let component: SalonPagePcComponent;
  let fixture: ComponentFixture<SalonPagePcComponent>;

  function makeSalon(): PublicSalonResponse {
    return {
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
    };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        SalonPagePcComponent,
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
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SalonPagePcComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('salon', makeSalon());
    fixture.componentRef.setInput('slug', 'test-salon');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the booking button when bookingDisabled is false', () => {
    fixture.componentRef.setInput('bookingDisabled', false);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.book-btn');
    expect(btn).withContext('booking button should be rendered by default').not.toBeNull();
  });

  it('hides the booking button when bookingDisabled is true (pro mode)', () => {
    fixture.componentRef.setInput('bookingDisabled', true);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('.book-btn');
    expect(btn).withContext('booking button must be hidden in pro mode').toBeNull();
  });
});
