import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { DatePipe } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantFeaturesService } from '../../core/tenant/tenant-features.service';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { HolidayInfo, HolidayExceptionInfo } from '../../features/salon-profile/models/salon-profile.model';

@Component({
    selector: 'app-pro-settings',
    standalone: true,
    imports: [FormsModule, MatSlideToggleModule, MatIconModule, MatFormFieldModule, MatInputModule, MatChipsModule, DatePipe, TranslocoPipe],
    template: `
        <div class="settings-page">
            <h1 class="page-title">{{ 'pro.settings.title' | transloco }}</h1>

            <div class="settings-card">
                <h2 class="section-title">{{ 'pro.settings.features' | transloco }}</h2>

                <div class="setting-row">
                    <div class="setting-info">
                        <div class="setting-label">{{ 'pro.settings.employeesLabel' | transloco }}</div>
                        <div class="setting-desc">{{ 'pro.settings.employeesDesc' | transloco }}</div>
                    </div>
                    <mat-slide-toggle
                        [checked]="featuresService.employeesEnabled()"
                        (change)="featuresService.toggleEmployees($event.checked)"
                    ></mat-slide-toggle>
                </div>
            </div>

            @if (featuresService.employeesEnabled()) {
                <div class="settings-card" style="margin-top: 16px;">
                    <h2 class="section-title">{{ 'pro.settings.leaveSection' | transloco }}</h2>

                    <div class="setting-row">
                        <div class="setting-info">
                            <div class="setting-label">{{ 'pro.settings.annualLeaveDaysLabel' | transloco }}</div>
                            <div class="setting-desc">{{ 'pro.settings.annualLeaveDaysDesc' | transloco }}</div>
                        </div>
                        <div class="days-input">
                            <input
                                type="number"
                                class="days-field"
                                [value]="featuresService.annualLeaveDays()"
                                min="0"
                                max="365"
                                (change)="onLeaveDaysChange($any($event.target).value)"
                            />
                            <span class="days-unit">{{ 'pro.settings.daysUnit' | transloco }}</span>
                        </div>
                    </div>
                </div>
            }

            <div class="settings-card" style="margin-top: 16px;">
                <h2 class="section-title">{{ 'pro.settings.holidaysSection' | transloco }}</h2>

                <div class="setting-row">
                    <div class="setting-info">
                        <div class="setting-label">{{ 'pro.settings.closedOnHolidaysLabel' | transloco }}</div>
                        <div class="setting-desc">{{ 'pro.settings.closedOnHolidaysDesc' | transloco }}</div>
                    </div>
                    <mat-slide-toggle
                        [checked]="featuresService.closedOnHolidays()"
                        (change)="featuresService.toggleClosedOnHolidays($event.checked)"
                    ></mat-slide-toggle>
                </div>

                @if (featuresService.closedOnHolidays() && upcomingHolidays().length > 0) {
                    <div class="holidays-list">
                        <div class="holidays-header">{{ 'pro.settings.upcomingHolidays' | transloco }}</div>
                        @for (holiday of upcomingHolidays(); track holiday.date) {
                            <div class="holiday-row">
                                <div class="holiday-info">
                                    <div class="holiday-name">{{ holiday.name }}</div>
                                    <div class="holiday-date">{{ holiday.date | date:'longDate' }}</div>
                                </div>
                                <button
                                    class="holiday-status-btn"
                                    [class.open]="isHolidayOpen(holiday.date)"
                                    (click)="toggleHolidayException(holiday.date)">
                                    @if (isHolidayOpen(holiday.date)) {
                                        {{ 'pro.settings.holidayOpen' | transloco }}
                                    } @else {
                                        {{ 'pro.settings.holidayClosed' | transloco }}
                                    }
                                </button>
                            </div>
                        }
                    </div>
                }
            </div>
        </div>
    `,
    styles: [`
        .settings-page { max-width: 800px; margin: 0 auto; padding: 1.5rem; }
        .page-title { font-size: 20px; font-weight: 600; color: #333; margin: 0 0 1.5rem; }
        .settings-card { background: #fff; border-radius: 14px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
        .section-title { font-size: 15px; font-weight: 600; color: #333; margin: 0 0 16px; }
        .setting-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; border-top: 1px solid #f5f5f5; }
        .setting-label { font-size: 14px; font-weight: 500; color: #333; }
        .setting-desc { font-size: 12px; color: #999; margin-top: 2px; }
        .days-input { display: flex; align-items: center; gap: 6px; }
        .days-field {
            width: 64px; padding: 6px 10px; border: 1.5px solid #e0e0e0; border-radius: 8px;
            font-family: Roboto, sans-serif; font-size: 14px; font-weight: 500; color: #333;
            text-align: center; outline: none;
        }
        .days-field:focus { border-color: #c06; box-shadow: 0 0 0 2px rgba(192,0,102,0.1); }
        .days-unit { font-size: 13px; color: #888; }
        .holidays-list { margin-top: 12px; }
        .holidays-header { font-size: 13px; font-weight: 600; color: #666; margin-bottom: 8px; padding-top: 8px; border-top: 1px solid #f5f5f5; }
        .holiday-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f8f8f8; }
        .holiday-name { font-size: 14px; font-weight: 500; color: #333; }
        .holiday-date { font-size: 12px; color: #999; margin-top: 2px; }
        .holiday-status-btn {
            padding: 4px 12px; border-radius: 16px; border: 1.5px solid #e57373;
            background: #fff5f5; color: #c62828; font-size: 12px; font-weight: 500;
            cursor: pointer; transition: all 150ms ease;
        }
        .holiday-status-btn:hover { background: #ffebee; }
        .holiday-status-btn.open {
            border-color: #81c784; background: #f1f8e9; color: #2e7d32;
        }
        .holiday-status-btn.open:hover { background: #e8f5e9; }
    `],
})
export class ProSettingsComponent implements OnInit {
    protected readonly featuresService = inject(TenantFeaturesService);
    private readonly http = inject(HttpClient);
    private readonly apiBaseUrl = inject(API_BASE_URL);

