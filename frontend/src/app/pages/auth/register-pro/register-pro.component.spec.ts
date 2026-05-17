import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';

import { RegisterProComponent } from './register-pro.component';
import { AuthService } from '../../../core/auth/auth.service';

describe('RegisterProComponent', () => {
  let component: RegisterProComponent;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.Spy;

  function setup(
    queryParams: Record<string, string> = {},
    overrides: { authResponse?: 'success' | { status: number } } = {}
  ): RegisterProComponent {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['registerPro']);
    if (overrides.authResponse && typeof overrides.authResponse !== 'string') {
      authService.registerPro.and.returnValue(throwError(() => overrides.authResponse));
    } else {
      authService.registerPro.and.returnValue(of({} as any));
    }

    const route = {
      snapshot: { queryParamMap: convertToParamMap(queryParams) },
    } as unknown as ActivatedRoute;

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [
        RegisterProComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        { provide: ActivatedRoute, useValue: route },
      ],
    });

    router = spyOn(TestBed.inject(Router), 'navigate').and.callFake(() => Promise.resolve(true));

    const fixture = TestBed.createComponent(RegisterProComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function fillAccount(c: RegisterProComponent): void {
    c.name.set('Sophie Martin');
    c.email.set('sophie@test.com');
    c.password.set('Password1!');
  }

  function fillBusiness(c: RegisterProComponent, withConsent = true): void {
    c.salonName.set('Mon Salon');
    c.consent.set(withConsent);
  }

  // ── isAccountValid ──

  it('isAccountValid is false when fields are empty', () => {
    const c = setup();
    expect(c.isAccountValid()).toBeFalse();
  });

  it('isAccountValid requires non-empty name', () => {
    const c = setup();
    c.email.set('a@b.c');
    c.password.set('Password1!');
    expect(c.isAccountValid()).toBeFalse();
    c.name.set('Sophie');
    expect(c.isAccountValid()).toBeTrue();
  });

  it('isAccountValid requires email containing @', () => {
    const c = setup();
    c.name.set('Sophie');
    c.password.set('Password1!');
    c.email.set('not-an-email');
    expect(c.isAccountValid()).toBeFalse();
    c.email.set('a@b');
    expect(c.isAccountValid()).toBeTrue();
  });

  it('isAccountValid requires password >= 8 chars', () => {
    const c = setup();
    c.name.set('Sophie');
    c.email.set('a@b.c');
    c.password.set('short');
    expect(c.isAccountValid()).toBeFalse();
    c.password.set('12345678');
    expect(c.isAccountValid()).toBeTrue();
  });

  // ── passwordsMatch ──

  it('passwordsMatch is true when both signals match', () => {
    const c = setup();
    c.password.set('secret123');
    c.confirmPassword.set('secret123');
    expect(c.passwordsMatch()).toBe(true);
  });

  it('passwordsMatch is false when signals differ', () => {
    const c = setup();
    c.password.set('secret123');
    c.confirmPassword.set('wrong');
    expect(c.passwordsMatch()).toBe(false);
  });

  // ── isBusinessValid ──

  it('isBusinessValid requires salonName AND consent', () => {
    const c = setup();
    expect(c.isBusinessValid()).toBeFalse();
    c.salonName.set('Salon');
    expect(c.isBusinessValid()).toBeFalse(); // missing consent
    c.consent.set(true);
    expect(c.isBusinessValid()).toBeTrue();
    c.salonName.set('   '); // whitespace-only
    expect(c.isBusinessValid()).toBeFalse();
  });

  // ── submit() guards ──

  it('submit is a no-op when account is invalid', () => {
    const c = setup();
    fillBusiness(c);
    c.submit();
    expect(authService.registerPro).not.toHaveBeenCalled();
  });

  it('submit is a no-op when consent is unchecked (the QA blocker)', () => {
    const c = setup();
    fillAccount(c);
    fillBusiness(c, /* withConsent */ false);
    c.submit();
    expect(authService.registerPro).not.toHaveBeenCalled();
  });

  it('submit is a no-op when salon name is missing', () => {
    const c = setup();
    fillAccount(c);
    c.consent.set(true);
    // salonName is empty by default
    c.submit();
    expect(authService.registerPro).not.toHaveBeenCalled();
  });

  // ── submit() success path ──

  it('submit POSTs all fields and navigates to /pro/dashboard on success', () => {
    const c = setup();
    fillAccount(c);
    c.salonName.set('Mon Salon');
    c.phone.set('0102030405');
    c.addressStreet.set('1 rue X');
    c.addressPostalCode.set('75001');
    c.addressCity.set('Paris');
    c.siret.set('12345678901234');
    c.consent.set(true);
    c.selectedPlan.set('pro');

    c.submit();

    expect(authService.registerPro).toHaveBeenCalledOnceWith({
      name: 'Sophie Martin',
      email: 'sophie@test.com',
      password: 'Password1!',
      tier: 'GESTION',
      billing: 'MONTHLY',
      salonName: 'Mon Salon',
      phone: '0102030405',
      addressStreet: '1 rue X',
      addressPostalCode: '75001',
      addressCity: 'Paris',
      siret: '12345678901234',
    });
    expect(router).toHaveBeenCalledOnceWith(['/pro/dashboard']);
    expect(c.isLoading()).toBeFalse();
  });

  it('submit trims whitespace from text fields before POSTing', () => {
    const c = setup();
    c.name.set('  Sophie  ');
    c.email.set('  sophie@test.com  ');
    c.password.set('Password1!');
    c.salonName.set('  Mon Salon  ');
    c.consent.set(true);

    c.submit();

    const call = authService.registerPro.calls.mostRecent().args[0];
    expect(call.name).toBe('Sophie');
    expect(call.email).toBe('sophie@test.com');
    expect(call.salonName).toBe('Mon Salon');
  });

  // ── submit() error paths ──

  it('submit on 409 conflict: surfaces emailConflict error and goes back to account step', () => {
    const c = setup({}, { authResponse: { status: 409 } });
    fillAccount(c);
    fillBusiness(c);
    c.step.set('business');

    c.submit();

    expect(c.error()).toBe('register.errors.emailConflict');
    expect(c.step()).toBe('account');
    expect(router).not.toHaveBeenCalled();
  });

  it('submit on generic error: surfaces generic error and stays on business step', () => {
    const c = setup({}, { authResponse: { status: 500 } });
    fillAccount(c);
    fillBusiness(c);
    c.step.set('business');

    c.submit();

    expect(c.error()).toBe('register.errors.generic');
    expect(c.step()).toBe('business');
  });

  // ── ngOnInit deep link ──

  it('ngOnInit jumps to account step when ?plan=free is passed', () => {
    const c = setup({ plan: 'free' });
    expect(c.selectedPlan()).toBe('free');
    expect(c.step()).toBe('account');
  });

  it('ngOnInit ignores unknown plan values', () => {
    const c = setup({ plan: 'platinum' });
    expect(c.step()).toBe('pricing');
  });

  it('ngOnInit defaults to pricing step when no plan param', () => {
    const c = setup();
    expect(c.step()).toBe('pricing');
  });

  // ─────────────────────────────────────────────────────────────
  // Adversarial: spam submit, race conditions
  // ─────────────────────────────────────────────────────────────

  describe('adversarial', () => {
    it('rapid double-click on submit while loading: only the first call goes through', () => {
      // We simulate "in-flight" by never resolving the auth response so
      // isLoading() stays true after the first submit. A double-clicker
      // shouldn't be able to fire a second registerPro POST.
      authService = jasmine.createSpyObj<AuthService>('AuthService', ['registerPro']);
      // never resolves
      authService.registerPro.and.returnValue(new Observable<any>(() => {}));

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [
          RegisterProComponent,
          TranslocoTestingModule.forRoot({
            langs: { en: {} },
            translocoConfig: { defaultLang: 'en' },
          }),
        ],
        providers: [
          provideZonelessChangeDetection(),
          provideHttpClient(),
          provideHttpClientTesting(),
          provideNoopAnimations(),
          provideRouter([]),
          { provide: AuthService, useValue: authService },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
          },
        ],
      });
      const fixture = TestBed.createComponent(RegisterProComponent);
      fixture.detectChanges();
      const c = fixture.componentInstance;
      fillAccount(c);
      fillBusiness(c);

      c.submit();
      expect(c.isLoading()).toBeTrue();
      // Re-entrancy guard: a second click while the first POST is in flight
      // is silently dropped so we don't get a 409 ping-pong on the user.
      c.submit();
      c.submit();
      expect(authService.registerPro).toHaveBeenCalledTimes(1);
    });
  });
});
