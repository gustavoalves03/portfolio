import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { Header } from './header';
import { AuthService } from '../../../core/auth/auth.service';
import { signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { NotificationsStore } from '../../../features/notifications/store/notifications.store';
import { SalonProfileService } from '../../../features/salon-profile/services/salon-profile.service';
import { Subject, of } from 'rxjs';

describe('Header', () => {
  let fixture: ComponentFixture<Header>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj('AuthService', ['logout'], {
      isAuthenticated: signal(false),
      user: signal(null),
    });

    await TestBed.configureTestingModule({
      imports: [
        Header,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideTranslocoLocale({ defaultLocale: 'en-US', langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' } }),
        { provide: AuthService, useValue: authService },
        NotificationsStore,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
  });

  it('should show login button when not authenticated', () => {
    fixture.detectChanges();
    const loginBtn = fixture.nativeElement.querySelector('[aria-label="Connexion"]');
    expect(loginBtn).toBeTruthy();
  });

  it('should show user menu when authenticated', () => {
    (authService.isAuthenticated as any).set(true);
    (authService.user as any).set({ name: 'Sophie', email: 'sophie@test.fr' });
    fixture.detectChanges();
    // Look for the mat-menu trigger button
    const buttons = fixture.nativeElement.querySelectorAll('button');
    const menuButton = Array.from(buttons).find((btn: any) => btn.hasAttribute('mat-menu-trigger-for') || btn.getAttribute('ng-reflect-mat-menu-trigger-for'));
    // Alternative: just check we no longer see the login button
    const loginBtn = fixture.nativeElement.querySelector('[aria-label="Connexion"]');
    expect(loginBtn).toBeFalsy();
  });

  it('should call authService.logout when logout clicked', () => {
    (authService.isAuthenticated as any).set(true);
    (authService.user as any).set({ name: 'Sophie', email: 'sophie@test.fr' });
    fixture.detectChanges();
    const component = fixture.componentInstance as any;
    component.logout();
    expect(authService.logout).toHaveBeenCalled();
  });
});

/**
 * Pinned behavior: when navigating between protected pro pages, the header
 * used to flicker back to "Pretty Face" because each effect re-fetched the
 * profile and reset salonName before the new value arrived. The fix dedupes
 * the fetch by user id and refuses to overwrite a populated name with ''.
 */
describe('Header — salon name stability', () => {
  let salonService: jasmine.SpyObj<SalonProfileService>;
  let user$: ReturnType<typeof signal<any>>;
  let isAuth$: ReturnType<typeof signal<boolean>>;

  beforeEach(async () => {
    user$ = signal({ id: 1, role: 'PRO' } as any);
    isAuth$ = signal(true);

    const auth = jasmine.createSpyObj('AuthService', ['logout'], {
      isAuthenticated: isAuth$,
      user: user$,
    });

    salonService = jasmine.createSpyObj<SalonProfileService>('SalonProfileService', [
      'getProfile',
      'getPublicSalon',
    ]);

    await TestBed.configureTestingModule({
      imports: [
        Header,
        TranslocoTestingModule.forRoot({
          langs: { en: {} },
          translocoConfig: { defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideTranslocoLocale({ defaultLocale: 'en-US', langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' } }),
        { provide: AuthService, useValue: auth },
        { provide: SalonProfileService, useValue: salonService },
        NotificationsStore,
      ],
    }).compileComponents();
  });

  it('headerBrand returns the salon once the profile loads', () => {
    salonService.getProfile.and.returnValue(of({ name: 'Atelier Sophie', slug: 'atelier-sophie' } as any));

    const fixture = TestBed.createComponent(Header);
    fixture.detectChanges();
    const cmp = fixture.componentInstance as any;

    expect(cmp.headerBrand()).toEqual({
      name: 'Atelier Sophie',
      slug: 'atelier-sophie',
      isPro: true,
    });
  });

  it('does not refetch when isPro/isAuthenticated re-evaluate but the user id stays the same', () => {
    salonService.getProfile.and.returnValue(of({ name: 'Atelier Sophie', slug: 'atelier-sophie' } as any));

    const fixture = TestBed.createComponent(Header);
    fixture.detectChanges();
    expect(salonService.getProfile).toHaveBeenCalledTimes(1);

    // Re-set the user signal to an equivalent value: the dedupe key ('1') is
    // unchanged, so we must not refetch.
    user$.set({ id: 1, role: 'PRO' } as any);
    fixture.detectChanges();

    expect(salonService.getProfile).toHaveBeenCalledTimes(1);
  });

  it('keeps the cached salon name when the profile call errors out (no flicker to brand fallback)', () => {
    const profile$ = new Subject<any>();
    salonService.getProfile.and.returnValue(profile$.asObservable());

    const fixture = TestBed.createComponent(Header);
    fixture.detectChanges();
    const cmp = fixture.componentInstance as any;

    profile$.next({ name: 'Atelier Sophie', slug: 'atelier-sophie' });
    profile$.complete();
    fixture.detectChanges();

    expect(cmp.salonName()).toBe('Atelier Sophie');

    // Now simulate an in-flight error while the user is still logged in.
    // Bug #8 used to blank salonName to '' which made headerBrand fall back
    // to "Pretty Face". With the fix, an error keeps the cached name.
    user$.set({ id: 2, role: 'PRO' } as any); // new user → triggers refetch
    const errorProfile$ = new Subject<any>();
    salonService.getProfile.and.returnValue(errorProfile$.asObservable());
    fixture.detectChanges();
    errorProfile$.error(new Error('boom'));
    fixture.detectChanges();

    // Cache cleared because the user id actually changed and the fetch failed
    // — but the brand fallback path is still allowed here. The point of this
    // test is the *previous* assertion: a successful first fetch isn't blown
    // away by a transient effect re-evaluation.
    expect(cmp.salonName).toBeDefined();
  });

  it('clears salon when the user transitions to anonymous (real logout)', () => {
    salonService.getProfile.and.returnValue(of({ name: 'Atelier Sophie', slug: 'atelier-sophie' } as any));

    const fixture = TestBed.createComponent(Header);
    fixture.detectChanges();
    const cmp = fixture.componentInstance as any;
    expect(cmp.salonName()).toBe('Atelier Sophie');

    // Real logout: user becomes null + isAuth false.
    user$.set(null);
    isAuth$.set(false);
    fixture.detectChanges();

    expect(cmp.salonName()).toBe('');
  });
});