    readonly upcomingHolidays = signal<HolidayInfo[]>([]);
    readonly holidayExceptions = signal<HolidayExceptionInfo[]>([]);

    ngOnInit(): void {
        this.loadUpcomingHolidays();
        this.loadExceptions();
    }

    onLeaveDaysChange(value: string): void {
        const days = parseInt(value, 10);
        if (!isNaN(days) && days >= 0 && days <= 365) {
            this.featuresService.setAnnualLeaveDays(days);
        }
    }

    isHolidayOpen(date: string): boolean {
        const ex = this.holidayExceptions().find((e) => e.date === date);
        return ex?.open === true;
    }

    toggleHolidayException(date: string): void {
        const currentlyOpen = this.isHolidayOpen(date);
        const newOpen = !currentlyOpen;
        const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
        this.http
            .put<{ date: string; open: boolean }>(`${base}/api/pro/holidays/exceptions/${date}`, {
                open: newOpen,
            })
            .subscribe({
                next: () => {
                    const exceptions = [...this.holidayExceptions()];
                    const idx = exceptions.findIndex((e) => e.date === date);
                    if (idx >= 0) {
                        exceptions[idx] = { date, open: newOpen };
                    } else {
                        exceptions.push({ date, open: newOpen });
                    }
                    this.holidayExceptions.set(exceptions);
                },
            });
    }

    private loadUpcomingHolidays(): void {
        const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
        this.http
            .get<HolidayInfo[]>(`${base}/api/pro/holidays/upcoming`)
            .subscribe({ next: (holidays) => this.upcomingHolidays.set(holidays) });
    }

    private loadExceptions(): void {
        const base = this.apiBaseUrl?.replace(/\/$/, '') ?? '';
        this.http
            .get<HolidayExceptionInfo[]>(`${base}/api/pro/holidays/exceptions`)
            .subscribe({ next: (exceptions) => this.holidayExceptions.set(exceptions) });
    }
}
