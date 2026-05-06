import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SalonCarouselComponent } from './salon-carousel.component';
import { SalonCard } from '../../../features/discovery/discovery.model';

function makeSalons(count: number): SalonCard[] {
  return Array.from({ length: count }).map((_, i) => ({
    name: `Salon ${i}`,
    slug: `slug-${i}`,
    description: null,
    logoUrl: null,
    categoryNames: null,
    addressCity: 'Paris',
    fullAddress: `${i} rue de Paris`,
  }));
}

describe('SalonCarouselComponent', () => {
  let fixture: ComponentFixture<SalonCarouselComponent>;
  let component: SalonCarouselComponent;

  function setup(salonsCount: number): void {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
      imports: [
        SalonCarouselComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(SalonCarouselComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('salons', makeSalons(salonsCount));
    fixture.detectChanges();
  }

  it('renders one card per salon', () => {
    setup(5);
    const cards = (fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid^="salon-card-"]');
    expect(cards.length).toBe(5);
  });

  it('marks card 0 as the centered one initially', () => {
    setup(5);
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-0"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('next() shifts the center to slug-1', () => {
    setup(5);
    component.next();
    fixture.detectChanges();
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-1"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('prev() wraps from index 0 to last', () => {
    setup(5);
    component.prev();
    fixture.detectChanges();
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-4"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('goTo(2) centers slug-2', () => {
    setup(5);
    component.goTo(2);
    fixture.detectChanges();
    const center = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-2"]');
    expect(center?.classList.contains('center')).toBe(true);
  });

  it('renders nothing visible when given an empty array', () => {
    setup(0);
    const stage = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="carousel-stage"]');
    expect(stage?.children.length ?? 0).toBe(0);
  });
});
