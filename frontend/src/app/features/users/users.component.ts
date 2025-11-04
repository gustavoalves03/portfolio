import { Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { UsersStore } from './store/users.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { CreateUserComponent } from './modals/create/create-user.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CrudTable],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss',
  providers: [UsersStore],
})
export class UsersComponent {
  readonly store = inject(UsersStore);
  private dialog = inject(MatDialog);

  displayedColumns: string[] = ['id', 'name', 'email'];

  onAddUser() {
    const dialogRef = this.dialog.open(CreateUserComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.store.createUser(result);
      }
    });
  }
}
