import { Component, ElementRef, PLATFORM_ID, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { useIntersection } from './use-intersection';

@Component({
  standalone: true,
  template: `<div #target>hello</div>`,
})
class HostComponent {
  readonly target = viewChild.required<ElementRef<HTMLElement>>('target');
  readonly visible = useIntersection(this.target, 0.3);
}

describe('useIntersection', () => {
  let originalIO: typeof window.IntersectionObserver;
  let observers: Array<{
    cb: IntersectionObserverCallback;
    instance: IntersectionObserver;
  }>;

  beforeEach(() => {
    observers = [];
    originalIO = window.IntersectionObserver;
    (window as any).IntersectionObserver = class {
      constructor(public cb: IntersectionObserverCallback) {
        observers.push({ cb, instance: this as any });
      }
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
      takeRecords(): IntersectionObserverEntry[] { return []; }
      readonly root = null;
      readonly rootMargin = '';
      readonly thresholds: number[] = [0.3];
    };
  });

  afterEach(() => {
    window.IntersectionObserver = originalIO;
  });

  function setup(platform: 'browser' | 'server'): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
      ],
      imports: [HostComponent],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('returns false on the server (SSR)', () => {
    const fixture = setup('server');
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('returns false on the browser before intersection', () => {
    const fixture = setup('browser');
    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('flips to true when the element intersects', () => {
    const fixture = setup('browser');
    const obs = observers[0];
    obs.cb(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      obs.instance,
    );
    expect(fixture.componentInstance.visible()).toBe(true);
  });

  it('stays true even if the element later leaves the viewport', () => {
    const fixture = setup('browser');
    const obs = observers[0];
    obs.cb(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      obs.instance,
    );
    expect(fixture.componentInstance.visible()).toBe(true);
    obs.cb(
      [{ isIntersecting: false } as IntersectionObserverEntry],
      obs.instance,
    );
    expect(fixture.componentInstance.visible()).toBe(true);
  });
});
