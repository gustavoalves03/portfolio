import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-booking-times',
  imports: [MatIconModule],
  templateUrl: './booking-times.component.html',
  standalone: true,
  styleUrl: './booking-times.component.scss'
})
export class BookingTimesComponent {
  // Inputs
  selectedTime = input<string>();
  hiddenOnMobile = input(false);
  showBackButton = input(false);

  // Outputs
  timeSelected = output<string>();
  backToCalendar = output<void>();

  // Available time slots
  timeSlots = [
    '09:00', '09:30', '10:00', '10:30', '11:00', '11:30',
    '14:00', '14:30', '15:00', '15:30', '16:00', '16:30', '17:00'
  ];

  isSelectedTime(time: string): boolean {
    return this.selectedTime() === time;
  }

  selectTime(time: string): void {
    this.timeSelected.emit(time);
  }

  goBack(): void {
    this.backToCalendar.emit();
  }
}
