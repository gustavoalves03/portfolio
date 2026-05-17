import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { PrivacyPageComponent } from './privacy-page.component';

describe('PrivacyPageComponent', () => {
  let fixture: ComponentFixture<PrivacyPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        PrivacyPageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              legal: {
                common: { lastUpdated: 'Last updated:' },
                privacy: {
                  title: 'Privacy Policy',
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

    fixture = TestBed.createComponent(PrivacyPageComponent);
    fixture.detectChanges();
  });

  it('renders the page title', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Privacy Policy');
  });

  it('renders at least one section', () => {
    const sections = fixture.nativeElement.querySelectorAll('.legal-section');
    expect(sections.length).toBeGreaterThan(0);
  });
});
