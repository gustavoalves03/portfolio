import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { AvailabilityTimelineComponent } from './availability-timeline.component';
import { WeekSlots } from '../availability.model';

const translations = {
  'pro.availability.days.1': 'Mon',
  'pro.availability.days.2': 'Tue',
  'pro.availability.days.3': 'Wed',
  'pro.availability.days.4': 'Thu',
  'pro.availability.days.5': 'Fri',
  'pro.availability.days.6': 'Sat',
  'pro.availability.days.7': 'Sun',
  'pro.availability.closed': 'Closed',
  'pro.availability.timeline.addSlot': 'Add slot',
};

const week: WeekSlots = [
  { dayOfWeek: 1, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 2, slots: [
    { openTime: '09:00', closeTime: '13:00' },
    { openTime: '14:00', closeTime: '18:00' },
  ] },
  { dayOfWeek: 3, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 4, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 5, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 6, slots: [] },
  { dayOfWeek: 7, slots: [] },
];

function setup(weekSlots: WeekSlots): ComponentFixture<AvailabilityTimelineComponent> {
  TestBed.configureTestingModule({
    imports: [
      AvailabilityTimelineComponent,
      TranslocoTestingModule.forRoot({
        langs: { en: translations },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      }),
    ],
    providers: [provideZonelessChangeDetection()],
  });
  const fixture = TestBed.createComponent(AvailabilityTimelineComponent);
  fixture.componentRef.setInput('week', weekSlots);
  fixture.detectChanges();
  return fixture;
}

describe('AvailabilityTimelineComponent', () => {
  it('renders 7 day rows', () => {
    const fixture = setup(week);
    const rows = fixture.nativeElement.querySelectorAll('.timeline-row');
    expect(rows.length).toBe(7);
  });

  it('renders one slot block per slot on Tuesday (2 blocks)', () => {
    const fixture = setup(week);
    const tuesdayRow = fixture.nativeElement.querySelectorAll('.timeline-row')[1];
    const blocks = tuesdayRow.querySelectorAll('.slot-block');
    expect(blocks.length).toBe(2);
  });

  it('renders the closed band on Saturday and Sunday', () => {
    const fixture = setup(week);
    const rows = fixture.nativeElement.querySelectorAll('.timeline-row');
    expect(rows[5].querySelector('.closed-block')).toBeTruthy();
    expect(rows[6].querySelector('.closed-block')).toBeTruthy();
  });

  it('emits slotClick with day, index, and anchor', () => {
    const fixture = setup(week);
    let event: { day: number; slotIndex: number; anchor: HTMLElement } | null = null;
    fixture.componentInstance.slotClick.subscribe((e) => (event = e));

    const firstBlock = fixture.nativeElement.querySelector('.slot-block') as HTMLElement;
    firstBlock.click();
    fixture.detectChanges();

    expect(event).toBeTruthy();
    expect(event!.day).toBe(1);
    expect(event!.slotIndex).toBe(0);
    expect(event!.anchor).toBe(firstBlock);
  });

  it('emits dayToggle on switch click', () => {
    const fixture = setup(week);
    let toggled: number | null = null;
    fixture.componentInstance.dayToggle.subscribe((d) => (toggled = d));

    const firstSwitch = fixture.nativeElement.querySelector('.switch') as HTMLElement;
    firstSwitch.click();
    fixture.detectChanges();

    expect(toggled as unknown as number).toBe(1);
  });

  it('emits addSlotClick on + button', () => {
    const fixture = setup(week);
    let event: { day: number; anchor: HTMLElement } | null = null;
    fixture.componentInstance.addSlotClick.subscribe((e) => (event = e));

    const firstAddBtn = fixture.nativeElement.querySelector('.add-slot-btn') as HTMLElement;
    firstAddBtn.click();
    fixture.detectChanges();

    expect(event).toBeTruthy();
    expect(event!.day).toBe(1);
  });
});
