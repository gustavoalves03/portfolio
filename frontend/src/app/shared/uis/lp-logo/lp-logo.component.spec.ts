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

  it('renders the full inline SVG by default', () => {
    fixture.detectChanges();
    const svg = fixture.debugElement.query(By.css('svg.lp-logo__svg--full'));
    expect(svg).withContext('expected full inline SVG').not.toBeNull();
    expect(svg.nativeElement.getAttribute('aria-label')).toBe('LuxPretty');
    expect(svg.nativeElement.textContent).toContain('LuxPretty');
    expect(svg.nativeElement.textContent).toContain('LXP');
  });

  it('renders only the mono inline SVG when variant="small"', () => {
    fixture.componentRef.setInput('variant', 'small');
    fixture.detectChanges();
    const mono = fixture.debugElement.query(By.css('svg.lp-logo__svg--mono'));
    expect(mono).withContext('expected mono inline SVG').not.toBeNull();
    expect(mono.nativeElement.textContent).toContain('LXP');
    expect(fixture.debugElement.query(By.css('svg.lp-logo__svg--full'))).toBeNull();
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
    expect(fixture.debugElement.query(By.css('svg.lp-logo__svg--full'))).not.toBeNull();
  });

  it('does not show the tagline by default', () => {
    fixture.detectChanges();
    const tagline = fixture.debugElement.query(By.css('.lp-logo__tagline'));
    expect(tagline).toBeNull();
  });
});
