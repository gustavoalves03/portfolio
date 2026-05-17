import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { CgvPageComponent } from './cgv-page.component';

describe('CgvPageComponent', () => {
  let fixture: ComponentFixture<CgvPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        CgvPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              legal: {
                common: { lastUpdated: 'Last updated:' },
                cgv: {
                  title: 'Terms of Sale (Pros)',
                  sections: { objet: { title: 'Objet', body: '<p>body</p>' } },
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(CgvPageComponent);
    fixture.detectChanges();
  });

  it('renders the page title', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Terms of Sale (Pros)');
  });

  it('renders at least one section', () => {
    const sections = fixture.nativeElement.querySelectorAll('.legal-section');
    expect(sections.length).toBeGreaterThan(0);
  });
});
