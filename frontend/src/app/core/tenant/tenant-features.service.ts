import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoService } from '@jsverse/transloco';
import { API_BASE_URL } from '../config/api-base-url.token';
import { AuthService } from '../auth/auth.service';
import { Role } from '../auth/auth.model';
import { effect } from '@angular/core';
import { ClosedDaysStore } from '../../features/availability/closed-days.store';

@Injectable({ providedIn: 'root' })
export class TenantFeaturesService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly authService = inject(AuthService);
  private readonly closedDaysStore = inject(ClosedDaysStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly i18n = inject(TranslocoService);

  /** Discrete confirmation that an auto-saved setting was persisted. */
  private notifyAutoSaveSuccess(): void {
    this.snackBar.open(this.i18n.translate('pro.settings.saveSuccess'), undefined, {
      duration: 1800,
    });
  }

  readonly employeesEnabled = signal<boolean>(false);
  readonly annualLeaveDays = signal<number>(25);
  readonly closedOnHolidays = signal<boolean>(true);
  readonly minAdvanceMinutes = signal<number>(120);
  readonly maxAdvanceDays = signal<number>(90);
  readonly maxClientHoursPerDay = signal<number>(8);

  // Per-setter sequence numbers. When two PUTs to the same field are in
  // flight at once, only the response that matches the latest issued seq
  // commits to the signal — older responses arriving late are discarded.
  // Without this guard, HTTP/2 multiplexing or a slow network could let
  // the *first* request's response overwrite a more recent value.
  private seq = {
    employees: 0,
    annualLeaveDays: 0,
    closedOnHolidays: 0,
    minAdvanceMinutes: 0,
    maxAdvanceDays: 0,
    maxClientHoursPerDay: 0,
  };

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
    const mySeq = ++this.seq.employees;
    this.http
      .put<{ enabled: boolean }>(`${base}/api/pro/tenant/settings/employees`, { enabled })
      .subscribe({
        next: () => {
          if (mySeq !== this.seq.employees) return;
          this.employeesEnabled.set(enabled);
          this.notifyAutoSaveSuccess();
        },
        error: () => {},
      });
  }

  setAnnualLeaveDays(days: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    const mySeq = ++this.seq.annualLeaveDays;
    this.http
      .put<{ annualLeaveDays: number }>(`${base}/api/pro/tenant/settings/annual-leave-days`, { days })
      .subscribe({
        next: () => {
          if (mySeq !== this.seq.annualLeaveDays) return;
          this.annualLeaveDays.set(days);
          this.notifyAutoSaveSuccess();
        },
        error: () => {},
      });
  }

  toggleClosedOnHolidays(closed: boolean): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    const mySeq = ++this.seq.closedOnHolidays;
    this.http
      .put<{ closedOnHolidays: boolean }>(`${base}/api/pro/tenant/settings/closed-on-holidays`, {
        closedOnHolidays: closed,
      })
      .subscribe({
        next: () => {
          if (mySeq !== this.seq.closedOnHolidays) return;
          this.closedOnHolidays.set(closed);
          this.closedDaysStore.invalidate();
          this.notifyAutoSaveSuccess();
        },
        error: () => {},
      });
  }

  setMinAdvanceMinutes(minutes: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    const mySeq = ++this.seq.minAdvanceMinutes;
    this.http
      .put<{ minAdvanceMinutes: number }>(
        `${base}/api/pro/tenant/settings/min-advance-minutes`,
        { minAdvanceMinutes: minutes },
      )
      .subscribe({
        next: () => {
          if (mySeq !== this.seq.minAdvanceMinutes) return;
          this.minAdvanceMinutes.set(minutes);
          this.notifyAutoSaveSuccess();
        },
        error: () => {},
      });
  }

  setMaxAdvanceDays(days: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    const mySeq = ++this.seq.maxAdvanceDays;
    this.http
      .put<{ maxAdvanceDays: number }>(`${base}/api/pro/tenant/settings/max-advance-days`, {
        maxAdvanceDays: days,
      })
      .subscribe({
        next: () => {
          if (mySeq !== this.seq.maxAdvanceDays) return;
          this.maxAdvanceDays.set(days);
          this.notifyAutoSaveSuccess();
        },
        error: () => {},
      });
  }

  setMaxClientHoursPerDay(hours: number): void {
    const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
    const mySeq = ++this.seq.maxClientHoursPerDay;
    this.http
      .put<{ maxClientHoursPerDay: number }>(
        `${base}/api/pro/tenant/settings/max-client-hours-per-day`,
        { maxClientHoursPerDay: hours },
      )
      .subscribe({
        next: () => {
          if (mySeq !== this.seq.maxClientHoursPerDay) return;
          this.maxClientHoursPerDay.set(hours);
          this.notifyAutoSaveSuccess();
        },
        error: () => {},
      });
  }
}
