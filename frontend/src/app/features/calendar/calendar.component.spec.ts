import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { CalendarComponent } from './calendar.component';
import { CalendarStore } from './calendar.store';
import { BlockedSlotResponse, CalendarDay } from './calendar.model';

const mockTranslations = {
  'pro.calendar.title': 'My calendar',
  'pro.calendar.available': 'Available',
  'pro.calendar.closed': 'Closed',
  'pro.calendar.blockSlot': 'Block a slot',
  'pro.calendar.unblock': 'Unblock',
  'pro.calendar.fullDay': 'Full day',
  'pro.calendar.reason': 'Reason (optional)',
  'pro.calendar.confirm': 'Confirm',
  'pro.calendar.blockSuccess': 'Slot blocked',
  'pro.calendar.unblockSuccess': 'Slot unblocked',
  'pro.calendar.selectDay': 'Select a day from the calendar',
  'pro.calendar.legend.today': 'Today',
  'pro.calendar.legend.blocked': 'Blocked slot',
  'pro.calendar.legend.closed': 'Closed',
  'common.cancel': 'Cancel',
};

describe('CalendarComponent', () => {
  let component: CalendarComponent;
  let fixture: ComponentFixture<CalendarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        CalendarComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: mockTranslations },
          translocoConfig: { defaultLang: 'en', availableLangs: ['en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideTranslocoLocale({
          defaultLocale: 'en-US',
          langToLocaleMapping: { en: 'en-US', fr: 'fr-FR' },
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ── Month navigation ──

  it('should display current month label', () => {
    expect(component.monthLabel()).toBeTruthy();
    expect(component.monthLabel().length).toBeGreaterThan(0);
  });

  it('should navigate to previous month', () => {
    const initial = component.currentMonth();
    component.prevMonth();
    const updated = component.currentMonth();
    expect(updated.getMonth()).toBe(initial.getMonth() === 0 ? 11 : initial.getMonth() - 1);
  });

  it('should navigate to next month', () => {
    const initial = component.currentMonth();
    component.nextMonth();
    const updated = component.currentMonth();
    expect(updated.getMonth()).toBe(initial.getMonth() === 11 ? 0 : initial.getMonth() + 1);
  });

  // ── Calendar grid ──

  it('should generate 42 calendar days (6 rows)', () => {
    expect(component.calendarDays().length).toBe(42);
  });

  it('should mark current month days correctly', () => {
    const currentMonthDays = component.calendarDays().filter((d) => d.isCurrentMonth);
    const month = component.currentMonth();
    const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
    expect(currentMonthDays.length).toBe(daysInMonth);
  });

  it('should mark today in the grid', () => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const currentMonth = component.currentMonth();
    if (
      today.getMonth() === currentMonth.getMonth() &&
      today.getFullYear() === currentMonth.getFullYear()
    ) {
      const todayCell = component.calendarDays().find((d) => d.isToday);
      expect(todayCell).toBeTruthy();
      expect(todayCell!.dayOfMonth).toBe(today.getDate());
    }
  });

  it('should set padding days as not current month', () => {
    const firstCurrentIdx = component.calendarDays().findIndex((d) => d.isCurrentMonth);
    if (firstCurrentIdx > 0) {
      expect(component.calendarDays()[0].isCurrentMonth).toBeFalse();
    }
  });

  // ── Day selection ──

  it('should have no selected date initially', () => {
    expect(component.selectedDate()).toBeNull();
  });

  it('should select a day from the current month', () => {
    const currentDay = component.calendarDays().find((d) => d.isCurrentMonth)!;
    component.selectDay(currentDay);
    expect(component.selectedDate()).toEqual(currentDay.date);
  });

  it('should not select a day from another month', () => {
    const otherDay = component.calendarDays().find((d) => !d.isCurrentMonth);
    if (otherDay) {
      component.selectDay(otherDay);
      expect(component.selectedDate()).toBeNull();
    }
  });

  it('should close block form when selecting a new day', () => {
    const days = component.calendarDays().filter((d) => d.isCurrentMonth);
    component.selectDay(days[0]);
    component.openBlockForm();
    expect(component.showBlockForm()).toBeTrue();

    component.selectDay(days[1]);
    expect(component.showBlockForm()).toBeFalse();
  });

  it('should identify selected day correctly', () => {
    const day = component.calendarDays().find((d) => d.isCurrentMonth)!;
    component.selectDay(day);
    expect(component.isSelected(day)).toBeTrue();

    const other = component.calendarDays().find(
      (d) => d.isCurrentMonth && d.dayOfMonth !== day.dayOfMonth
    );
    if (other) {
      expect(component.isSelected(other)).toBeFalse();
    }
  });

  // ── Opening hours for selected day ──

  it('should return empty opening hours when no date selected', () => {
    expect(component.selectedDayOpeningHours()).toEqual([]);
  });

  it('should mark selected day as closed when no opening hours exist', () => {
    const day = component.calendarDays().find((d) => d.isCurrentMonth)!;
    component.selectDay(day);
    // No opening hours loaded → closed
    expect(component.isSelectedDayClosed()).toBeTrue();
  });

  // ── Blocked slots for selected day ──

  it('should return empty blocks when no date selected', () => {
    expect(component.selectedDayBlocks()).toEqual([]);
  });

  // ── Block form ──

  it('should open block form with default values', () => {
    component.openBlockForm();
    expect(component.showBlockForm()).toBeTrue();
    expect(component.blockFullDay()).toBeFalse();
    expect(component.blockStartTime()).toBe('09:00');
    expect(component.blockEndTime()).toBe('18:00');
    expect(component.blockReason()).toBe('');
  });

  it('should reset block form fields when opening', () => {
    component.blockFullDay.set(true);
    component.blockStartTime.set('10:00');
    component.blockEndTime.set('15:00');
    component.blockReason.set('Formation');

    component.openBlockForm();

    expect(component.blockFullDay()).toBeFalse();
    expect(component.blockStartTime()).toBe('09:00');
    expect(component.blockEndTime()).toBe('18:00');
    expect(component.blockReason()).toBe('');
  });

  it('should not create block when no date is selected', () => {
    const storeSpy = spyOn(component.store, 'createBlock');
    component.confirmBlock();
    expect(storeSpy).not.toHaveBeenCalled();
  });

  it('should create a time-range block request', () => {
    const day = component.calendarDays().find((d) => d.isCurrentMonth)!;
    component.selectDay(day);
    component.openBlockForm();
    component.blockStartTime.set('10:00');
    component.blockEndTime.set('12:00');
    component.blockReason.set('Pause');

    const storeSpy = spyOn(component.store, 'createBlock');
    component.confirmBlock();

    expect(storeSpy).toHaveBeenCalled();
    const arg = (storeSpy.calls.mostRecent().args[0] as any);
    expect(arg.fullDay).toBeFalse();
    expect(arg.startTime).toBe('10:00');
    expect(arg.endTime).toBe('12:00');
    expect(arg.reason).toBe('Pause');
  });

  it('should create a full-day block request without times', () => {
    const day = component.calendarDays().find((d) => d.isCurrentMonth)!;
    component.selectDay(day);
    component.openBlockForm();
    component.blockFullDay.set(true);

    const storeSpy = spyOn(component.store, 'createBlock');
    component.confirmBlock();

    const arg = (storeSpy.calls.mostRecent().args[0] as any);
    expect(arg.fullDay).toBeTrue();
    expect(arg.startTime).toBeUndefined();
    expect(arg.endTime).toBeUndefined();
  });

  it('should close block form after confirming', () => {
    const day = component.calendarDays().find((d) => d.isCurrentMonth)!;
    component.selectDay(day);
    component.openBlockForm();
    spyOn(component.store, 'createBlock');
    component.confirmBlock();
    expect(component.showBlockForm()).toBeFalse();
  });

  it('should omit reason when empty', () => {
    const day = component.calendarDays().find((d) => d.isCurrentMonth)!;
    component.selectDay(day);
    component.openBlockForm();
    component.blockReason.set('');

    const storeSpy = spyOn(component.store, 'createBlock');
    component.confirmBlock();

    const arg = (storeSpy.calls.mostRecent().args[0] as any);
    expect(arg.reason).toBeUndefined();
  });

  // ── Unblock ──

  it('should call store.deleteBlock with the block id', () => {
    const block: BlockedSlotResponse = {
      id: 42,
      date: '2026-04-10',
      startTime: '10:00',
      endTime: '12:00',
      fullDay: false,
      reason: 'test',
    };
    const storeSpy = spyOn(component.store, 'deleteBlock');
    component.onUnblock(block);
    expect(storeSpy).toHaveBeenCalledWith(42);
  });

  // ── Calendar day flags (closed / blocked) ──

  it('should mark days without opening hours as closed', () => {
    // By default no opening hours are loaded, so all days of current month should be closed
    const currentDays = component.calendarDays().filter((d) => d.isCurrentMonth);
    expect(currentDays.every((d) => d.isClosed)).toBeTrue();
  });
});
