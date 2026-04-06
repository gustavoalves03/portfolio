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
  readonly minAdvanceMinutes = signal<number>(120);
  readonly maxAdvanceDays = signal<number>(90);
  readonly maxClientHoursPerDay = signal<number>(8);

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
        this.minAdvanceMinutes.set(120);
        this.maxAdvanceDays.set(90);
        this.maxClientHoursPerDay.set(8);
      }
    });
  }

  private loadFeatures(): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .get<{
        employeesEnabled: boolean;
        annualLeaveDays: number;
        closedOnHolidays: boolean;
        minAdvanceMinutes: number;
        maxAdvanceDays: number;
        maxClientHoursPerDay: number;
      }>(`${base}/api/pro/tenant`)
      .subscribe({
        next: (r) => {
          this.employeesEnabled.set(r.employeesEnabled ?? false);
          this.annualLeaveDays.set(r.annualLeaveDays ?? 25);
          this.closedOnHolidays.set(r.closedOnHolidays ?? true);
          this.minAdvanceMinutes.set(r.minAdvanceMinutes ?? 120);
          this.maxAdvanceDays.set(r.maxAdvanceDays ?? 90);
          this.maxClientHoursPerDay.set(r.maxClientHoursPerDay ?? 8);
        },
        error: () => {
          this.employeesEnabled.set(false);
          this.annualLeaveDays.set(25);
          this.closedOnHolidays.set(true);
          this.minAdvanceMinutes.set(120);
          this.maxAdvanceDays.set(90);
          this.maxClientHoursPerDay.set(8);
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

  setMinAdvanceMinutes(minutes: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .put<{ minAdvanceMinutes: number }>(
        `${base}/api/pro/tenant/settings/min-advance-minutes`,
        { minAdvanceMinutes: minutes },
      )
      .subscribe({ next: () => this.minAdvanceMinutes.set(minutes) });
  }

  setMaxAdvanceDays(days: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .put<{ maxAdvanceDays: number }>(`${base}/api/pro/tenant/settings/max-advance-days`, {
        maxAdvanceDays: days,
      })
      .subscribe({ next: () => this.maxAdvanceDays.set(days) });
  }

  setMaxClientHoursPerDay(hours: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    this.http
      .put<{ maxClientHoursPerDay: number }>(
        `${base}/api/pro/tenant/settings/max-client-hours-per-day`,
        { maxClientHoursPerDay: hours },
      )
      .subscribe({ next: () => this.maxClientHoursPerDay.set(hours) });
  }
}
