import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { LoginComponent } from './login.component';
import { AuthService } from '../../../core/auth/auth.service';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

const mockTranslations = {
  'auth.login.title': 'Sign In',
  'auth.login.subtitle': 'Access your professional space',
  'auth.login.email': 'Email address',
  'auth.login.password': 'Password',
  'auth.login.submit': 'Sign in',
  'auth.login.googleButton': 'Continue with Google',
  'auth.login.noAccount': "Don't have an account yet?",
  'auth.login.signUp': 'Sign up',
  'auth.login.orDivider': 'or',
  'auth.errors.emailRequired': 'Email is required',
  'auth.errors.emailInvalid': 'Email is not valid',
  'auth.errors.passwordRequired': 'Password is required',
  'auth.errors.invalidCredentials': 'Invalid email or password',
  'auth.errors.oauthFailed': 'Google sign-in failed. Please try again.',
  'auth.errors.networkError': 'Network error. Please check your connection.',
};

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['loginWithCredentials', 'loginWithGoogle']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate', 'getCurrentNavigation']);
    routerSpy.getCurrentNavigation.and.returnValue(null);

    await TestBed.configureTestingModule({
      imports: [
        LoginComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // T2.1: Form validation — does not call service with invalid fields
  it('should not call loginWithCredentials when form is invalid', () => {
    component.onSubmit();
    expect(authServiceSpy.loginWithCredentials).not.toHaveBeenCalled();
  });

  it('should mark all controls as touched when submitting invalid form', () => {
    component.onSubmit();
    expect(component.emailControl?.touched).toBeTrue();
    expect(component.passwordControl?.touched).toBeTrue();
  });

  it('should show email format error when email is invalid', () => {
    component.emailControl?.setValue('not-an-email');
    component.emailControl?.markAsTouched();
    expect(component.emailControl?.hasError('email')).toBeTrue();
  });

  // T2.2: Success flow — redirects to /pro/dashboard
  it('should redirect to /pro/dashboard on successful login', () => {
    authServiceSpy.loginWithCredentials.and.returnValue(of({} as any));

    component.form.setValue({ email: 'pro@salon.fr', password: 'password123' });
    component.onSubmit();

    expect(authServiceSpy.loginWithCredentials).toHaveBeenCalledWith('pro@salon.fr', 'password123');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
  });

  // T2.3: 401 error — shows invalidCredentialsError banner
  it('should set invalidCredentialsError on 401 response', () => {
    const error = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });
    authServiceSpy.loginWithCredentials.and.returnValue(throwError(() => error));

    component.form.setValue({ email: 'pro@salon.fr', password: 'wrongpass1' });
    component.onSubmit();

    expect(component.invalidCredentialsError).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });

  // T2.4: Network error — shows networkError banner for non-401/403 errors
  it('should set networkError on 500 response', () => {
    const error = new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' });
    authServiceSpy.loginWithCredentials.and.returnValue(throwError(() => error));

    component.form.setValue({ email: 'pro@salon.fr', password: 'password123' });
    component.onSubmit();

    expect(component.networkError).toBeTrue();
    expect(component.invalidCredentialsError).toBeFalse();
  });

  // T2.5: Google login — calls authService.loginWithGoogle
  it('should call loginWithGoogle when Google button is clicked', () => {
    component.loginWithGoogle();
    expect(authServiceSpy.loginWithGoogle).toHaveBeenCalled();
  });
});
