import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { DatePipe } from '@angular/common';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TenantFeaturesService } from '../../core/tenant/tenant-features.service';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { HolidayInfo, HolidayExceptionInfo } from '../../features/salon-profile/models/salon-profile.model';
import { ClosedDaysStore } from '../../features/availability/closed-days.store';

@Component({
    selector: 'app-pro-settings',
    standalone: true,
    imports: [
        FormsModule,
        MatSlideToggleModule,
        MatIconModule,
        MatFormFieldModule,
        MatInputModule,
        MatChipsModule,
        MatSnackBarModule,
        DatePipe,
        TranslocoPipe,
    ],
    templateUrl: './pro-settings.component.html',
    styleUrl: './pro-settings.component.scss',
})
export class ProSettingsComponent implements OnInit {
    protected readonly featuresService = inject(TenantFeaturesService);
    private readonly http = inject(HttpClient);
    private readonly apiBaseUrl = inject(API_BASE_URL);
    private readonly closedDaysStore = inject(ClosedDaysStore);
    private readonly snackBar = inject(MatSnackBar);
    private readonly i18n = inject(TranslocoService);

    readonly upcomingHolidays = signal<HolidayInfo[]>([]);
    readonly holidaysOpen = signal(false);
    readonly holidayExceptions = signal<HolidayExceptionInfo[]>([]);

    /** Currently visible section (anchor). */
    readonly activeSection = signal<'features' | 'leave' | 'booking' | 'holidays'>('features');

    ngOnInit(): void {
        this.loadUpcomingHolidays();
        this.loadExceptions();
    }

    scrollTo(section: 'features' | 'leave' | 'booking' | 'holidays'): void {
        this.activeSection.set(section);
        const el = document.getElementById(section);
        if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    onLeaveDaysChange(value: string): void {
        const days = parseInt(value, 10);
        if (!isNaN(days) && days >= 0 && days <= 365) {
            this.featuresService.setAnnualLeaveDays(days);
        }
    }

    onMinAdvanceChange(value: string): void {
        const minutes = parseInt(value, 10);
        if (!isNaN(minutes) && minutes >= 0) {
            this.featuresService.setMinAdvanceMinutes(minutes);
        } else {
            this.notifyInvalidNumber();
        }
    }

    onMaxAdvanceChange(value: string): void {
        const days = parseInt(value, 10);
        if (!isNaN(days) && days >= 0) {
            this.featuresService.setMaxAdvanceDays(days);
        } else {
            this.notifyInvalidNumber();
        }
    }

    onMaxClientHoursChange(value: string): void {
        const hours = parseInt(value, 10);
        if (!isNaN(hours) && hours >= 0) {
            this.featuresService.setMaxClientHoursPerDay(hours);
        } else {
            this.notifyInvalidNumber();
        }
    }

    private notifyInvalidNumber(): void {
        // Surface a hint to the pro: silently dropping the change confused QA.
        this.snackBar.open(
            this.i18n.translate('pro.settings.invalidNumber'),
            'OK',
            { duration: 3000, panelClass: 'snackbar-error' }
        );
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
                    this.closedDaysStore.invalidate();
                },
            });
    }

    /** Compute weekday name for a holiday in the active locale. */
    weekdayName(iso: string): string {
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return '';
        const lang = this.i18n.getActiveLang();
        const locale = lang === 'fr' ? 'fr-FR' : 'en-GB';
        return d.toLocaleDateString(locale, { weekday: 'long' });
    }

    /** Days from today to the holiday, e.g. "dans 1 jour" or "dans 21 jours". */
    daysUntil(iso: string): string {
        const target = new Date(iso);
        if (Number.isNaN(target.getTime())) return '';
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        target.setHours(0, 0, 0, 0);
        const diff = Math.round((target.getTime() - today.getTime()) / 86400000);
        if (diff === 0) return this.i18n.translate('pro.settings.today');
        if (diff === 1) return this.i18n.translate('pro.settings.inOneDay');
        return this.i18n.translate('pro.settings.inDays', { n: diff });
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
