import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
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
  imports: [DatePipe, TranslocoPipe, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './leaves.component.html',
  styleUrl: './leaves.component.scss',
  providers: [LeavesStore],
})
export class LeavesComponent {
  readonly store = inject(LeavesStore);
  private readonly dialog = inject(MatDialog);

  readonly activeView = signal<'pending' | 'history'>('pending');
  readonly typeFilter = signal<LeaveType | undefined>(undefined);
  readonly statusFilter = signal<LeaveStatus | undefined>(undefined);

  readonly filteredHistory = computed(() => {
    let leaves = this.store.historyLeaves();
    const status = this.statusFilter();
    if (status) {
      leaves = leaves.filter((l) => l.status === status);
    }
    return leaves;
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

  getDayCount(leave: LeaveResponse): number {
    const start = new Date(leave.startDate);
    const end = new Date(leave.endDate);
    const diff = Math.round((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
    return Math.max(diff, 1);
  }

  switchView(view: 'pending' | 'history'): void {
    this.activeView.set(view);
    if (view === 'history') {
      this.store.loadHistory(this.typeFilter());
    }
  }

  setTypeFilter(type: LeaveType | undefined): void {
    this.typeFilter.set(type);
    this.store.loadHistory(type);
  }

  setStatusFilter(status: LeaveStatus | undefined): void {
    this.statusFilter.set(status);
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
