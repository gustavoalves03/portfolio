import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { AvailabilityComponent } from './availability.component';
import { DashboardStore } from '../dashboard/store/dashboard.store';

const mockTranslations = {
  'pro.availability.title': 'My availability',
  'pro.availability.subtitle': 'Weekly hours',
  'pro.availability.closed': 'Closed',
  'pro.availability.save': 'Save',
  'pro.availability.saveSuccess': 'Hours updated',
  'pro.availability.kpi.openDays': 'Open days',
  'pro.availability.kpi.weeklyHours': 'Weekly hours',
  'pro.availability.kpi.totalSlots': 'Total slots',
  'pro.availability.preset.fullWeek': 'Full week',
  'pro.availability.preset.midDayBreak': 'With break',
  'pro.availability.preset.closeAll': 'Close all',
  'pro.availability.preset.confirmCloseAll': 'Sure?',
  'pro.availability.toolbar.summary': '{{open}} open',
  'pro.availability.timeline.addSlot': 'Add slot',
  'pro.availability.daylist.addPause': 'Add pause',
  'pro.availability.days.1': 'Mon',
  'pro.availability.days.2': 'Tue',
  'pro.availability.days.3': 'Wed',
  'pro.availability.days.4': 'Thu',
  'pro.availability.days.5': 'Fri',
  'pro.availability.days.6': 'Sat',
  'pro.availability.days.7': 'Sun',
};

describe('AvailabilityComponent', () => {
  let fixture: ComponentFixture<AvailabilityComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AvailabilityComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideTranslocoLocale({ defaultLocale: 'en-US' }),
        {
          provide: DashboardStore,
          useValue: { loadReadiness: () => {} },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AvailabilityComponent);
    fixture.detectChanges();
  });

  it('renders page header', () => {
    const title = fixture.nativeElement.querySelector('.page-title');
    expect(title?.textContent).toContain('My availability');
  });

  it('renders 3 KPI tiles', () => {
    const kpis = fixture.nativeElement.querySelectorAll('.kpi');
    expect(kpis.length).toBe(3);
  });

  it('renders the toolbar with 3 preset buttons', () => {
    const btns = fixture.nativeElement.querySelectorAll('.quick-btn');
    expect(btns.length).toBe(3);
  });

  it('renders the savebar with save button', () => {
    const btn = fixture.nativeElement.querySelector('[data-testid="availability-save"]');
    expect(btn).toBeTruthy();
  });
});
