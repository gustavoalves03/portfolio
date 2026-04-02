import { Component, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { EmployeesStore } from './employees.store';
import { CaresStore } from '../cares/store/cares.store';
import { Employee } from './employees.model';
import { CreateEmployeeComponent } from './modals/create-employee/create-employee.component';
import { EmployeeDetailComponent } from './modals/employee-detail/employee-detail.component';

@Component({
  selector: 'app-employees',
  standalone: true,
  imports: [
    TranslocoPipe,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './employees.component.html',
  styleUrl: './employees.component.scss',
  providers: [EmployeesStore, CaresStore],
})
export class EmployeesComponent {
  readonly store = inject(EmployeesStore);
  readonly caresStore = inject(CaresStore);
  private readonly dialog = inject(MatDialog);

  onAdd(): void {
    const dialogRef = this.dialog.open(CreateEmployeeComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: true,
      data: { cares: this.caresStore.availableCares() },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.store.createEmployee(result);
      }
    });
  }

  onCardClick(employee: Employee): void {
    const dialogRef = this.dialog.open(EmployeeDetailComponent, {
      width: '500px',
      disableClose: false,
      autoFocus: false,
      data: { employee, cares: this.caresStore.availableCares() },
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result?.delete) {
        this.store.deleteEmployee(employee.id);
      } else if (result) {
        this.store.updateEmployee({ id: employee.id, req: result });
      }
    });
  }
}
