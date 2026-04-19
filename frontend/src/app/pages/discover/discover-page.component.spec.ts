import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { DiscoverPageComponent } from './discover-page.component';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { BehaviorSubject, of } from 'rxjs';

const mockSalons = [
  { name: 'Atelier Lumière', slug: 'atelier-lumiere', description: 'Soins visage', logoUrl: null, categoryNames: 'Soins visage', addressCity: 'Paris', fullAddress: '1 rue de la Paix, 75001 Paris' },
  { name: 'Rose & Thé', slug: 'rose-et-the', description: 'Ongles', logoUrl: null, categoryNames: 'Ongles', addressCity: 'Lyon', fullAddress: '5 rue Victor Hugo, 69002 Lyon' },
];

function createRoute(params: Record<string, string> = {}) {
  return {
    queryParamMap: of(convertToParamMap(params)),
  };
}

describe('DiscoverPageComponent', () => {
  let discoveryService: jasmine.SpyObj<DiscoveryService>;

  function setup(params: Record<string, string> = {}) {
    discoveryService = jasmine.createSpyObj('DiscoveryService', ['searchSalons']);
    discoveryService.searchSalons.and.returnValue(of(mockSalons));

    TestBed.configureTestingModule({
      imports: [
        DiscoverPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              discover: {
                title: 'Découvrir les salons',
                search: 'Rechercher...',
                noResults: 'Aucun salon trouvé',
                noResultsHint: 'Essayez une autre catégorie',
                allCategories: 'Tous',
              },
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        { provide: ActivatedRoute, useValue: createRoute(params) },
        { provide: DiscoveryService, useValue: discoveryService },
      ],
    });

    const fixture = TestBed.createComponent(DiscoverPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    const fixture = setup();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render salon cards', () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    const cards = el.querySelectorAll('.salon-card');
    expect(cards.length).toBe(2);
  });

  it('should call searchSalons with category param', () => {
    setup({ category: 'ongles' });
    expect(discoveryService.searchSalons).toHaveBeenCalled();
  });

  it('should navigate to salon on card click', () => {
    const fixture = setup();
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture.componentInstance.onSalonClick('atelier-lumiere');
    expect(router.navigate).toHaveBeenCalledWith(['/salon', 'atelier-lumiere']);
  });

  it('should show empty state when no results', () => {
    discoveryService = jasmine.createSpyObj('DiscoveryService', ['searchSalons']);
    discoveryService.searchSalons.and.returnValue(of([]));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [
        DiscoverPageComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: { discover: { title: 'Découvrir', noResults: 'Aucun salon', noResultsHint: 'Essayez', allCategories: 'Tous', search: 'Rechercher...' } } },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        { provide: ActivatedRoute, useValue: createRoute() },
        { provide: DiscoveryService, useValue: discoveryService },
      ],
    });

    const fixture = TestBed.createComponent(DiscoverPageComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.empty-state')).toBeTruthy();
  });

  it('click on a salon card triggers navigation', () => {
    const fixture = setup();
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    const firstCard = fixture.nativeElement.querySelector('.salon-card') as HTMLButtonElement;
    firstCard.click();

    expect(router.navigate).toHaveBeenCalledWith(['/salon', 'atelier-lumiere']);
  });

  it('transitions from empty to results when salons arrive', () => {
    const source$ = new BehaviorSubject<typeof mockSalons>([]);
    discoveryService = jasmine.createSpyObj('DiscoveryService', ['searchSalons']);
    discoveryService.searchSalons.and.returnValue(source$.asObservable());

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [
        DiscoverPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              discover: {
                title: 'T', search: 'S', noResults: 'Aucun', noResultsHint: 'Hint', allCategories: 'Tous',
              },
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        { provide: ActivatedRoute, useValue: createRoute() },
        { provide: DiscoveryService, useValue: discoveryService },
      ],
    });

    const fixture = TestBed.createComponent(DiscoverPageComponent);
    fixture.detectChanges();

    // Initial: empty results → empty state visible
    let host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.empty-state')).toBeTruthy();
    expect(host.querySelectorAll('.salon-card').length).toBe(0);

    // Emit results
    source$.next(mockSalons);
    fixture.detectChanges();

    expect(host.querySelector('.empty-state')).toBeFalsy();
    expect(host.querySelectorAll('.salon-card').length).toBe(2);
  });

  it('typing in the search input triggers a new search after debounce', (done) => {
    const fixture = setup();
    discoveryService.searchSalons.calls.reset();

    fixture.componentInstance.searchQuery.set('lumiere');

    // debounceTime(300) uses the async scheduler (setTimeout)
    setTimeout(() => {
      expect(discoveryService.searchSalons).toHaveBeenCalled();
      const args = discoveryService.searchSalons.calls.mostRecent().args;
      expect(args[0]).toBeNull();
      expect(args[1]).toBe('lumiere');
      done();
    }, 400);
  });

  it('route query param "q" pre-fills the search input', () => {
    const fixture = setup({ q: 'rose' });
    expect(fixture.componentInstance.searchQuery()).toBe('rose');
    expect(discoveryService.searchSalons).toHaveBeenCalledWith(null, 'rose');
  });

  it('onCardEnter and onCardLeave toggle hoveredSlug', () => {
    const fixture = setup();
    const c = fixture.componentInstance;

    c.onCardEnter('atelier-lumiere');
    expect(c.hoveredSlug()).toBe('atelier-lumiere');

    c.onCardLeave('atelier-lumiere');
    expect(c.hoveredSlug()).toBeNull();
  });

  it('truncate strips HTML and caps length', () => {
    const fixture = setup();
    const c = fixture.componentInstance;

    expect(c.truncate(null, 10)).toBe('');
    expect(c.truncate('hello', 10)).toBe('hello');
    expect(c.truncate('hello world this is long', 10)).toBe('hello worl...');
    expect(c.truncate('<p>bold <strong>text</strong></p>', 100)).toBe('bold text');
  });

  it('getGradient wraps around via modulo', () => {
    const fixture = setup();
    const first = fixture.componentInstance.getGradient(0);
    expect(fixture.componentInstance.getGradient(5)).toBe(first);
    expect(typeof fixture.componentInstance.getGradient(2)).toBe('string');
  });
});
