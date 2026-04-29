import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { patchState } from '@ngrx/signals';
import { of } from 'rxjs';
import { ProDashboardComponent } from './pro-dashboard.component';
import { API_BASE_URL } from '../../core/config/api-base-url.token';
import { AnalyticsService } from '../../features/analytics/analytics.service';
import { AnalyticsResponse } from '../../features/analytics/analytics.model';
import { TenantReadiness } from '../../features/dashboard/models/dashboard.model';

function mockAnalytics(): AnalyticsResponse {
  return {
    totalBookings: 42,
    totalRevenue: 120000, // cents
    attendanceRate: 0.87,
    occupancyRate: 0.65,
    avgBasket: 6500,
    cancelledCount: 3,
    noShowCount: 1,
    newClientsCount: 5,
    recurringClientsCount: 37,
    bookingsTrend: 12,
    revenueTrend: -7,
    attendanceTrend: null,
    bookingsPerDay: [],
    revenuePerDay: [{ date: '2026-04-01', value: 5000 }],
    heatmap: { '10': { 1: 2, 2: 3 }, '11': { 1: 1, 2: 4 } },
    employeeRankings: [
      { id: 1, name: 'Alice', bookingCount: 20, revenue: 80000, attendanceRate: 0.9 },
      { id: 2, name: 'Bob', bookingCount: 5, revenue: 30000, attendanceRate: 0.75 },
    ],
    clientRankings: [
      { id: 1, name: 'Claire', visitCount: 10, cancelCount: 1, noShowCount: 0, revenue: 40000, attendanceRate: 0.95 },
    ],
    careRankings: [
      { id: 1, name: 'Soin visage', bookingCount: 15, revenue: 60000 },
      { id: 2, name: 'Massage', bookingCount: 5, revenue: 20000 },
    ],
    forecastRevenue: 150000,
    forecastTrend: 5,
    atRiskClients: [],
    statusBreakdown: { CONFIRMED: 35, PENDING: 5, CANCELLED: 1, NO_SHOW: 1 },
  };
}

