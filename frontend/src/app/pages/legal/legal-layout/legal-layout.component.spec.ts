import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { LegalLayoutComponent } from './legal-layout.component';

@Component({
  standalone: true,
  imports: [LegalLayoutComponent],
  template: `
    <app-legal-layout titleKey="legal.cgu.title" [updatedAt]="'2026-05-17'">
      <section data-testid="slot">slot content</section>
    </app-legal-layout>
  `,
})
class HostComponent {}

describe('LegalLayoutComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: {
            en: {
              legal: {
                common: { lastUpdated: 'Last updated:' },
                cgu: { title: 'Terms' },
              },
            },
          },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the translated title', () => {
    const h1 = fixture.nativeElement.querySelector('h1');
    expect(h1.textContent.trim()).toBe('Terms');
  });

  it('renders the last-updated date', () => {
    const updated = fixture.nativeElement.querySelector('.legal-page__updated');
    expect(updated.textContent).toContain('Last updated:');
    expect(updated.textContent).toContain('2026');
  });

  it('projects ng-content into the article', () => {
    const slot = fixture.nativeElement.querySelector('[data-testid="slot"]');
    expect(slot).toBeTruthy();
    expect(slot.textContent).toContain('slot content');
  });
});
