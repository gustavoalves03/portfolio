import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BookingCardComponent } from './booking-card.component';
import { CareBookingDetailed, CareBookingStatus } from '../../models/bookings.model';

function makeBooking(overrides: Partial<CareBookingDetailed> = {}): CareBookingDetailed {
  return {
    id: 1,
    user: { id: 42, name: 'Marie D.', email: 'm@x.fr' } as any,
    care: { id: 10, name: 'Soin visage', duration: 45, price: 4500 } as any,
    quantity: 1,
    appointmentDate: '2026-04-17',
    appointmentTime: '14:30:00',
    status: CareBookingStatus.CONFIRMED,
    createdAt: '2026-04-10T10:00:00',
    employeeId: 5,
    employeeName: 'Sophie',
    salonClientId: null,
    salonClientName: null,
    ...overrides,
  };
}

describe('BookingCardComponent', () => {
  let fixture: ComponentFixture<BookingCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        BookingCardComponent,
        TranslocoTestingModule.forRoot({
          langs: { fr: {} },
          translocoConfig: { defaultLang: 'fr' },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(BookingCardComponent);
  });

  it('renders appointment time, care name, and client name', () => {
    fixture.componentRef.setInput('booking', makeBooking());
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.time')?.textContent?.trim()).toBe('14:30');
    expect(host.querySelector('.care')?.textContent?.trim()).toBe('Soin visage');
    expect(host.querySelector('.client')?.textContent?.trim()).toBe('Marie D.');
  });

  it('shows salonClientName over user.name when present', () => {
    fixture.componentRef.setInput('booking', makeBooking({
      salonClientName: 'Julie R.',
      salonClientId: 99,
    }));
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('.client')?.textContent?.trim()).toBe('Julie R.');
  });

  it('emits cardClick with the booking on host click', () => {
    const booking = makeBooking();
    fixture.componentRef.setInput('booking', booking);
    const emitted: CareBookingDetailed[] = [];
    fixture.componentInstance.cardClick.subscribe((b) => emitted.push(b));
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('.card') as HTMLElement;
    card.click();
    expect(emitted.length).toBe(1);
    expect(emitted[0].id).toBe(1);
  });

  it('applies the correct status class for each status', () => {
    const cases: Array<[CareBookingStatus, string]> = [
      [CareBookingStatus.CONFIRMED, 'ok'],
      [CareBookingStatus.PENDING, 'pending'],
      [CareBookingStatus.CANCELLED, 'cancelled'],
      [CareBookingStatus.NO_SHOW, 'noshow'],
    ];
    for (const [status, className] of cases) {
      fixture.componentRef.setInput('booking', makeBooking({ status }));
      fixture.detectChanges();
      const host = fixture.nativeElement as HTMLElement;
      const pill = host.querySelector('.status') as HTMLElement;
      expect(pill.classList.contains(className)).toBeTrue();
    }
  });
});
