import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { AvailabilityDayListComponent } from './availability-day-list.component';
import { WeekSlots } from '../availability.model';

const translations = {
  'pro.availability.days.1': 'Monday',
  'pro.availability.days.2': 'Tuesday',
  'pro.availability.days.3': 'Wednesday',
  'pro.availability.days.4': 'Thursday',
  'pro.availability.days.5': 'Friday',
  'pro.availability.days.6': 'Saturday',
  'pro.availability.days.7': 'Sunday',
  'pro.availability.closed': 'Closed',
  'pro.availability.daylist.addPause': 'Add pause',
};

const week: WeekSlots = [
  { dayOfWeek: 1, slots: [{ openTime: '09:00', closeTime: '18:00' }] },
  { dayOfWeek: 2, slots: [] },
  { dayOfWeek: 3, slots: [] },
  { dayOfWeek: 4, slots: [] },
  { dayOfWeek: 5, slots: [] },
  { dayOfWeek: 6, slots: [] },
  { dayOfWeek: 7, slots: [] },
];

function setup(weekSlots: WeekSlots): ComponentFixture<AvailabilityDayListComponent> {
  TestBed.configureTestingModule({
    imports: [
      AvailabilityDayListComponent,
      TranslocoTestingModule.forRoot({
        langs: { en: translations },
        translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
      }),
    ],
    providers: [provideZonelessChangeDetection()],
  });
  const fixture = TestBed.createComponent(AvailabilityDayListComponent);
  fixture.componentRef.setInput('week', weekSlots);
  fixture.detectChanges();
  return fixture;
}

describe('AvailabilityDayListComponent', () => {
  it('renders 7 day cards', () => {
    const fixture = setup(week);
    const cards = fixture.nativeElement.querySelectorAll('.day-card');
    expect(cards.length).toBe(7);
  });

  it('renders the slot summary on Monday', () => {
    const fixture = setup(week);
    const monday = fixture.nativeElement.querySelector('.day-card');
    expect(monday.textContent).toContain('09:00');
    expect(monday.textContent).toContain('18:00');
  });

  it('emits slotClick with anchor', () => {
    const fixture = setup(week);
    let event: { day: number; slotIndex: number; anchor: HTMLElement } | null = null;
    fixture.componentInstance.slotClick.subscribe((e) => (event = e as any));

    const timeBox = fixture.nativeElement.querySelector('.time-box') as HTMLElement;
    timeBox.click();
    fixture.detectChanges();

    expect(event!.day).toBe(1);
    expect(event!.slotIndex).toBe(0);
  });

  it('emits dayToggle on switch click', () => {
    const fixture = setup(week);
    let toggled: number | null = null;
    fixture.componentInstance.dayToggle.subscribe((d) => (toggled = d as any));

    const firstSwitch = fixture.nativeElement.querySelector('.switch') as HTMLElement;
    firstSwitch.click();

    expect(toggled).toBe(1 as any);
  });
});