describe('ProDashboardComponent', () => {
  let component: ProDashboardComponent;
  let fixture: ComponentFixture<ProDashboardComponent>;
  let analyticsSpy: jasmine.SpyObj<AnalyticsService>;

  beforeEach(async () => {
    analyticsSpy = jasmine.createSpyObj<AnalyticsService>('AnalyticsService', ['getAnalytics']);
    analyticsSpy.getAnalytics.and.returnValue(of(mockAnalytics()));

    await TestBed.configureTestingModule({
      imports: [
        ProDashboardComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {}, en: {} },
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr', 'en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideNoopAnimations(),
        provideTranslocoLocale({
          defaultLocale: 'fr-FR',
          langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' },
        }),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
        { provide: AnalyticsService, useValue: analyticsSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProDashboardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('starts with week period and undefined employee/care filters', () => {
    expect(component.period()).toBe('week');
    expect(component.selectedEmployeeId()).toBeUndefined();
    expect(component.selectedCareId()).toBeUndefined();
  });

  it('setPeriod updates the signal', () => {
    component.setPeriod('month');
    expect(component.period()).toBe('month');
    component.setPeriod('year');
    expect(component.period()).toBe('year');
  });

  it('toggleRevenueChartType flips between bar and line', () => {
    expect(component.revenueChartType()).toBe('bar');
    component.toggleRevenueChartType();
    expect(component.revenueChartType()).toBe('line');
    component.toggleRevenueChartType();
    expect(component.revenueChartType()).toBe('bar');
  });

  it('formatCurrency converts cents to euros with two decimals', () => {
    expect(component.formatCurrency(12345)).toBe('123.45 \u20AC');
    expect(component.formatCurrency(100)).toBe('1.00 \u20AC');
    expect(component.formatCurrency(0)).toBe('0.00 \u20AC');
  });

  it('formatPercent rounds a [0,1] rate to integer percent', () => {
    expect(component.formatPercent(0.87)).toBe('87%');
    expect(component.formatPercent(0)).toBe('0%');
    expect(component.formatPercent(1)).toBe('100%');
    expect(component.formatPercent(0.555)).toBe('56%');
  });

  it('trendArrow returns up-arrow for non-negative, down-arrow for negative, empty for null', () => {
    expect(component.trendArrow(null)).toBe('');
    expect(component.trendArrow(10)).toBe('\u25B2');
    expect(component.trendArrow(0)).toBe('\u25B2');
    expect(component.trendArrow(-5)).toBe('\u25BC');
  });

  it('trendClass marks positive/negative consistently with trendArrow', () => {
    expect(component.trendClass(null)).toBe('');
    expect(component.trendClass(5)).toBe('trend-positive');
    expect(component.trendClass(-5)).toBe('trend-negative');
  });

  it('trendValue prefixes sign and rounds to percent', () => {
    expect(component.trendValue(null)).toBe('');
    expect(component.trendValue(12)).toBe('+12%');
    expect(component.trendValue(-7)).toBe('-7%');
    expect(component.trendValue(0)).toBe('+0%');
  });

  it('setEmployeeSort / setClientSort update the sort signals', () => {
    component.setEmployeeSort('bookingCount');
    expect(component.employeeSortKey()).toBe('bookingCount');
    component.setClientSort('revenue');
    expect(component.clientSortKey()).toBe('revenue');
  });

  it('kpis returns empty array when analyticsData is null', () => {
    expect(component.kpis()).toEqual([]);
  });

  it('kpis exposes 5 entries when analyticsData is set', () => {
    component.analyticsData.set(mockAnalytics());
    const kpis = component.kpis();
    expect(kpis.length).toBe(5);
    const keys = kpis.map((k) => k.key);
    expect(keys).toEqual(['bookings', 'revenue', 'attendance', 'occupancy', 'avgBasket']);
  });

  it('sortedEmployees sorts descending by selected key', () => {
    component.analyticsData.set(mockAnalytics());
    component.setEmployeeSort('revenue');
    const sorted = component.sortedEmployees();
    expect(sorted[0].name).toBe('Alice'); // 80000 > 30000
    expect(sorted[1].name).toBe('Bob');
  });

  it('sortedCares sorts descending by revenue', () => {
    component.analyticsData.set(mockAnalytics());
    const sorted = component.sortedCares();
    expect(sorted[0].id).toBe(1); // 60000 > 20000
    expect(sorted[1].id).toBe(2);
  });

  it('maxEmployeeValue returns max across rankings, 1 when empty', () => {
    component.analyticsData.set(mockAnalytics());
    expect(component.maxEmployeeValue('revenue')).toBe(80000);
    expect(component.maxEmployeeValue('bookingCount')).toBe(20);

    component.analyticsData.set({ ...mockAnalytics(), employeeRankings: [] });
    expect(component.maxEmployeeValue('revenue')).toBe(1);
  });

  it('employeeBarWidth scales to percentage of max', () => {
    component.analyticsData.set(mockAnalytics());
    component.setEmployeeSort('revenue');
    const alice = mockAnalytics().employeeRankings[0];
    expect(component.employeeBarWidth(alice)).toBe(100); // 80000 / 80000 * 100
    const bob = mockAnalytics().employeeRankings[1];
    expect(component.employeeBarWidth(bob)).toBeCloseTo(37.5, 1); // 30000 / 80000 * 100
  });

  it('heatmapCellColor returns neutral color for zero opacity, rose tint otherwise', () => {
    expect(component.heatmapCellColor(0)).toBe('#f5f4f2');
    expect(component.heatmapCellColor(0.5)).toBe('rgba(192, 0, 102, 0.5)');
    expect(component.heatmapCellColor(1)).toBe('rgba(192, 0, 102, 0.9)');
  });

  it('heatmapData returns normalized cells with opacity proportional to maxCount', () => {
    component.analyticsData.set(mockAnalytics());
    const h = component.heatmapData();
    expect(h.hours).toEqual([10, 11]);
    expect(h.days).toEqual([1, 2]);
    expect(h.maxCount).toBe(4);
    // Cell with count=4 should have opacity 1; count=0 → 0.
    const maxCell = h.cells.find((c) => c.count === 4);
    expect(maxCell?.opacity).toBe(1);
  });

  it('heatmapData returns empty structure when analyticsData is null', () => {
    const h = component.heatmapData();
    expect(h.hours).toEqual([]);
    expect(h.days).toEqual([]);
    expect(h.cells).toEqual([]);
    expect(h.maxCount).toBe(0);
  });

  // ─────────────────────────────────────────────────────────────
  // Onboarding checklist (DRAFT)
  // ─────────────────────────────────────────────────────────────

  describe('onboarding checklist', () => {
    function setReadiness(partial: Partial<TenantReadiness>): void {
      const r: TenantReadiness = {
        slug: 'test-salon',
        name: false,
        hasCategory: false,
        hasActiveCare: false,
        hasOpeningHours: false,
        canPublish: false,
        status: 'DRAFT',
        ...partial,
      };
      patchState(component.store as any, { readiness: r });
    }

    it('returns no steps when readiness is null', () => {
      expect(component.checklistSteps()).toEqual([]);
      expect(component.checklistDone()).toBe(0);
      expect(component.checklistTotal()).toBe(0);
      expect(component.nextStepKey()).toBeNull();
      // Edge: no division-by-zero panic
      expect(component.checklistProgressPercent()).toBe(0);
    });

    it('exposes 3 steps in fixed order: name, cares, openingHours', () => {
      setReadiness({});
      const keys = component.checklistSteps().map((s) => s.key);
      expect(keys).toEqual(['name', 'cares', 'openingHours']);
    });

    it('zero done → next is the first step (name), progress 0%', () => {
      setReadiness({});
      expect(component.checklistDone()).toBe(0);
      expect(component.nextStepKey()).toBe('name');
      expect(component.checklistProgressPercent()).toBe(0);
    });

    it('only name done → next is cares, progress ~33%', () => {
      setReadiness({ name: true });
      expect(component.checklistDone()).toBe(1);
      expect(component.nextStepKey()).toBe('cares');
      expect(component.checklistProgressPercent()).toBe(33);
    });

    it('name+cares done → next is openingHours, progress ~67%', () => {
      setReadiness({ name: true, hasActiveCare: true });
      expect(component.checklistDone()).toBe(2);
      expect(component.nextStepKey()).toBe('openingHours');
      expect(component.checklistProgressPercent()).toBe(67);
    });

    it('all done → no next step, progress 100%', () => {
      setReadiness({ name: true, hasActiveCare: true, hasOpeningHours: true, canPublish: true });
      expect(component.checklistDone()).toBe(3);
      expect(component.nextStepKey()).toBeNull();
      expect(component.checklistProgressPercent()).toBe(100);
    });

    // ─── Improbable / edge cases ───

    it('out-of-order completion: only openingHours done → next falls back to name (the first uncompleted)', () => {
      setReadiness({ hasOpeningHours: true });
      expect(component.checklistDone()).toBe(1);
      // The user "skipped" steps 1 and 2 — the highlighted next is still the first missing
      expect(component.nextStepKey()).toBe('name');
    });

    it('only cares done → next is name (still the first uncompleted)', () => {
      setReadiness({ hasActiveCare: true });
      expect(component.nextStepKey()).toBe('name');
      expect(component.checklistDone()).toBe(1);
    });

    it('inconsistent backend: canPublish=true but a step is unflagged → trust per-step flags', () => {
      // Defensive: the UI relies on per-step booleans, not on canPublish, so even a
      // contradictory canPublish doesn't lie about which step is missing.
      setReadiness({ name: true, hasActiveCare: true, hasOpeningHours: false, canPublish: true });
      expect(component.checklistDone()).toBe(2);
      expect(component.nextStepKey()).toBe('openingHours');
    });

    it('hasCategory has no impact on the checklist (currently not displayed)', () => {
      // `hasCategory` is in the readiness DTO but not part of the user-facing 3 steps.
      // Toggling it must not change displayed state.
      setReadiness({ hasCategory: true });
      expect(component.checklistDone()).toBe(0);
      expect(component.nextStepKey()).toBe('name');
    });

    it('readiness present but status=ACTIVE: computed signals still derive cleanly', () => {
      // The template hides the checklist when status !== DRAFT, but the derived
      // signals must remain side-effect-free regardless of status.
      setReadiness({ name: true, hasActiveCare: true, hasOpeningHours: true, status: 'ACTIVE', canPublish: true });
      expect(component.checklistDone()).toBe(3);
      expect(component.nextStepKey()).toBeNull();
      expect(component.checklistProgressPercent()).toBe(100);
    });

    it('readiness toggled back from done to undone updates next/progress reactively', () => {
      setReadiness({ name: true, hasActiveCare: true, hasOpeningHours: true });
      expect(component.checklistDone()).toBe(3);

      setReadiness({ name: true, hasActiveCare: true, hasOpeningHours: false });
      expect(component.checklistDone()).toBe(2);
      expect(component.nextStepKey()).toBe('openingHours');
      expect(component.checklistProgressPercent()).toBe(67);
    });

    it('each step has a unique routerLink to the editor screen', () => {
      setReadiness({});
      const links = component.checklistSteps().map((s) => s.link);
      expect(links).toEqual(['/pro/salon', '/pro/cares', '/pro/planning']);
      // No duplicates
      expect(new Set(links).size).toBe(links.length);
    });

    it('cares step carries openCreate=care queryParams when undone', () => {
      setReadiness({ name: true });
      const caresStep = component.checklistSteps().find((s) => s.key === 'cares')!;
      expect(caresStep.queryParams).toEqual({ openCreate: 'care' });
    });

    it('cares step has no queryParams once done (avoids re-opening on revisit)', () => {
      setReadiness({ hasActiveCare: true });
      const caresStep = component.checklistSteps().find((s) => s.key === 'cares')!;
      expect(caresStep.queryParams).toBeNull();
    });

    it('name and openingHours steps never carry queryParams', () => {
      setReadiness({});
      const name = component.checklistSteps().find((s) => s.key === 'name')!;
      const hours = component.checklistSteps().find((s) => s.key === 'openingHours')!;
      expect(name.queryParams).toBeNull();
      expect(hours.queryParams).toBeNull();
    });
  });
});
