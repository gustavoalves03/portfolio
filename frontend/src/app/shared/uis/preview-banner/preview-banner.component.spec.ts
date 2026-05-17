import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { PreviewBannerComponent } from './preview-banner.component';
import { DashboardService } from '../../../features/dashboard/services/dashboard.service';

describe('PreviewBannerComponent', () => {
  let fixture: ComponentFixture<PreviewBannerComponent>;
  let component: PreviewBannerComponent;
  let dashboardSpy: jasmine.SpyObj<DashboardService>;

  beforeEach(() => {
    dashboardSpy = jasmine.createSpyObj<DashboardService>('DashboardService', [
      'publish',
      'getReadiness',
    ]);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DashboardService, useValue: dashboardSpy },
      ],
      imports: [
        PreviewBannerComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
    });
    fixture = TestBed.createComponent(PreviewBannerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('slug', 'demo');
    fixture.componentRef.setInput('canPublish', false);
    fixture.detectChanges();
  });

  it('renders the back-to-dashboard link', () => {
    const link = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="banner-back"]'
    );
    expect(link).not.toBeNull();
    expect(link?.getAttribute('href')).toBe('/pro/dashboard');
  });

  it('hides the publish button when canPublish is false', () => {
    fixture.componentRef.setInput('canPublish', false);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="banner-publish"]'
    );
    expect(button).toBeNull();
  });

  it('shows the publish button when canPublish is true', () => {
    fixture.componentRef.setInput('canPublish', true);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="banner-publish"]'
    );
    expect(button).not.toBeNull();
  });

  it('emits published after successful publish', () => {
    dashboardSpy.publish.and.returnValue(of(void 0));
    let emitted = false;
    fixture.componentRef.setInput('canPublish', true);
    component.published.subscribe(() => (emitted = true));
    fixture.detectChanges();
    component.onPublish();
    expect(dashboardSpy.publish).toHaveBeenCalled();
    expect(emitted).toBe(true);
  });

  it('keeps the publish button enabled after a failed publish', () => {
    dashboardSpy.publish.and.returnValue(throwError(() => new Error('boom')));
    fixture.componentRef.setInput('canPublish', true);
    fixture.detectChanges();
    component.onPublish();
    expect(component.publishing()).toBe(false);
  });

  it('redirects to /pro/onboarding/payment on 402', () => {
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    dashboardSpy.publish.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 402,
            error: { message: 'Active subscription required', tier: 'PREMIUM', billing: 'MONTHLY' },
          })
      )
    );
    fixture.componentRef.setInput('canPublish', true);
    fixture.detectChanges();
    component.onPublish();
    expect(navigateSpy).toHaveBeenCalledWith(
      ['/pro/onboarding/payment'],
      { queryParams: { tier: 'PREMIUM', billing: 'MONTHLY' } }
    );
  });
});
