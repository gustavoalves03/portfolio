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
  readonly annualLeaveDays = signal<number>(25);
  readonly closedOnHolidays = signal<boolean>(true);

  constructor() {
    effect(() => {
      const user = this.authService.user();
      const isPro = user?.role === Role.PRO || user?.role === Role.ADMIN;
      if (isPro) {
        this.loadFeatures();
      } else {
        this.employeesEnabled.set(false);
        this.annualLeaveDays.set(25);
        this.closedOnHolidays.set(true);
      }
    });
  }

  private loadFeatures(): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .get<{ employeesEnabled: boolean; annualLeaveDays: number; closedOnHolidays: boolean }>(
        `${base}/api/pro/tenant`,
      )
      .subscribe({
        next: (r) => {
          this.employeesEnabled.set(r.employeesEnabled ?? false);
          this.annualLeaveDays.set(r.annualLeaveDays ?? 25);
          this.closedOnHolidays.set(r.closedOnHolidays ?? true);
        },
        error: () => {
          this.employeesEnabled.set(false);
          this.annualLeaveDays.set(25);
          this.closedOnHolidays.set(true);
        },
      });
  }

  toggleEmployees(enabled: boolean): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .put<{ enabled: boolean }>(`${base}/api/pro/tenant/settings/employees`, { enabled })
      .subscribe({ next: () => this.employeesEnabled.set(enabled) });
  }

  setAnnualLeaveDays(days: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .put<{ annualLeaveDays: number }>(`${base}/api/pro/tenant/settings/annual-leave-days`, { days })
      .subscribe({ next: () => this.annualLeaveDays.set(days) });
  }

  toggleClosedOnHolidays(closed: boolean): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .put<{ closedOnHolidays: boolean }>(`${base}/api/pro/tenant/settings/closed-on-holidays`, {
        closedOnHolidays: closed,
      })
      .subscribe({ next: () => this.closedOnHolidays.set(closed) });
  }
}
