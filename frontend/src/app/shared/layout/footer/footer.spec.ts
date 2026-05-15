import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Footer } from './footer';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthProvider, Role, User } from '../../../core/auth/auth.model';

describe('Footer', () => {
  function makeUser(roles: Role[]): User {
    return {
      id: 1,
      name: 'X',
      email: 'x@x.com',
      provider: AuthProvider.LOCAL,
      roles,
      activeTenantId: roles.length > 0 ? 42 : null,
      availableTenants: [],
    };
  }

  function setup(opts: { user: User | null; isAuthenticated: boolean }) {
    const userSig = signal(opts.user);
    const authSig = signal(opts.isAuthenticated);
    const rolesSig = signal<Role[]>(opts.user?.roles ?? []);

    TestBed.configureTestingModule({
      imports: [
        Footer,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: AuthService,
          useValue: {
            user: userSig,
            isAuthenticated: authSig,
            roles: rolesSig,
            hasRole: (...required: Role[]) => required.some(r => rolesSig().includes(r)),
          },
        },
      ],
    });

    const fixture = TestBed.createComponent(Footer);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the full dark footer for an unauthenticated visitor', () => {
    const fixture = setup({ user: null, isAuthenticated: false });
    expect(fixture.debugElement.query(By.css('.lp-footer'))).withContext('full footer').not.toBeNull();
    expect(fixture.debugElement.query(By.css('.lp-footer-pro'))).toBeNull();
    expect(fixture.debugElement.queryAll(By.css('.lp-footer__col')).length).toBe(3);
    expect(fixture.debugElement.query(By.css('.lp-footer__brand'))).not.toBeNull();
  });

  it('renders the minimal pro footer for a logged-in PRO user', () => {
    const fixture = setup({
      user: makeUser([Role.PRO]),
      isAuthenticated: true,
    });
    expect(fixture.debugElement.query(By.css('.lp-footer-pro'))).withContext('pro footer').not.toBeNull();
    expect(fixture.debugElement.query(By.css('.lp-footer'))).toBeNull();
  });

  it('renders the full footer for a logged-in client (no roles)', () => {
    const fixture = setup({
      user: makeUser([]),
      isAuthenticated: true,
    });
    expect(fixture.debugElement.query(By.css('.lp-footer'))).withContext('full footer').not.toBeNull();
    expect(fixture.debugElement.query(By.css('.lp-footer-pro'))).toBeNull();
  });

  it('does not contain any "Pretty Face" hardcoded text in any state', () => {
    for (const opts of [
      { user: null, isAuthenticated: false },
      { user: makeUser([Role.PRO]), isAuthenticated: true },
      { user: makeUser([]), isAuthenticated: true },
    ]) {
      const fixture = setup(opts);
      const html: string = fixture.nativeElement.innerHTML;
      expect(html).withContext(`Pretty Face leak in state ${JSON.stringify(opts)}`).not.toMatch(/Pretty\s*Face/i);
      TestBed.resetTestingModule();
    }
  });
});
