import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantFeaturesService } from '../../core/tenant/tenant-features.service';

@Component({
    selector: 'app-pro-settings',
    standalone: true,
    imports: [FormsModule, MatSlideToggleModule, MatIconModule, MatFormFieldModule, MatInputModule, TranslocoPipe],
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
    `],
})
export class ProSettingsComponent {
    protected readonly featuresService = inject(TenantFeaturesService);

    onLeaveDaysChange(value: string): void {
        const days = parseInt(value, 10);
        if (!isNaN(days) && days >= 0 && days <= 365) {
            this.featuresService.setAnnualLeaveDays(days);
        }
    }
}
