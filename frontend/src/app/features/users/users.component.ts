import { Component, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { UsersStore } from './store/users.store';
import { CrudTable } from '../../shared/uis/crud-table/crud-table';
import { TableColumn, TableAction } from '../../shared/uis/crud-table/crud-table.models';
import { TranslocoPipe } from '@jsverse/transloco';
import { CreateUserComponent } from './modals/create/create-user.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [CrudTable, TranslocoPipe, MatSnackBarModule],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss',
  providers: [UsersStore],
})
export class UsersComponent {
  readonly store = inject(UsersStore);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);

  // Configuration des colonnes avec traduction
  readonly columns = signal<TableColumn[]>([
    { key: 'id', headerKey: 'users.columns.id', align: 'center' },
    { key: 'name', headerKey: 'users.columns.name', align: 'left' },
    { key: 'email', headerKey: 'users.columns.email', align: 'left' }
  ]);

  // Configuration des actions
  readonly actions = signal<TableAction[]>([
    {
      icon: 'edit',
      tooltipKey: 'actions.edit',
      color: 'primary',
      callback: (user: any) => this.onEditUser(user)
    },
    {
      icon: 'delete',
      tooltipKey: 'actions.delete',
      color: 'warn',
      callback: (user: any) => this.onDeleteUser(user)
    }
  ]);

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

  onEditUser(user: any) {
    console.log('Edit user:', user);
    this.snackBar.open(`Édition de ${user.name}`, 'OK', { duration: 2000 });
  }

  onDeleteUser(user: any) {
    if (confirm(`Êtes-vous sûr de vouloir supprimer "${user.name}" ?`)) {
      this.store.deleteUser(user.id);
    }
  }
}
