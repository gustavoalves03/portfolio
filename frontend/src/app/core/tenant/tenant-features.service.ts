import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../config/api-base-url.token';
import { AuthService } from '../auth/auth.service';
import { Role } from '../auth/auth.model';
import { effect } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TenantFeaturesService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly authService = inject(AuthService);

  readonly employeesEnabled = signal<boolean>(false);

  constructor() {
    effect(() => {
      const user = this.authService.user();
      const isPro = user?.role === Role.PRO || user?.role === Role.ADMIN;
      if (isPro) {
        this.loadFeatures();
      } else {
        this.employeesEnabled.set(false);
      }
    });
  }

  private loadFeatures(): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http.get<{ employeesEnabled: boolean }>(`${base}/api/pro/tenant`).subscribe({
      next: (r) => this.employeesEnabled.set(r.employeesEnabled ?? false),
      error: () => this.employeesEnabled.set(false),
    });
  }

  toggleEmployees(enabled: boolean): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .put<{ enabled: boolean }>(`${base}/api/pro/tenant/settings/employees`, { enabled })
      .subscribe({ next: () => this.employeesEnabled.set(enabled) });
  }
}
