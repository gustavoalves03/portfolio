import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { SlotPopoverComponent, SlotPopoverData, SlotPopoverResult } from './slot-popover.component';

const translations = {
  'pro.availability.popover.title.create': 'New slot · {{day}}',
  'pro.availability.popover.title.edit': 'Edit · {{day}}',
  'pro.availability.popover.startLabel': 'Start',
  'pro.availability.popover.endLabel': 'End',
  'pro.availability.popover.copyTo': 'Copy to:',
  'pro.availability.popover.copyAll': 'All week',
  'pro.availability.popover.delete': 'Delete',
  'pro.availability.popover.cancel': 'Cancel',
  'pro.availability.popover.confirm': 'Confirm',
  'pro.availability.invalidOverlap': 'This slot overlaps another',
  'pro.availability.days.1': 'Monday',
  'pro.availability.days.2': 'Tuesday',
  'pro.availability.days.3': 'Wednesday',
  'pro.availability.days.4': 'Thursday',
  'pro.availability.days.5': 'Friday',
  'pro.availability.days.6': 'Saturday',
  'pro.availability.days.7': 'Sunday',
};

function setup(data: SlotPopoverData): ComponentFixture<SlotPopoverComponent> {
  TestBed.configureTestingModule({
    imports: [
      SlotPopoverComponent,
      TranslocoTestingModule.forRoot({
        langs: { en: translations },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      }),
    ],
    providers: [provideZonelessChangeDetection()],
  });
  const fixture = TestBed.createComponent(SlotPopoverComponent);
  fixture.componentRef.setInput('data', data);
  fixture.detectChanges();
  return fixture;
}

describe('SlotPopoverComponent', () => {
  it('pre-fills selects from data in create mode', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 2,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [1, 3, 4, 5, 6, 7],
      existingSlotsForDay: [],
    });
    const cmp = fixture.componentInstance;
    expect(cmp.start()).toBe('09:00');
    expect(cmp.end()).toBe('12:00');
    expect(cmp.canConfirm()).toBe(true);
  });

  it('disables confirm when start >= end', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 1,
      initialStart: '12:00',
      initialEnd: '09:00',
      otherDays: [],
      existingSlotsForDay: [],
    });
    expect(fixture.componentInstance.canConfirm()).toBe(false);
  });

  it('detects overlap with existing slots', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 1,
      initialStart: '11:00',
      initialEnd: '14:00',
      otherDays: [],
      existingSlotsForDay: [{ openTime: '09:00', closeTime: '12:00' }],
    });
    expect(fixture.componentInstance.hasOverlap()).toBe(true);
    expect(fixture.componentInstance.canConfirm()).toBe(false);
  });

  it('emits confirm with the selected times', () => {
    const fixture = setup({
      mode: 'create',
      dayOfWeek: 1,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [],
      existingSlotsForDay: [],
    });
    let result: SlotPopoverResult | undefined;
    fixture.componentInstance.confirm.subscribe((r) => (result = r));
    fixture.componentInstance.onConfirm();
    expect(result).toEqual({
      action: 'save',
      start: '09:00',
      end: '12:00',
      copyToDays: [],
    });
  });

  it('toggles a day in copyToDays', () => {
    const fixture = setup({
      mode: 'edit',
      dayOfWeek: 1,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [2, 3, 4, 5],
      existingSlotsForDay: [],
    });
    const cmp = fixture.componentInstance;
    cmp.toggleCopyDay(2);
    cmp.toggleCopyDay(3);
    expect(cmp.copyToDays()).toEqual([2, 3]);
    cmp.toggleCopyDay(2);
    expect(cmp.copyToDays()).toEqual([3]);
  });

  it('emits delete in edit mode only', () => {
    const fixture = setup({
      mode: 'edit',
      dayOfWeek: 1,
      initialStart: '09:00',
      initialEnd: '12:00',
      otherDays: [],
      existingSlotsForDay: [],
    });
    let deleted = false;
    fixture.componentInstance.confirm.subscribe((r) => {
      if (r.action === 'delete') deleted = true;
    });
    fixture.componentInstance.onDelete();
    expect(deleted).toBe(true);
  });
});
