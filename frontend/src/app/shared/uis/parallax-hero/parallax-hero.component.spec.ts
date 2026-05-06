import { Component, PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParallaxHeroComponent } from './parallax-hero.component';

@Component({
  standalone: true,
  imports: [ParallaxHeroComponent],
  template: `
    <app-parallax-hero imageUrl="/pricing/hero.jpg">
      <span class="overlay">overlay-content</span>
    </app-parallax-hero>
  `,
})
class HostComponent {}

describe('ParallaxHeroComponent', () => {
  function setup(platform: 'browser' | 'server'): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
      ],
      imports: [HostComponent],
    });
    const f = TestBed.createComponent(HostComponent);
    f.detectChanges();
    return f;
  }

  it('renders the background image', () => {
    const fixture = setup('browser');
    const bg = (fixture.nativeElement as HTMLElement).querySelector('.parallax-bg');
    expect(bg).not.toBeNull();
    expect((bg as HTMLElement).style.backgroundImage).toContain('/pricing/hero.jpg');
  });

  it('projects ng-content as the overlay', () => {
    const fixture = setup('browser');
    const overlay = (fixture.nativeElement as HTMLElement).querySelector('.overlay');
    expect(overlay?.textContent?.trim()).toBe('overlay-content');
  });

  it('renders correctly during SSR (no scroll listener errors)', () => {
    const fixture = setup('server');
    expect(() => fixture.detectChanges()).not.toThrow();
    const bg = (fixture.nativeElement as HTMLElement).querySelector('.parallax-bg');
    expect(bg).not.toBeNull();
  });
});
