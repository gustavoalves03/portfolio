import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { Router } from '@angular/router';
import { proEmailVerifiedGuard } from './pro-email-verified.guard';
import { AuthService } from './auth.service';
import { Role } from './auth.model';

describe('proEmailVerifiedGuard', () => {
  let router: jasmine.SpyObj<Router>;
  let auth: { user: ReturnType<typeof signal> };

  function run() {
    return TestBed.runInInjectionContext(() =>
      proEmailVerifiedGuard({} as any, {} as any),
    );
  }

  beforeEach(() => {
    router = jasmine.createSpyObj('Router', ['navigate']);
    auth = { user: signal(null) };
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Router, useValue: router },
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  it('returns true for verified PRO', () => {
    auth.user.set({ roles: [Role.PRO], emailVerified: true });
    expect(run()).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects unverified PRO', () => {
    auth.user.set({ roles: [Role.PRO], emailVerified: false });
    expect(run()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/verify-email-required']);
  });

  it('returns true for non-PRO user even if unverified', () => {
    auth.user.set({ roles: [Role.EMPLOYEE], emailVerified: false });
    expect(run()).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
