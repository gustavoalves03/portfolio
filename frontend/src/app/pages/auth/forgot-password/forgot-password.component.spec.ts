import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';

const mockTranslations = {
  'auth.forgotPassword.title': 'Reset your password',
  'auth.forgotPassword.subtitle': 'Enter your email',
  'auth.forgotPassword.email': 'Email',
  'auth.forgotPassword.submit': 'Send',
  'auth.forgotPassword.success': 'Check your inbox',
  'auth.forgotPassword.backToLogin': 'Back',
  'auth.errors.emailRequired': 'Email is required',
  'auth.errors.emailInvalid': 'Email is not valid',
};

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['requestPasswordReset']);

    await TestBed.configureTestingModule({
      imports: [
        ForgotPasswordComponent,
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
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should not call service when form is invalid', () => {
    component.onSubmit();
    expect(authServiceSpy.requestPasswordReset).not.toHaveBeenCalled();
  });

  it('should show success message after submitting valid email', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(of({ message: 'ok' }));

    component.form.setValue({ email: 'sophie@salon.fr' });
    component.onSubmit();

    expect(authServiceSpy.requestPasswordReset).toHaveBeenCalledWith('sophie@salon.fr');
    expect(component.emailSent).toBeTrue();
  });

  it('should still show success on error (no enumeration)', () => {
    authServiceSpy.requestPasswordReset.and.returnValue(throwError(() => new Error('fail')));

    component.form.setValue({ email: 'sophie@salon.fr' });
    component.onSubmit();

    expect(component.emailSent).toBeTrue();
  });
});
