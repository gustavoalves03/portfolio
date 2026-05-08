import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Home } from './home';

describe('Home', () => {
  let fixture: ComponentFixture<Home>;

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
        Home,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
  });

  it('renders hero, search bar, category grid and pro CTA', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.v1-hero')).not.toBeNull();
    expect(root.querySelector('.v1-search')).not.toBeNull();
    expect(root.querySelectorAll('.v1-cat').length).toBeGreaterThan(0);
    expect(root.querySelector('.v1-pro-card')).not.toBeNull();
  });
});
