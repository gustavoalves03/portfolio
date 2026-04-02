import { Component, inject } from '@angular/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { TranslocoPipe } from '@jsverse/transloco';
import { TenantFeaturesService } from '../../core/tenant/tenant-features.service';

@Component({
    selector: 'app-pro-settings',
    standalone: true,
    imports: [MatSlideToggleModule, MatIconModule, TranslocoPipe],
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
    `],
})
export class ProSettingsComponent {
    protected readonly featuresService = inject(TenantFeaturesService);
}
