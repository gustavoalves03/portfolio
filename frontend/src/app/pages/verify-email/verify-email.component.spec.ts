import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { VerifyEmailComponent } from './verify-email.component';
import { AuthService } from '../../core/auth/auth.service';

describe('VerifyEmailComponent', () => {
  let fixture: ComponentFixture<VerifyEmailComponent>;
  let auth: jasmine.SpyObj<AuthService>;

  function configure(token: string | null) {
    TestBed.configureTestingModule({
      imports: [
        VerifyEmailComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: AuthService, useValue: auth },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: { get: () => token } },
          },
        },
      ],
    });
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['verifyEmail', 'sendVerification']);
  });

  it('shows success when token is valid', () => {
    auth.verifyEmail.and.returnValue(of({ message: 'Email verified' }));
    configure('valid-token');
    fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('success');
    expect(auth.verifyEmail).toHaveBeenCalledWith('valid-token');
  });

  it('shows expired state on TOKEN_EXPIRED error', () => {
    auth.verifyEmail.and.returnValue(
      throwError(() => ({ error: { error: 'TOKEN_EXPIRED' } })),
    );
    configure('expired-token');
    fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('expired');
  });

  it('shows invalid state when token absent', () => {
    configure(null);
    fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.state()).toBe('invalid');
    expect(auth.verifyEmail).not.toHaveBeenCalled();
  });
});
