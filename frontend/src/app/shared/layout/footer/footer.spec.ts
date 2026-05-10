import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { Footer } from './footer';
import { AuthService } from '../../../core/auth/auth.service';
import { Role } from '../../../core/auth/auth.model';

describe('Footer', () => {
  function setup(opts: { user: any; isAuthenticated: boolean }) {
    const userSig = signal(opts.user);
    const authSig = signal(opts.isAuthenticated);

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
      user: { id: 1, role: Role.PRO },
      isAuthenticated: true,
    });
    expect(fixture.debugElement.query(By.css('.lp-footer-pro'))).withContext('pro footer').not.toBeNull();
    expect(fixture.debugElement.query(By.css('.lp-footer'))).toBeNull();
  });

  it('renders the full footer for a logged-in client (non-pro)', () => {
    const fixture = setup({
      user: { id: 1, role: Role.USER },
      isAuthenticated: true,
    });
    expect(fixture.debugElement.query(By.css('.lp-footer'))).withContext('full footer').not.toBeNull();
    expect(fixture.debugElement.query(By.css('.lp-footer-pro'))).toBeNull();
  });

  it('does not contain any "Pretty Face" hardcoded text in any state', () => {
    for (const opts of [
      { user: null, isAuthenticated: false },
      { user: { id: 1, role: Role.PRO }, isAuthenticated: true },
      { user: { id: 1, role: Role.USER }, isAuthenticated: true },
    ]) {
      const fixture = setup(opts);
      const html: string = fixture.nativeElement.innerHTML;
      expect(html).withContext(`Pretty Face leak in state ${JSON.stringify(opts)}`).not.toMatch(/Pretty\s*Face/i);
      TestBed.resetTestingModule();
    }
  });
});
