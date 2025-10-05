import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { CaresStore } from './store/cares.store';
import {CrudTable} from '../../shared/uis/crud-table/crud-table';
import {Care} from './models/cares.model';

@Component({
  selector: 'app-cares',
  standalone: true,
  imports: [CurrencyPipe, CrudTable],
  templateUrl: './cares.component.html',
  styleUrl: './cares.component.scss',
  providers: [CaresStore]
})
export class CaresComponent {
  readonly store = inject(CaresStore);

  displayedColumns: string[] = ['id', 'name', 'description', 'price', 'duration', 'actions'];
}
