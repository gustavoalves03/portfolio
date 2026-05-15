import { Injectable, signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { Role, TenantSummary, User, AuthResponse } from './auth.model';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, of, map } from 'rxjs';
import { isPlatformBrowser } from '@angular/common';
import { API_BASE_URL } from '../config/api-base-url.token';
import { TenantStatusService } from '../tenant/tenant-status.service';

const TOKEN_KEY = 'auth_token';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly tenantStatus = inject(TenantStatusService);

  // Signals for reactive state
  private readonly currentUser = signal<User | null>(null);
  private readonly token = signal<string | null>(null);

  // Computed signals
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly user = this.currentUser.asReadonly();

  // Scoped-RBAC signals derived from the JWT-driven currentUser
  readonly roles = computed<Role[]>(() => this.currentUser()?.roles ?? []);
  readonly activeTenantId = computed<number | null>(() => this.currentUser()?.activeTenantId ?? null);
  readonly availableTenants = computed<TenantSummary[]>(() => this.currentUser()?.availableTenants ?? []);
  readonly isClientMode = computed<boolean>(() => this.activeTenantId() === null);

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
   * Initiate Google OAuth2 login. The role hint controls what kind of account
   * gets created on first login: 'client' (default — no tenant) or 'pro'
   * (provisions a salon tenant). Generic login surfaces (header modal, /login
   * page) MUST pass 'client' or rely on the default; only the dedicated pro
   * sign-up flow should pass 'pro'.
   */
  loginWithGoogle(roleHint: 'client' | 'pro' = 'client'): void {
    if (isPlatformBrowser(this.platformId)) {
      window.location.href = `${this.apiBaseUrl}/oauth2/authorization/google?role_hint=${roleHint}`;
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
    this.tenantStatus.reset();
    this.token.set(null);
    this.currentUser.set(null);
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(TOKEN_KEY);
    }
    this.router.navigate(['/']);
  }

  /**
   * Navigate to the appropriate dashboard based on the user's roles and
   * whether a tenant context is active. In client mode (no activeTenantId),
   * pros and employees go to the public home — they're acting as clients.
   */
  navigateByRole(): void {
    const inClientMode = this.isClientMode();
    if (!inClientMode && this.hasRole(Role.PRO, Role.ADMIN)) {
      this.router.navigate(['/pro/dashboard']);
    } else if (!inClientMode && this.hasRole(Role.EMPLOYEE)) {
      this.router.navigate(['/employee/bookings']);
    } else {
      this.router.navigate(['/']);
    }
  }

  /**
   * True if the user holds ANY of the given roles.
   * ADMIN is NOT auto-promoted — callers that want ADMIN to bypass must
   * include Role.ADMIN explicitly: hasRole(Role.PRO, Role.ADMIN).
   */
  hasRole(...required: Role[]): boolean {
    const userRoles = this.roles();
    return required.some(r => userRoles.includes(r));
  }

  /**
   * Switch the active tenant. Pass null to drop into client mode (no tenant
   * context, only GLOBAL roles apply). Re-issues the JWT and updates the
   * stored user.
   */
  switchTenant(tenantId: number | null): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(
      `${this.apiBaseUrl}/api/me/switch-tenant`,
      { tenantId }
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken);
        this.currentUser.set(response.user);
      })
    );
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
