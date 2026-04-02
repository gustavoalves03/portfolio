import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslocoPipe } from '@jsverse/transloco';
import { LeavesStore } from './leaves.store';
import { LeaveResponse } from './leaves.model';

@Component({
  selector: 'app-leaves',
  standalone: true,
  imports: [
    DatePipe,
    TranslocoPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './leaves.component.html',
  styleUrl: './leaves.component.scss',
  providers: [LeavesStore],
})
export class LeavesComponent {
  readonly store = inject(LeavesStore);

  getTypeKey(leave: LeaveResponse): string {
    return leave.type === 'VACATION' ? 'pro.leaves.vacation' : 'pro.leaves.sickness';
  }

  onApprove(leave: LeaveResponse): void {
    this.store.reviewLeave({
      leaveId: leave.id,
      dto: { status: 'APPROVED' },
    });
  }

  onReject(leave: LeaveResponse): void {
    this.store.reviewLeave({
      leaveId: leave.id,
      dto: { status: 'REJECTED' },
    });
  }
}
