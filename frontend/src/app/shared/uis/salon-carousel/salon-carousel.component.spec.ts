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
    const card = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-0"]');
    // The center class is on the parent container in the new structure.
    expect(card?.parentElement?.classList.contains('center')).toBe(true);
  });

  it('next() shifts the center to slug-1', () => {
    setup(5);
    component.next();
    fixture.detectChanges();
    const card = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-1"]');
    expect(card?.parentElement?.classList.contains('center')).toBe(true);
  });

  it('prev() wraps from index 0 to last', () => {
    setup(5);
    component.prev();
    fixture.detectChanges();
    const card = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-4"]');
    expect(card?.parentElement?.classList.contains('center')).toBe(true);
  });

  it('goTo(2) centers slug-2', () => {
    setup(5);
    component.goTo(2);
    fixture.detectChanges();
    const card = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="salon-card-slug-2"]');
    expect(card?.parentElement?.classList.contains('center')).toBe(true);
  });

  it('renders nothing visible when given an empty array', () => {
    setup(0);
    const stage = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="carousel-stage"]');
    expect(stage?.children.length ?? 0).toBe(0);
  });

  it('toggles flipped state when toggleFlip is called', () => {
    setup(5);
    expect(component.isFlipped('slug-0')).toBe(false);
    component.toggleFlip('slug-0');
    fixture.detectChanges();
    expect(component.isFlipped('slug-0')).toBe(true);
    component.toggleFlip('slug-0');
    fixture.detectChanges();
    expect(component.isFlipped('slug-0')).toBe(false);
  });

  it('un-flips when the center changes', () => {
    setup(5);
    component.toggleFlip('slug-0');
    expect(component.isFlipped('slug-0')).toBe(true);
    component.next();
    fixture.detectChanges();
    expect(component.isFlipped('slug-0')).toBe(false);
  });

  it('renders a flip toggle button on the center card', () => {
    setup(5);
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="flip-toggle-slug-0"]',
    );
    expect(button).not.toBeNull();
  });

  it('does not render flip toggle on side cards', () => {
    setup(5);
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="flip-toggle-slug-1"]',
    );
    expect(button).toBeNull();
  });
});
