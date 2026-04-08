import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Home } from './home';
import { API_BASE_URL } from '../../core/config/api-base-url.token';

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
          { path: 'pricing', children: [] },
        ]),
        provideNoopAnimations(),
        provideHttpClient(),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
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

  it('should navigate to salon on salon click', () => {
    spyOn(router, 'navigate');
    component.onSalonClick('atelier-lumiere');
    expect(router.navigate).toHaveBeenCalledWith(['/salon', 'atelier-lumiere']);
  });

  it('should navigate to pro CTA', () => {
    spyOn(router, 'navigate');
    component.onProCta();
    expect(router.navigate).toHaveBeenCalled();
  });

  it('should navigate to discover all', () => {
    spyOn(router, 'navigate');
    component.onDiscoverAll();
    expect(router.navigate).toHaveBeenCalledWith(['/discover']);
  });
});
