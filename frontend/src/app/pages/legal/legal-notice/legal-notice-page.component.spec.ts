import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { LegalNoticePageComponent } from './legal-notice-page.component';

describe('LegalNoticePageComponent', () => {
  let fixture: ComponentFixture<LegalNoticePageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        LegalNoticePageComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              legal: {
                common: { lastUpdated: 'Last updated:' },
                notice: {
                  title: 'Legal Notice',
                  preLaunchBanner: 'pre-launch test text',
                  sections: { editeur: { title: 'Editor', body: '<p>body</p>' } },
                },
              },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(LegalNoticePageComponent);
    fixture.detectChanges();
  });

  it('renders the page title', () => {
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Legal Notice');
  });

  it('renders at least one section', () => {
    const sections = fixture.nativeElement.querySelectorAll('.legal-section');
    expect(sections.length).toBeGreaterThan(0);
  });

  it('renders the pre-launch banner', () => {
    const banner = fixture.nativeElement.querySelector('.legal-prelaunch-banner');
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('pre-launch');
  });
});
