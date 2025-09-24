import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { CaresStore } from './store/cares.store';

@Component({
  selector: 'app-cares',
  standalone: true,
  imports: [CurrencyPipe],
  templateUrl: './cares.component.html',
  styleUrl: './cares.component.scss',
  providers: [CaresStore]
})
export class CaresComponent {
  readonly store = inject(CaresStore);
}
