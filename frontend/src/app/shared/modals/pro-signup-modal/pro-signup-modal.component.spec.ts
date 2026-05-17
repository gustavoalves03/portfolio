import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection } from '@angular/core';
import { of, throwError, Observable } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoTestingModule } from '@jsverse/transloco';

import {
  ProSignupModalComponent,
  ProSignupModalData,
} from './pro-signup-modal.component';
import { AuthService } from '../../../core/auth/auth.service';

describe('ProSignupModalComponent', () => {
  let fixture: ComponentFixture<ProSignupModalComponent>;
  let component: ProSignupModalComponent;
  let authService: jasmine.SpyObj<AuthService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ProSignupModalComponent>>;
  const data: ProSignupModalData = { tier: 'GESTION', billing: 'YEARLY' };

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['registerPro']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [
        ProSignupModalComponent,
        TranslocoTestingModule.forRoot({ langs: { fr: {} }, translocoConfig: { defaultLang: 'fr' } }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: AuthService, useValue: authService },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProSignupModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('exposes tier and billing from injected data', () => {
    expect(component.tier).toBe('GESTION');
    expect(component.billing).toBe('YEARLY');
  });

  it('isFormValid returns false when fields are empty', () => {
    expect(component.isFormValid()).toBe(false);
  });

  it('isFormValid returns true when all fields are filled and emails match', () => {
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.emailConfirm.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);
    expect(component.isFormValid()).toBe(true);
  });

  it('isFormValid returns false when emails do not match', () => {
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.emailConfirm.set('other@b.com');
    component.password.set('password123');
    component.consent.set(true);
    expect(component.emailMismatch()).toBe(true);
    expect(component.isFormValid()).toBe(false);
  });

  it('applyEmailSuggestion replaces email and clears suggestion', () => {
    component.email.set('user@gmial.com');
    component.emailSuggestion.set('user@gmail.com');
    component.applyEmailSuggestion();
    expect(component.email()).toBe('user@gmail.com');
    expect(component.emailSuggestion()).toBeNull();
  });

  it('submit calls registerPro with tier+billing from data', () => {
    authService.registerPro.and.returnValue(of({ id: 1 } as any));
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.emailConfirm.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(authService.registerPro).toHaveBeenCalledWith(jasmine.objectContaining({
      name: 'Alice',
      email: 'a@b.com',
      password: 'password123',
      tier: 'GESTION',
      billing: 'YEARLY',
    }));
  });

  it('submit closes dialog with authenticated:true on success', () => {
    authService.registerPro.and.returnValue(of({ id: 1 } as any));
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.emailConfirm.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(dialogRef.close).toHaveBeenCalledWith({ authenticated: true });
  });

  it('submit sets emailAlreadyInUse error on 409', () => {
    authService.registerPro.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409 }))
    );
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.emailConfirm.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(component.errorKey()).toBe('proSignup.modal.errors.emailAlreadyInUse');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('submit sets networkError on non-409 error', () => {
    authService.registerPro.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.emailConfirm.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();

    expect(component.errorKey()).toBe('proSignup.modal.errors.networkError');
  });

  it('submit does nothing when form invalid', () => {
    component.submit();
    expect(authService.registerPro).not.toHaveBeenCalled();
  });

  it('re-entrancy guard: second submit while loading does nothing', () => {
    authService.registerPro.and.returnValue(new Observable(() => {}));
    component.name.set('Alice');
    component.email.set('a@b.com');
    component.emailConfirm.set('a@b.com');
    component.password.set('password123');
    component.consent.set(true);

    component.submit();
    component.submit();

    expect(authService.registerPro).toHaveBeenCalledTimes(1);
  });

  it('openLogin closes dialog with authenticated:false', () => {
    component.openLogin();
    expect(dialogRef.close).toHaveBeenCalledWith({ authenticated: false, switchToLogin: true });
  });
});
