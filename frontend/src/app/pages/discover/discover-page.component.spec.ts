import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { DiscoverPageComponent } from './discover-page.component';
import { DiscoveryService } from '../../features/discovery/discovery.service';
import { of } from 'rxjs';

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

  it('should render category chips', () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    const chips = el.querySelectorAll('.chip');
    expect(chips.length).toBe(5); // 4 categories + "Tous"
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
});
