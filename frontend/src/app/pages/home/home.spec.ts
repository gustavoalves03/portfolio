import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Home } from './home';

describe('Home (Landing Page)', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        Home,
        TranslocoTestingModule.forRoot({
          langs: {
            fr: {
              home: {
                hero: {
                  title: 'Pretty Face',
                  subtitle: 'Trouve ton prochain soin beauté',
                  search: 'Rechercher...',
                },
                categories: { title: 'Catégories' },
                salons: { title: 'Découvre les artistes près de toi' },
                cta: { question: 'Tu es pro ?', action: 'Crée ta vitrine' },
              },
            },
          },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([
          { path: 'discover', children: [] },
          { path: 'salon/:slug', children: [] },
          { path: 'register', children: [] },
        ]),
        provideNoopAnimations(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render hero section with title', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.hero-title')?.textContent).toContain('Pretty Face');
  });

  it('should render 4 category cards', () => {
    const el = fixture.nativeElement as HTMLElement;
    const cards = el.querySelectorAll('.category-card');
    expect(cards.length).toBe(4);
  });

  it('should navigate to discover on category click', () => {
    spyOn(router, 'navigate');
    component.onCategoryClick('soins-visage');
    expect(router.navigate).toHaveBeenCalledWith(['/discover'], {
      queryParams: { category: 'soins-visage' },
    });
  });

  it('should render 5 salon cards', () => {
    const el = fixture.nativeElement as HTMLElement;
    const cards = el.querySelectorAll('.salon-card');
    expect(cards.length).toBe(5);
  });

  it('should navigate to salon on salon click', () => {
    spyOn(router, 'navigate');
    component.onSalonClick('atelier-lumiere');
    expect(router.navigate).toHaveBeenCalledWith(['/salon', 'atelier-lumiere']);
  });

  it('should render pro CTA', () => {
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.pro-cta')).toBeTruthy();
  });

  it('should navigate to register on pro CTA', () => {
    spyOn(router, 'navigate');
    component.onProCta();
    expect(router.navigate).toHaveBeenCalledWith(['/register']);
  });

  it('should navigate to discover on search', () => {
    spyOn(router, 'navigate');
    component.searchQuery.set('visage');
    component.onSearch();
    expect(router.navigate).toHaveBeenCalledWith(['/discover'], {
      queryParams: { q: 'visage' },
    });
  });

  it('should not navigate on empty search', () => {
    spyOn(router, 'navigate');
    component.searchQuery.set('  ');
    component.onSearch();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
