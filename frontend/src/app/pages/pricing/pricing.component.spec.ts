import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PricingComponent } from './pricing.component';

describe('PricingComponent', () => {
  let fixture: ComponentFixture<PricingComponent>;

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
        PricingComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(PricingComponent);
    fixture.detectChanges();
  });

  it('renders the parallax hero', () => {
    const hero = (fixture.nativeElement as HTMLElement).querySelector('app-parallax-hero');
    expect(hero).not.toBeNull();
  });

  it('renders the mock browser frame containing the three widgets', () => {
    const frame = (fixture.nativeElement as HTMLElement).querySelector('app-mock-browser');
    expect(frame).not.toBeNull();
    expect(frame?.querySelector('app-revenue-widget')).not.toBeNull();
    expect(frame?.querySelector('app-calendar-widget')).not.toBeNull();
    expect(frame?.querySelector('app-reviews-widget')).not.toBeNull();
  });

  it('renders the four feature cards', () => {
    const cards = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="feature-card"]');
    expect(cards.length).toBe(4);
  });

  it('renders the plan card', () => {
    const plan = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="plan-card"]');
    expect(plan).not.toBeNull();
  });

  it('renders the final CTA with the right router link', () => {
    const cta = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="final-cta"]');
    expect(cta).not.toBeNull();
    expect(cta?.getAttribute('href')).toContain('/register/pro');
  });
});
