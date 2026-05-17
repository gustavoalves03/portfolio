import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { CookiesPageComponent } from './cookies-page.component';

describe('CookiesPageComponent', () => {
  let fixture: ComponentFixture<CookiesPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        CookiesPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              legal: {
                common: { lastUpdated: 'Last updated:' },
                cookies: {
                  title: 'Cookies Policy',
                  sections: { placeholder: { title: 'Section', body: '<p>body</p>' } },
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(CookiesPageComponent);
    fixture.detectChanges();
  });

  it('renders the page title', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Cookies Policy');
  });

  it('renders at least one section', () => {
    const sections = fixture.nativeElement.querySelectorAll('.legal-section');
    expect(sections.length).toBeGreaterThan(0);
  });
});
