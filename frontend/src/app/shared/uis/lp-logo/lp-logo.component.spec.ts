import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { By } from '@angular/platform-browser';
import { LpLogoComponent } from './lp-logo.component';

describe('LpLogoComponent', () => {
  let fixture: ComponentFixture<LpLogoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LpLogoComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(LpLogoComponent);
  });

  it('renders the full SVG by default with LuxPretty alt text', () => {
    fixture.detectChanges();
    const img = fixture.debugElement.query(By.css('img.lp-logo__img--full'));
    expect(img).withContext('expected full SVG image').not.toBeNull();
    expect(img.nativeElement.getAttribute('src')).toBe('/logos/luxpretty-full.svg');
    expect(img.nativeElement.getAttribute('alt')).toBe('LuxPretty');
  });

  it('renders the mono SVG when variant="small"', () => {
    fixture.componentRef.setInput('variant', 'small');
    fixture.detectChanges();
    const img = fixture.debugElement.query(By.css('img.lp-logo__img--mono'));
    expect(img).withContext('expected mono SVG image').not.toBeNull();
    expect(img.nativeElement.getAttribute('src')).toBe('/logos/luxpretty-mono.svg');
    expect(fixture.debugElement.query(By.css('img.lp-logo__img--full'))).toBeNull();
  });

  it('applies the small variant class when variant="small"', () => {
    fixture.componentRef.setInput('variant', 'small');
    fixture.detectChanges();
    const host = fixture.debugElement.query(By.css('.lp-logo'));
    expect(host.nativeElement.classList).toContain('lp-logo--small');
  });

  it('shows the tagline when variant="with-tagline"', () => {
    fixture.componentRef.setInput('variant', 'with-tagline');
    fixture.detectChanges();
    const tagline = fixture.debugElement.query(By.css('.lp-logo__tagline'));
    expect(tagline).withContext('expected tagline element').not.toBeNull();
    expect(tagline.nativeElement.textContent).toContain('Beauté');
    // Full variant SVG is still shown alongside the tagline
    expect(fixture.debugElement.query(By.css('img.lp-logo__img--full'))).not.toBeNull();
  });

  it('does not show the tagline by default', () => {
    fixture.detectChanges();
    const tagline = fixture.debugElement.query(By.css('.lp-logo__tagline'));
    expect(tagline).toBeNull();
  });
});
