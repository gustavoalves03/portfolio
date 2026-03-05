import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { OAuth2RedirectComponent } from './oauth2-redirect.component';
import { AuthService } from '../../../core/auth/auth.service';
import { Router, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

describe('OAuth2RedirectComponent', () => {
  let component: OAuth2RedirectComponent;
  let fixture: ComponentFixture<OAuth2RedirectComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  function createWithParams(params: Record<string, string>) {
    const activatedRouteMock = {
      snapshot: {
        queryParamMap: {
          get: (key: string) => params[key] ?? null,
        },
      },
    };

    TestBed.overrideProvider(ActivatedRoute, { useValue: activatedRouteMock });
    fixture = TestBed.createComponent(OAuth2RedirectComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['handleOAuth2Callback']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [OAuth2RedirectComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();
  });

  // T3.1: Token present — stores token and redirects to /pro/dashboard
  it('should call handleOAuth2Callback and redirect to /pro/dashboard on success', () => {
    authServiceSpy.handleOAuth2Callback.and.returnValue(of({} as any));

    createWithParams({ token: 'jwt-token-123' });

    expect(authServiceSpy.handleOAuth2Callback).toHaveBeenCalledWith('jwt-token-123');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
  });

  // T3.2: Error param present — redirects to /login with oauthError state
  it('should redirect to /login with oauthError state when error param is present', () => {
    createWithParams({ error: encodeURIComponent('account_not_found') });

    expect(authServiceSpy.handleOAuth2Callback).not.toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(
      ['/login'],
      { state: { oauthError: 'account_not_found' } }
    );
  });

  // T3.3: handleOAuth2Callback fails — redirects to /login
  it('should redirect to /login when handleOAuth2Callback errors', () => {
    authServiceSpy.handleOAuth2Callback.and.returnValue(throwError(() => new Error('network error')));

    createWithParams({ token: 'bad-token' });

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
