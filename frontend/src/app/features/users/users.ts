import { Component, OnInit, inject, signal } from '@angular/core';
import { AsyncPipe, NgForOf, NgIf } from '@angular/common';
import { UsersService } from './services/users.service';
import { User } from './models/users.model';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [NgIf, NgForOf, AsyncPipe],
  templateUrl: './users.html',
  styleUrl: './users.scss',
})
export class Users implements OnInit {
  private readonly service = inject(UsersService);
  readonly users = signal<User[]>([]);
  readonly loading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loading.set(true);
    this.service.list({ page: 0, size: 50 }).subscribe({
      next: (page) => {
        this.users.set(page.content);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message ?? 'Erreur de chargement des utilisateurs');
        this.loading.set(false);
      },
    });
  }
}

