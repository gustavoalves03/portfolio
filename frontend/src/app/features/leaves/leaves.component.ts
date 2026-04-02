import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslocoPipe } from '@jsverse/transloco';
import { LeavesStore } from './leaves.store';
import { LeaveResponse, LeaveStatus, LeaveType } from './leaves.model';
import {
  ReviewLeaveDialogComponent,
  ReviewLeaveDialogData,
  ReviewLeaveDialogResult,
} from './modals/review-leave-dialog/review-leave-dialog.component';

@Component({
  selector: 'app-leaves',
  standalone: true,
  imports: [
    DatePipe,
    TranslocoPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTabsModule,
  ],
  templateUrl: './leaves.component.html',
  styleUrl: './leaves.component.scss',
  providers: [LeavesStore],
})
export class LeavesComponent {
  readonly store = inject(LeavesStore);
  private readonly dialog = inject(MatDialog);

  readonly historyTypeFilter = signal<LeaveType | undefined>(undefined);
  readonly historyStatusFilter = signal<LeaveStatus | undefined>(undefined);
  private historyLoaded = false;

  readonly filteredHistory = computed(() => {
    const leaves = this.store.historyLeaves();
    const statusFilter = this.historyStatusFilter();
    if (!statusFilter) return leaves;
    return leaves.filter((l) => l.status === statusFilter);
  });

  getTypeKey(leave: LeaveResponse): string {
    return leave.type === 'VACATION' ? 'pro.leaves.vacation' : 'pro.leaves.sickness';
  }

  getStatusKey(leave: LeaveResponse): string {
    switch (leave.status) {
      case 'APPROVED':
        return 'pro.leaves.approved';
      case 'REJECTED':
        return 'pro.leaves.rejected';
      default:
        return 'pro.leaves.pending';
    }
  }

  onTabChange(index: number): void {
    if (index === 1 && !this.historyLoaded) {
      this.historyLoaded = true;
      this.store.loadHistory(this.historyTypeFilter());
    }
  }

  setTypeFilter(type: LeaveType | undefined): void {
    this.historyTypeFilter.set(type);
    this.store.loadHistory(type);
  }

  setStatusFilter(status: LeaveStatus | undefined): void {
    this.historyStatusFilter.set(status);
  }

  onApprove(leave: LeaveResponse): void {
    this.openReviewDialog(leave, 'APPROVED');
  }

  onReject(leave: LeaveResponse): void {
    this.openReviewDialog(leave, 'REJECTED');
  }

  private openReviewDialog(leave: LeaveResponse, action: 'APPROVED' | 'REJECTED'): void {
    const data: ReviewLeaveDialogData = {
      action,
      employeeName: leave.employeeName,
    };

    const dialogRef = this.dialog.open(ReviewLeaveDialogComponent, {
      width: '420px',
      data,
    });

    dialogRef.afterClosed().subscribe((result: ReviewLeaveDialogResult | undefined) => {
      if (result) {
        this.store.reviewLeave({
          leaveId: leave.id,
          dto: { status: result.status, reviewerNote: result.reviewerNote },
        });
      }
    });
  }
}
