import { Component, inject, signal, OnInit, PLATFORM_ID } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatSlideToggleModule, MatSlideToggle } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { DatePipe, isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TenantFeaturesService } from '../../core/tenant/tenant-features.service';
import { FeatureFlagsStore } from '../../core/feature-flags/feature-flags.store';
import { FeatureUpgradeDialogComponent } from '../../core/feature-flags/feature-upgrade-dialog.component';
import { SubscriptionService } from '../../features/subscription/services/subscription.service';
import { SubscriptionResponse } from '../../features/subscription/models/subscription.model';
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
        MatButtonModule,
        MatProgressSpinnerModule,
        MatFormFieldModule,
        MatInputModule,
        MatChipsModule,
        MatSnackBarModule,
        RouterLink,
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
    private readonly featureFlags = inject(FeatureFlagsStore);
    private readonly dialog = inject(MatDialog);
    private readonly subscriptionService = inject(SubscriptionService);
    private readonly platformId = inject(PLATFORM_ID);

    /** Whether the tenant's tier unlocks the EMPLOYEES capability. */
    protected readonly employeesFeatureEnabled = this.featureFlags.isEnabled('EMPLOYEES');

    /** Loading state while we fetch the Stripe billing-portal URL. */
    protected readonly portalLoading = signal(false);

    /** Current subscription; null until loaded. Drives the subscription section. */
    protected readonly subscription = signal<SubscriptionResponse | null>(null);

    /**
     * The billing portal only exists for tenants that already have a Stripe
     * customer (i.e. a real paid subscription). For VITRINE / never-subscribed
     * tenants there is nothing to manage, so we show an upgrade prompt instead.
     */
    protected readonly hasBillingAccount = signal(false);

    /**
     * Opens the Stripe customer billing portal, where the user can update their
     * payment method or cancel (unsubscribe). Stripe hosts the cancellation flow,
     * so we just redirect to the session URL it returns.
     */
    protected openBillingPortal(): void {
        if (this.portalLoading()) return;
        this.portalLoading.set(true);
        this.subscriptionService.createPortalSession().subscribe({
            next: ({ url }) => {
                if (isPlatformBrowser(this.platformId)) {
                    window.location.href = url;
                } else {
                    this.portalLoading.set(false);
                }
            },
            error: (err) => {
                this.portalLoading.set(false);
                // 400 = no Stripe customer yet (nothing to manage); anything else is
                // an unexpected failure. Surface the right message either way.
                const key =
                    err?.status === 400
                        ? 'pro.settings.subscription.noAccount'
                        : 'pro.settings.subscription.error';
                this.snackBar.open(this.i18n.translate(key), undefined, { duration: 4000 });
            },
        });
    }

    /**
     * Settings toggle for the "manage employees" capability. If the tier does not
     * include EMPLOYEES and the user tries to turn it on, show an upgrade modal and
     * leave the setting off — the toggle reverts because [checked] is bound to the
     * service signal, which we never mutate here.
     */
    protected onToggleEmployees(checked: boolean, toggle: MatSlideToggle): void {
        if (checked && !this.employeesFeatureEnabled()) {
            // Revert the visual state synchronously: [checked] stays false but
            // mat-slide-toggle already flipped its own internal state, and Angular
            // won't re-run the binding (the bound value didn't change), so force it.
            toggle.checked = false;
            this.dialog.open(FeatureUpgradeDialogComponent, {
                data: { feature: 'EMPLOYEES' },
                autoFocus: false,
            });
            return;
        }
        this.featuresService.toggleEmployees(checked);
    }

    readonly upcomingHolidays = signal<HolidayInfo[]>([]);
    readonly holidaysOpen = signal(false);
    readonly holidayExceptions = signal<HolidayExceptionInfo[]>([]);

    /** Currently visible section (anchor). */
    readonly activeSection = signal<'features' | 'leave' | 'booking' | 'holidays'>('features');

    ngOnInit(): void {
        this.loadUpcomingHolidays();
        this.loadExceptions();
        this.loadSubscription();
    }

    private loadSubscription(): void {
        this.subscriptionService.getCurrentSubscription().subscribe({
            next: (sub) => {
                this.subscription.set(sub);
                this.hasBillingAccount.set(!!sub.stripeCustomerId);
            },
            error: () => {
                this.subscription.set(null);
                this.hasBillingAccount.set(false);
            },
        });
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
