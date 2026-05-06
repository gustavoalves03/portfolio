import { Component, PLATFORM_ID, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { useCountUp } from './use-count-up';

@Component({
  standalone: true,
  template: ``,
})
class HostComponent {
  readonly start = signal(false);
  readonly value = useCountUp(100, 1000, this.start);
}

describe('useCountUp', () => {
  function setup(platform: 'browser' | 'server'): ComponentFixture<HostComponent> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: PLATFORM_ID, useValue: platform },
      ],
      imports: [HostComponent],
    });
    return TestBed.createComponent(HostComponent);
  }

  it('returns 0 on the server (SSR)', () => {
    const fixture = setup('server');
    expect(fixture.componentInstance.value()).toBe(0);
    fixture.componentInstance.start.set(true);
    fixture.detectChanges();
    expect(fixture.componentInstance.value()).toBe(0);
  });

  it('returns 0 in the browser before start is true', () => {
    const fixture = setup('browser');
    expect(fixture.componentInstance.value()).toBe(0);
  });

  it('ramps from 0 toward target after start flips true', (done) => {
    const fixture = setup('browser');
    fixture.componentInstance.start.set(true);
    fixture.detectChanges();
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        expect(fixture.componentInstance.value()).toBeGreaterThan(0);
        done();
      });
    });
  });

  it('settles at target when duration elapses', (done) => {
    const fixture = setup('browser');
    fixture.componentInstance.start.set(true);
    fixture.detectChanges();
    // Wait beyond the 1000ms duration.
    setTimeout(() => {
      expect(fixture.componentInstance.value()).toBe(100);
      done();
    }, 1200);
  }, 2000);
});
