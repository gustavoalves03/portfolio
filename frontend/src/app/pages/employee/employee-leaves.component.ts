import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { EmployeeLeaveService } from '../../features/employee-leaves/employee-leave.service';
import { LeaveResponse, LeaveStatus } from '../../features/leaves/leaves.model';

@Component({
    selector: 'app-employee-leaves',
    standalone: true,
    imports: [
        FormsModule,
        MatCardModule,
        MatFormFieldModule,
        MatSelectModule,
        MatInputModule,
        MatButtonModule,
        MatIconModule,
        MatProgressSpinnerModule,
        TranslocoPipe,
    ],
    template: `
        <div class="leaves-page">
            <h1 class="page-title">{{ 'employee.leaves.title' | transloco }}</h1>

            <!-- Create leave request form -->
            <mat-card class="form-card">
                <mat-card-header>
                    <mat-card-title class="form-title">
                        {{ 'employee.leaves.createTitle' | transloco }}
                    </mat-card-title>
                </mat-card-header>
                <mat-card-content>
                    <form class="leave-form" (ngSubmit)="onSubmit()">
                        <mat-form-field appearance="outline" class="field-full">
                            <mat-label>{{ 'employee.leaves.type' | transloco }}</mat-label>
                            <mat-select [(ngModel)]="leaveType" name="type" required>
                                <mat-option value="VACATION">
                                    {{ 'employee.leaves.vacation' | transloco }}
                                </mat-option>
                                <mat-option value="SICKNESS">
                                    {{ 'employee.leaves.sickness' | transloco }}
                                </mat-option>
                            </mat-select>
                        </mat-form-field>

                        <div class="date-row">
                            <mat-form-field appearance="outline" class="field-half">
                                <mat-label>{{ 'employee.leaves.startDate' | transloco }}</mat-label>
                                <input
                                    matInput
                                    type="date"
                                    [(ngModel)]="startDate"
                                    name="startDate"
                                    required
                                />
                            </mat-form-field>

                            <mat-form-field appearance="outline" class="field-half">
                                <mat-label>{{ 'employee.leaves.endDate' | transloco }}</mat-label>
                                <input
                                    matInput
                                    type="date"
                                    [(ngModel)]="endDate"
                                    name="endDate"
                                    required
                                />
                            </mat-form-field>
                        </div>

                        <mat-form-field appearance="outline" class="field-full">
                            <mat-label>{{ 'employee.leaves.reason' | transloco }}</mat-label>
                            <textarea
                                matInput
                                [(ngModel)]="reason"
                                name="reason"
                                rows="3"
                            ></textarea>
                        </mat-form-field>

                        <div class="form-actions">
                            <button
                                mat-flat-button
                                type="submit"
                                class="submit-btn"
                                [disabled]="submitting() || !leaveType || !startDate || !endDate"
                            >
                                @if (submitting()) {
                                    <mat-spinner diameter="20"></mat-spinner>
                                } @else {
                                    {{ 'employee.leaves.submit' | transloco }}
                                }
                            </button>
                        </div>
                    </form>
                </mat-card-content>
            </mat-card>

            <!-- Leave requests list -->
            <div class="leaves-list-section">
                <h2 class="section-title">{{ 'employee.leaves.myLeaves' | transloco }}</h2>

                @if (loadingLeaves()) {
                    <div class="loading-wrapper">
                        <mat-spinner diameter="32"></mat-spinner>
                    </div>
                } @else if (leaves().length === 0) {
                    <div class="empty-state">
                        <mat-icon class="empty-icon">beach_access</mat-icon>
                        <p>{{ 'employee.leaves.empty' | transloco }}</p>
                    </div>
                } @else {
                    <div class="leaves-list">
                        @for (leave of leaves(); track leave.id) {
                            <mat-card class="leave-card">
                                <mat-card-content>
                                    <div class="leave-header">
                                        <span class="leave-type-badge" [class]="'type-' + leave.type.toLowerCase()">
                                            @if (leave.type === 'VACATION') {
                                                {{ 'employee.leaves.vacation' | transloco }}
                                            } @else {
                                                {{ 'employee.leaves.sickness' | transloco }}
                                            }
                                        </span>
                                        <span class="leave-status-badge" [class]="'status-' + leave.status.toLowerCase()">
                                            {{ statusLabel(leave.status) | transloco }}
                                        </span>
                                    </div>
                                    <div class="leave-dates">
                                        <mat-icon class="date-icon">date_range</mat-icon>
                                        <span>{{ leave.startDate }}</span>
                                        <span class="date-sep">→</span>
                                        <span>{{ leave.endDate }}</span>
                                    </div>
                                    @if (leave.reason) {
                                        <p class="leave-reason">{{ leave.reason }}</p>
                                    }
                                    @if (leave.reviewerNote) {
                                        <p class="reviewer-note">
                                            <mat-icon class="note-icon">comment</mat-icon>
                                            {{ leave.reviewerNote }}
                                        </p>
                                    }
                                </mat-card-content>
                            </mat-card>
                        }
                    </div>
                }
            </div>
        </div>
    `,
    styles: [`
        .leaves-page {
            max-width: 800px;
            margin: 0 auto;
            padding: 1.5rem;
        }

        .page-title {
            font-size: 20px;
            font-weight: 600;
            color: #333;
            margin: 0 0 1.5rem;
        }

        .form-card {
            border-radius: 12px !important;
            margin-bottom: 2rem;
            box-shadow: 0 2px 8px rgba(0,0,0,0.08) !important;
        }

        .form-title {
            font-size: 16px !important;
            font-weight: 600 !important;
            color: #333 !important;
        }

        .leave-form {
            display: flex;
            flex-direction: column;
            gap: 0;
            padding-top: 0.5rem;
        }

        .field-full {
            width: 100%;
        }

        .date-row {
            display: flex;
            gap: 1rem;
        }

        .field-half {
            flex: 1;
        }

        .form-actions {
            display: flex;
            justify-content: flex-end;
            margin-top: 0.5rem;
        }

        .submit-btn {
            background-color: #cc0066 !important;
            color: white !important;
            border-radius: 8px !important;
            min-width: 160px;
        }

        .section-title {
            font-size: 16px;
            font-weight: 600;
            color: #333;
            margin: 0 0 1rem;
        }

        .loading-wrapper {
            display: flex;
            justify-content: center;
            padding: 2rem;
        }

        .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 2.5rem;
            color: #aaa;
        }

        .empty-icon {
            font-size: 40px;
            width: 40px;
            height: 40px;
            color: #ddd;
            margin-bottom: 0.5rem;
        }

        .leaves-list {
            display: flex;
            flex-direction: column;
            gap: 0.75rem;
        }

        .leave-card {
            border-radius: 10px !important;
            box-shadow: 0 1px 4px rgba(0,0,0,0.07) !important;
        }

        .leave-header {
            display: flex;
            align-items: center;
            gap: 8px;
            margin-bottom: 8px;
        }

        .leave-type-badge {
            padding: 2px 10px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
        }

        .type-vacation {
            background: #e8f5e9;
            color: #2e7d32;
        }

        .type-sickness {
            background: #fff3e0;
            color: #e65100;
        }

        .leave-status-badge {
            padding: 2px 10px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
            margin-left: auto;
        }

        .status-pending {
            background: #fff8e1;
            color: #f57f17;
        }

        .status-approved {
            background: #e8f5e9;
            color: #2e7d32;
        }

        .status-rejected {
            background: #fce4ec;
            color: #c62828;
        }

        .leave-dates {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 14px;
            color: #555;
        }

        .date-icon {
            font-size: 16px;
            width: 16px;
            height: 16px;
            color: #cc0066;
        }

        .date-sep {
            color: #aaa;
        }

        .leave-reason {
            font-size: 13px;
            color: #777;
            margin: 6px 0 0;
        }

        .reviewer-note {
            display: flex;
            align-items: flex-start;
            gap: 4px;
            font-size: 13px;
            color: #555;
            margin: 6px 0 0;
            font-style: italic;
        }

        .note-icon {
            font-size: 14px;
            width: 14px;
            height: 14px;
            margin-top: 2px;
            color: #cc0066;
        }
    `],
})
export class EmployeeLeavesComponent implements OnInit {
    private leaveService = inject(EmployeeLeaveService);
    private snackBar = inject(MatSnackBar);
    private transloco = inject(TranslocoService);

