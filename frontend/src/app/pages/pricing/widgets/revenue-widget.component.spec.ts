import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { RevenueWidgetComponent } from './revenue-widget.component';

@Component({
  standalone: true,
  imports: [RevenueWidgetComponent],
  template: `<app-revenue-widget [started]="started()" />`,
})
class HostComponent {
  readonly started = signal(false);
}

describe('RevenueWidgetComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
      imports: [
        HostComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders 0 € when not started', () => {
    const value = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="revenue-value"]');
    expect(value?.textContent).toContain('0');
  });

  it('renders the SVG sparkline path', () => {
    const path = (fixture.nativeElement as HTMLElement).querySelector('svg path[data-testid="sparkline"]');
    expect(path).not.toBeNull();
  });

  it('renders the label and trend chip', () => {
    const label = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="revenue-label"]');
    expect(label).not.toBeNull();
    const trend = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="revenue-trend"]');
    expect(trend).not.toBeNull();
  });
});
