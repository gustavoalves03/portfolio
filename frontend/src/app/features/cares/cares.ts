import { Component, OnInit, inject, signal } from '@angular/core';
import { AsyncPipe, CurrencyPipe, NgForOf, NgIf } from '@angular/common';
import { CaresService } from './services/cares.service';
import { Care } from './models/cares.model';

@Component({
  selector: 'app-cares',
  imports: [NgIf, NgForOf, AsyncPipe, CurrencyPipe],
  templateUrl: './cares.html',
  styleUrl: './cares.scss'
})
export class Cares implements OnInit {
  private readonly service = inject(CaresService);
  readonly cares = signal<Care[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.service.list({ page: 0, size: 20 }).subscribe({
      next: (page) => {
        this.cares.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Erreur de chargement');
        this.loading.set(false);
      },
    });
  }
}