    readonly leaves = signal<LeaveResponse[]>([]);
    readonly loadingLeaves = signal(true);
    readonly submitting = signal(false);

    leaveType = '';
    startDate = '';
    endDate = '';
    reason = '';

    ngOnInit(): void {
        this.loadLeaves();
    }

    onSubmit(): void {
        if (!this.leaveType || !this.startDate || !this.endDate) return;

        this.submitting.set(true);
        const dto: { type: string; startDate: string; endDate: string; reason?: string } = {
            type: this.leaveType,
            startDate: this.startDate,
            endDate: this.endDate,
        };
        if (this.reason) {
            dto.reason = this.reason;
        }

        this.leaveService.createLeave(dto).subscribe({
            next: () => {
                this.submitting.set(false);
                this.leaveType = '';
                this.startDate = '';
                this.endDate = '';
                this.reason = '';
                this.snackBar.open(
                    this.transloco.translate('employee.leaves.success'),
                    'OK',
                    { duration: 3000 }
                );
                this.loadLeaves();
            },
            error: () => {
                this.submitting.set(false);
            },
        });
    }

    statusLabel(status: LeaveStatus): string {
        const map: Record<LeaveStatus, string> = {
            PENDING: 'employee.leaves.pending',
            APPROVED: 'employee.leaves.approved',
            REJECTED: 'employee.leaves.rejected',
        };
        return map[status] ?? status;
    }

    private loadLeaves(): void {
        this.loadingLeaves.set(true);
        this.leaveService.getMyLeaves().subscribe({
            next: (list) => {
                this.leaves.set(list as LeaveResponse[]);
                this.loadingLeaves.set(false);
            },
            error: () => {
                this.loadingLeaves.set(false);
            },
        });
    }
}
