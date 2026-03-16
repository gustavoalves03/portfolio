import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { SalonPageComponent } from './salon-page.component';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('SalonPageComponent', () => {
  let component: SalonPageComponent;
  let fixture: ComponentFixture<SalonPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
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
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'test-salon' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SalonPageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
