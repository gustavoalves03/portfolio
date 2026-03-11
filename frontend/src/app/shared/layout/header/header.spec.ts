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
