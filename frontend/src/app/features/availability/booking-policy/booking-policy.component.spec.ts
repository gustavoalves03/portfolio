import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { BookingPolicyComponent } from './booking-policy.component';
import { BookingPolicyStore } from './booking-policy.store';

describe('BookingPolicyComponent', () => {
  let fixture: ComponentFixture<BookingPolicyComponent>;
  let storeSpy: any;

  beforeEach(async () => {
    storeSpy = {
      policy: signal({
        maxBookingsPerDayPerClient: 1,
        maxBookingsPerWeekForNewClient: 1,
        updatedAt: '2026-05-11T10:00:00',
      }),
      isPending: signal(false),
      isFulfilled: signal(true),
      error: signal(null),
      load: jasmine.createSpy('load'),
      update: jasmine.createSpy('update'),
    };

    await TestBed.configureTestingModule({
      imports: [
        BookingPolicyComponent,
        TranslocoTestingModule.forRoot({
          langs: { en: {}, fr: {} },
          translocoConfig: { defaultLang: 'fr', availableLangs: ['fr', 'en'] },
        }),
      ],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: BookingPolicyStore, useValue: storeSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookingPolicyComponent);
    fixture.detectChanges();
  });

  it('initializes form from store policy', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]');
    expect((inputs[0] as HTMLInputElement).value).toBe('1');
    expect((inputs[1] as HTMLInputElement).value).toBe('1');
  });

  it('save button calls store.update with the form values', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]') as NodeListOf<HTMLInputElement>;
    inputs[0].value = '3';
    inputs[0].dispatchEvent(new Event('input'));
    inputs[1].value = '2';
    inputs[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button.save-btn');
    button.click();

    expect(storeSpy.update).toHaveBeenCalledWith({
      maxBookingsPerDayPerClient: 3,
      maxBookingsPerWeekForNewClient: 2,
    });
  });

  it('disables save button when value is below 1', () => {
    const inputs = fixture.nativeElement.querySelectorAll('input[type="number"]') as NodeListOf<HTMLInputElement>;
    inputs[0].value = '0';
    inputs[0].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button.save-btn');
    expect(button.disabled).toBeTrue();
  });

  it('disables save button while pending', () => {
    storeSpy.isPending.set(true);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button.save-btn');
    expect(button.disabled).toBeTrue();
  });
});
