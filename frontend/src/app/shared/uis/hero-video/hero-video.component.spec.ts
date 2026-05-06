import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, PLATFORM_ID, provideZonelessChangeDetection } from '@angular/core';
import { HeroVideoComponent } from './hero-video.component';

@Component({
  standalone: true,
  imports: [HeroVideoComponent],
  template: `
    <app-hero-video posterUrl="/hero/p.jpg" videoUrl="/hero/v.mp4">
      <span class="overlay">hello</span>
    </app-hero-video>
  `,
})
class HostComponent {}

describe('HeroVideoComponent', () => {
  function setup(platform: 'browser' | 'server', desktopHover = true): ComponentFixture<HostComponent> {
    if (platform === 'browser') {
      // Stub matchMedia. Returns true for the desktop+hover query when desktopHover is true.
      window.matchMedia = ((query: string): MediaQueryList => ({
        matches: desktopHover && query.includes('(min-width: 768px)'),
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      } as unknown as MediaQueryList)) as typeof window.matchMedia;
    }
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

  it('renders <img> poster on the server (SSR)', () => {
    const fixture = setup('server');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('img.hero-poster')).not.toBeNull();
    expect(root.querySelector('video.hero-video')).toBeNull();
  });

  it('renders <video> on desktop+hover-capable browser', () => {
    const fixture = setup('browser', true);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('video.hero-video')).not.toBeNull();
  });

  it('renders <img> on browser without hover (mobile)', () => {
    const fixture = setup('browser', false);
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('img.hero-poster')).not.toBeNull();
    expect(root.querySelector('video.hero-video')).toBeNull();
  });

  it('projects ng-content as the overlay', () => {
    const fixture = setup('browser', true);
    const overlay = (fixture.nativeElement as HTMLElement).querySelector('.overlay');
    expect(overlay?.textContent?.trim()).toBe('hello');
  });
});
