import { Component, inject } from '@angular/core';
import { UsersStore } from './store/users.store';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss',
  providers: [UsersStore],
})
export class UsersComponent {
  readonly store = inject(UsersStore);
}
