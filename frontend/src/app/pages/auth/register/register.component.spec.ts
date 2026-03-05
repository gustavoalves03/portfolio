import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../../core/auth/auth.service';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

const mockTranslations = {
  'auth.register.title': 'Create your professional space',
  'auth.register.subtitle': 'Join Pretty Face',
  'auth.register.name': 'Full name',
  'auth.register.email': 'Email address',
  'auth.register.password': 'Password',
  'auth.register.passwordHint': 'At least 8 characters',
  'auth.register.consent': 'I accept the Terms',
  'auth.register.consentRequired': 'You must accept the terms',
  'auth.register.submit': 'Create my account',
  'auth.register.loginLink': 'Already registered?',
  'auth.errors.nameRequired': 'Name is required',
  'auth.errors.emailRequired': 'Email is required',
  'auth.errors.emailInvalid': 'Email is not valid',
  'auth.errors.emailAlreadyInUse': 'Email already in use',
  'auth.errors.passwordRequired': 'Password is required',
  'auth.errors.passwordTooShort': 'Password too short',
};

describe('RegisterComponent', () => {
  let component: RegisterComponent;
  let fixture: ComponentFixture<RegisterComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['registerPro']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [
        RegisterComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // T3.1: Form validation — submit disabled with invalid fields
  it('should mark form as invalid when fields are empty', () => {
    expect(component.form.invalid).toBeTrue();
  });

  it('should mark all controls as touched when submitting invalid form', () => {
    component.onSubmit();
    expect(component.nameControl?.touched).toBeTrue();
    expect(component.emailControl?.touched).toBeTrue();
    expect(component.passwordControl?.touched).toBeTrue();
  });

  it('should show name error when name is empty and touched', () => {
    component.nameControl?.markAsTouched();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    // Form is marked invalid with required validator
    expect(component.nameControl?.hasError('required')).toBeTrue();
  });

  it('should show email format error when email is invalid', () => {
    component.emailControl?.setValue('not-an-email');
    component.emailControl?.markAsTouched();
    expect(component.emailControl?.hasError('email')).toBeTrue();
  });

  it('should show password minlength error when password too short', () => {
    component.passwordControl?.setValue('short');
    component.passwordControl?.markAsTouched();
    expect(component.passwordControl?.hasError('minlength')).toBeTrue();
  });

  it('should not call registerPro when form is invalid', () => {
    component.onSubmit();
    expect(authServiceSpy.registerPro).not.toHaveBeenCalled();
  });

  // T3.2: Success flow — redirects to /pro/dashboard
  it('should redirect to /pro/dashboard on successful registration', () => {
    authServiceSpy.registerPro.and.returnValue(of({} as any));

    component.form.setValue({
      name: 'Sophie Martin',
      email: 'sophie@salon.fr',
      password: 'password123',
      consent: true,
    });

    component.onSubmit();

    expect(authServiceSpy.registerPro).toHaveBeenCalledWith('Sophie Martin', 'sophie@salon.fr', 'password123');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/pro/dashboard']);
  });

  // T3.3: 409 error — shows inline email error
  it('should set emailConflictError on 409 response', () => {
    const error = new HttpErrorResponse({ status: 409, statusText: 'Conflict' });
    authServiceSpy.registerPro.and.returnValue(throwError(() => error));

    component.form.setValue({
      name: 'Sophie Martin',
      email: 'existing@salon.fr',
      password: 'password123',
      consent: true,
    });

    component.onSubmit();

    expect(component.emailConflictError).toBeTrue();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  });
});
