import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { SalonProfileComponent } from './salon-profile.component';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

describe('SalonProfileComponent', () => {
  let component: SalonProfileComponent;
  let fixture: ComponentFixture<SalonProfileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        SalonProfileComponent,
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
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SalonProfileComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
