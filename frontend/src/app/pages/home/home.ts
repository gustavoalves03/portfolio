import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { CaresStore } from '../../features/cares/store/cares.store';

@Component({
  selector: 'app-home',
  imports: [MatCardModule, CurrencyPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  providers: [CaresStore]
})
export class Home {
  readonly caresStore = inject(CaresStore);
}
