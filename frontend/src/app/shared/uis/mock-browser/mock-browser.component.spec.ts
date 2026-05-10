import { Component, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockBrowserComponent } from './mock-browser.component';

@Component({
  standalone: true,
  imports: [MockBrowserComponent],
  template: `
    <app-mock-browser url="luxpretty.app/dashboard">
      <div class="inside">payload</div>
    </app-mock-browser>
  `,
})
class HostComponent {}

describe('MockBrowserComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [HostComponent],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the URL in the address bar', () => {
    const url = (fixture.nativeElement as HTMLElement).querySelector('.mb-url');
    expect(url?.textContent?.trim()).toBe('luxpretty.app/dashboard');
  });

  it('projects ng-content as the body', () => {
    const inside = (fixture.nativeElement as HTMLElement).querySelector('.inside');
    expect(inside?.textContent?.trim()).toBe('payload');
  });

  it('renders three traffic-light dots', () => {
    const dots = (fixture.nativeElement as HTMLElement).querySelectorAll('.mb-dot');
    expect(dots.length).toBe(3);
  });
});
