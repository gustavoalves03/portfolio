import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../../core/auth/auth.service';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

const mockTranslations = {
  'auth.resetPassword.title': 'New password',
  'auth.resetPassword.subtitle': 'Enter new password',
  'auth.resetPassword.newPassword': 'New password',
  'auth.resetPassword.confirmPassword': 'Confirm',
  'auth.resetPassword.passwordHint': '8 chars',
  'auth.resetPassword.submit': 'Update',
  'auth.resetPassword.success': 'Updated',
  'auth.resetPassword.goToLogin': 'Login',
  'auth.resetPassword.invalidToken': 'Invalid token',
  'auth.resetPassword.requestNewLink': 'Request new',
  'auth.errors.passwordRequired': 'Required',
  'auth.errors.passwordTooShort': 'Too short',
  'auth.errors.passwordMismatch': 'Mismatch',
};

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function setup(token: string | null) {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['resetPassword']);

    TestBed.configureTestingModule({
      imports: [
        ResetPasswordComponent,
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
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should show invalid token when no token in URL', () => {
    setup(null);
    expect(component.invalidToken).toBeTrue();
  });

  it('should not submit when passwords do not match', () => {
    setup('valid-token');
    component.form.setValue({ newPassword: 'password123', confirmPassword: 'different1' });
    component.form.markAllAsTouched();
    component.onSubmit();
    expect(authServiceSpy.resetPassword).not.toHaveBeenCalled();
  });

  it('should call resetPassword and show success on valid submit', () => {
    setup('valid-token');
    authServiceSpy.resetPassword.and.returnValue(of({ message: 'ok' }));

    component.form.setValue({ newPassword: 'newpass123', confirmPassword: 'newpass123' });
    component.onSubmit();

    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith('valid-token', 'newpass123');
    expect(component.resetSuccess).toBeTrue();
  });

  it('should show invalid token error on 400 response', () => {
    setup('expired-token');
    const error = new HttpErrorResponse({ status: 400, statusText: 'Bad Request' });
    authServiceSpy.resetPassword.and.returnValue(throwError(() => error));

    component.form.setValue({ newPassword: 'newpass123', confirmPassword: 'newpass123' });
    component.onSubmit();

    expect(component.invalidToken).toBeTrue();
    expect(component.resetSuccess).toBeFalse();
  });
});
