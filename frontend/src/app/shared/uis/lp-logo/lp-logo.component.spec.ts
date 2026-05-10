import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { By } from '@angular/platform-browser';
import { LpLogoComponent } from './lp-logo.component';

describe('LpLogoComponent', () => {
  let fixture: ComponentFixture<LpLogoComponent>;
  let component: LpLogoComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LpLogoComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(LpLogoComponent);
    component = fixture.componentInstance;
  });

  it('renders both LUX and Pretty words by default', () => {
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    expect(root.textContent).toContain('LUX');
    expect(root.textContent).toContain('Pretty');
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
  });

  it('does not show the tagline by default', () => {
    fixture.detectChanges();
    const tagline = fixture.debugElement.query(By.css('.lp-logo__tagline'));
    expect(tagline).toBeNull();
  });
});
