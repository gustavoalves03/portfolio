import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ProShellComponent } from './pro-shell.component';
import { DashboardStore } from '../../features/dashboard/store/dashboard.store';

describe('ProShellComponent', () => {
  let fixture: ComponentFixture<ProShellComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
      imports: [
        ProShellComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(ProShellComponent);
    fixture.detectChanges();
  });

  it('provides DashboardStore at the component level', () => {
    const store = fixture.debugElement.injector.get(DashboardStore);
    expect(store).toBeTruthy();
  });

  it('renders an onboarding indicator and a router outlet', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('app-onboarding-indicator')).not.toBeNull();
    expect(root.querySelector('router-outlet')).not.toBeNull();
  });
});
