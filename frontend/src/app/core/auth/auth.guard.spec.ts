import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Router, UrlTree } from '@angular/router';
import { provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { of } from 'rxjs';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['checkAuthentication']);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([{ path: 'login', children: [] }]),
        { provide: AuthService, useValue: authService },
      ],
    });

    router = TestBed.inject(Router);
  });

  it('should allow access when authenticated', (done) => {
    authService.checkAuthentication.and.returnValue(of(true));

    TestBed.runInInjectionContext(() => {
      const result$ = authGuard({} as any, {} as any);
      (result$ as any).subscribe((result: boolean | UrlTree) => {
        expect(result).toBeTrue();
        done();
      });
    });
  });

  it('should redirect to /login when not authenticated', (done) => {
    authService.checkAuthentication.and.returnValue(of(false));

    TestBed.runInInjectionContext(() => {
      const result$ = authGuard({} as any, {} as any);
      (result$ as any).subscribe((result: boolean | UrlTree) => {
        expect(result instanceof UrlTree).toBeTrue();
        expect((result as UrlTree).toString()).toBe('/login');
        done();
      });
    });
  });
});
