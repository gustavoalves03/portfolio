import { Injectable, signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { Role } from './auth.model';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, of, map } from 'rxjs';
import { User } from './auth.model';
import { isPlatformBrowser } from '@angular/common';
import { API_BASE_URL } from '../config/api-base-url.token';

const TOKEN_KEY = 'auth_token';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  // Signals for reactive state
  private readonly currentUser = signal<User | null>(null);
  private readonly token = signal<string | null>(null);

  // Computed signals
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly user = this.currentUser.asReadonly();

  constructor() {
    // Load token from localStorage on init (browser only)
    if (isPlatformBrowser(this.platformId)) {
      const storedToken = localStorage.getItem(TOKEN_KEY);
      if (storedToken) {
        this.token.set(storedToken);
        this.loadCurrentUser();
      }
    }
  }

  /**
   * Register a new beauty professional with email and password
   */
  registerPro(data: {
    name: string; email: string; password: string;
    salonName: string; phone: string;
    addressStreet: string; addressPostalCode: string; addressCity: string;
    siret: string; plan: string;
  }): Observable<User> {
    return this.http.post<{accessToken: string, user: User}>(
      `${this.apiBaseUrl}/api/auth/register/pro`,
      { ...data, consent: true }
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      }),
      map(response => response.user),
      catchError(error => { throw error; })
    );
  }

  /**
   * Register a new client with email and password
   */
  registerClient(name: string, email: string, password: string): Observable<User> {
    return this.http.post<{accessToken: string, user: User}>(
      `${this.apiBaseUrl}/api/auth/register/client`,
      { name, email, password, consent: true }
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      }),
      map(response => response.user),
      catchError(error => {
        throw error;
      })
    );
  }

  /**
   * Login with email and password
   */
  loginWithCredentials(email: string, password: string): Observable<User> {
    return this.http.post<{accessToken: string, user: User}>(`${this.apiBaseUrl}/api/auth/login`, { email, password }).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      }),
      map(response => response.user),
      tap(() => console.log('Login successful')),
      catchError(error => {
        console.error('Login failed:', error);
        throw error;
      })
    );
  }

  /**
   * Initiate Google OAuth2 login
   * @param roleHint optional role hint ('client' or 'pro') for new account creation
   */
  loginWithGoogle(roleHint?: 'client' | 'pro'): void {
    if (isPlatformBrowser(this.platformId)) {
      const hint = roleHint ?? 'pro';
      window.location.href = `${this.apiBaseUrl}/oauth2/authorization/google?role_hint=${hint}`;
    }
  }

  /**
   * Handle OAuth2 redirect callback with token
   */
  handleOAuth2Callback(token: string): Observable<User> {
    this.setToken(token);
    return this.loadCurrentUser();
  }

  /**
   * Request a password reset email
   */
  requestPasswordReset(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiBaseUrl}/api/auth/forgot-password`, { email });
  }

  /**
   * Reset password with token
   */
  resetPassword(token: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiBaseUrl}/api/auth/reset-password`, { token, newPassword });
  }

  /**
   * Load current user from backend
   */
  private loadCurrentUser(): Observable<User> {
    return this.http.get<User>(`${this.apiBaseUrl}/api/auth/me`).pipe(
      tap(user => this.currentUser.set(user)),
      catchError(error => {
        console.error('Failed to load user:', error);
        this.logout();
        return of(null as any);
      })
    );
  }

  /**
   * Get current auth token
   */
  getToken(): string | null {
    return this.token();
  }

  /**
   * Set auth token
   */
  private setToken(newToken: string): void {
    this.token.set(newToken);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(TOKEN_KEY, newToken);
    }
  }

  /**
   * Logout user
   */
  logout(): void {
    this.token.set(null);
    this.currentUser.set(null);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(TOKEN_KEY);
    }
    this.router.navigate(['/']);
  }

  /**
   * Navigate to the appropriate dashboard based on user role
   */
  navigateByRole(): void {
    const role = this.currentUser()?.role;
    if (role === Role.PRO || role === Role.ADMIN) {
      this.router.navigate(['/pro/dashboard']);
    } else if (role === Role.EMPLOYEE) {
      this.router.navigate(['/employee/bookings']);
    } else {
      this.router.navigate(['/']);
    }
  }

  /**
   * Check if user is authenticated (for use in guards)
   */
  checkAuthentication(): Observable<boolean> {
    if (!this.token()) {
      return of(false);
    }

    if (this.currentUser()) {
      return of(true);
    }

    return this.loadCurrentUser().pipe(
      map(() => true),
      catchError(() => of(false)),
      tap((success: boolean) => {
        if (!success) {
          this.logout();
        }
      })
    );
  }
}
