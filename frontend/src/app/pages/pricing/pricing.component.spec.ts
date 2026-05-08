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

  it('renders the page header', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.pricing-header')).not.toBeNull();
    expect(root.querySelector('.pricing-title')).not.toBeNull();
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
