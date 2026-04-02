import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { EmployeeLeaveService } from '../../features/employee-leaves/employee-leave.service';
import { LeaveResponse, LeaveStatus } from '../../features/leaves/leaves.model';

interface CalDay {
    date: Date;
    dayOfMonth: number;
    isCurrentMonth: boolean;
    isToday: boolean;
    isPast: boolean;
}

@Component({
    selector: 'app-employee-leaves',
    standalone: true,
    imports: [
        FormsModule,
        MatCardModule,
        MatFormFieldModule,
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
                        <!-- Type selector chips -->
                        <div class="type-selector">
                            <button
                                type="button"
                                class="type-chip"
                                [class.selected]="leaveType === 'VACATION'"
                                (click)="leaveType = 'VACATION'"
                            >
                                <mat-icon>beach_access</mat-icon>
                                {{ 'employee.leaves.vacation' | transloco }}
                            </button>
                            <button
                                type="button"
                                class="type-chip"
                                [class.selected]="leaveType === 'SICKNESS'"
                                (click)="leaveType = 'SICKNESS'"
                            >
                                <mat-icon>local_hospital</mat-icon>
                                {{ 'employee.leaves.sickness' | transloco }}
                            </button>
                        </div>

                        <!-- Document upload for sickness -->
                        @if (leaveType === 'SICKNESS') {
                            <div class="doc-section">
                                <div class="doc-label">
                                    <mat-icon>description</mat-icon>
                                    {{ 'employee.leaves.medicalCert' | transloco }}
                                </div>
                                <button
                                    type="button"
                                    class="upload-btn"
                                    (click)="fileInput.click()"
                                >
                                    <mat-icon>upload_file</mat-icon>
                                    {{ 'employee.leaves.attachDoc' | transloco }}
                                </button>
                                <input
                                    #fileInput
                                    type="file"
                                    hidden
                                    accept=".pdf,.jpg,.jpeg,.png"
                                    (change)="onFileSelected($event)"
                                />
                                @if (selectedFile()) {
                                    <div class="file-attached">
                                        <mat-icon>check_circle</mat-icon>
                                        {{ selectedFile()!.name }}
                                    </div>
                                }
                            </div>
                        }

                        <!-- Inline calendar range picker -->
                        <div class="cal-section">
                            <div class="cal-label">
                                {{ 'employee.leaves.period' | transloco }}
                            </div>

                            @if (datesSummary()) {
                                <div class="date-summary">
                                    <mat-icon>calendar_today</mat-icon>
                                    <span class="date-value">
                                        {{ formatDisplayDate(datesSummary()!.start) }}
                                    </span>
                                    <span class="arrow">→</span>
                                    <span class="date-value">
                                        {{ formatDisplayDate(datesSummary()!.end) }}
                                    </span>
                                    <span class="day-count">
                                        {{ datesSummary()!.dayCount }}
                                        {{
                                            datesSummary()!.dayCount > 1
                                                ? ('employee.leaves.days' | transloco)
                                                : ('employee.leaves.day' | transloco)
                                        }}
                                    </span>
                                </div>
                            }

                            <div class="cal-wrapper">
                                <div class="cal-nav">
                                    <button
                                        type="button"
                                        class="cal-nav-btn"
                                        (click)="prevMonth()"
                                    >
                                        <mat-icon>chevron_left</mat-icon>
                                    </button>
                                    <span class="cal-month">{{ monthLabel() }}</span>
                                    <button
                                        type="button"
                                        class="cal-nav-btn"
                                        (click)="nextMonth()"
                                    >
                                        <mat-icon>chevron_right</mat-icon>
                                    </button>
                                </div>

                                <div class="cal-grid">
                                    @for (wd of weekDayLabels; track wd) {
                                        <div class="cal-weekday">{{ wd }}</div>
                                    }
                                    @for (day of calendarDays(); track day.date.getTime()) {
                                        <button
                                            type="button"
                                            class="cal-day"
                                            [class.other]="!day.isCurrentMonth"
                                            [class.disabled]="day.isPast && day.isCurrentMonth"
                                            [class.today]="day.isToday"
                                            [class.range-start]="isRangeStart(day)"
                                            [class.range-end]="isRangeEnd(day)"
                                            [class.in-range]="isInRange(day)"
                                            (click)="onDayClick(day)"
                                        >
                                            {{ day.dayOfMonth }}
                                        </button>
                                    }
                                </div>
                            </div>
                        </div>

                        <mat-form-field appearance="outline" class="field-full reason-field">
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
                                [disabled]="
                                    submitting() || !leaveType || !startDate || !endDate
                                "
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
                <h2 class="section-title">
                    {{ 'employee.leaves.myLeaves' | transloco }}
                </h2>

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
                                        <span
                                            class="leave-type-badge"
                                            [class]="
                                                'type-' + leave.type.toLowerCase()
                                            "
                                        >
                                            @if (leave.type === 'VACATION') {
                                                {{
                                                    'employee.leaves.vacation'
                                                        | transloco
                                                }}
                                            } @else {
                                                {{
                                                    'employee.leaves.sickness'
                                                        | transloco
                                                }}
                                            }
                                        </span>
                                        <span
                                            class="leave-status-badge"
                                            [class]="
                                                'status-' +
                                                leave.status.toLowerCase()
                                            "
                                        >
                                            {{
                                                statusLabel(leave.status)
                                                    | transloco
                                            }}
                                        </span>
                                    </div>
                                    <div class="leave-dates">
                                        <mat-icon class="date-icon"
                                            >date_range</mat-icon
                                        >
                                        <span>{{ leave.startDate }}</span>
                                        <span class="date-sep">→</span>
                                        <span>{{ leave.endDate }}</span>
                                    </div>
                                    @if (leave.reason) {
                                        <p class="leave-reason">
                                            {{ leave.reason }}
                                        </p>
                                    }
                                    @if (leave.reviewerNote) {
                                        <p class="reviewer-note">
                                            <mat-icon class="note-icon"
                                                >comment</mat-icon
                                            >
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
    styles: [
        `
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
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08) !important;
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

            .reason-field {
                margin-top: 4px;
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

            /* ── Type selector chips ── */
            .type-selector {
                display: flex;
                gap: 10px;
                margin-bottom: 20px;
            }

            .type-chip {
                display: flex;
                align-items: center;
                gap: 8px;
                padding: 10px 20px;
                border: 1.5px solid #e0e0e0;
                border-radius: 12px;
                background: #fff;
                font-family: Roboto, sans-serif;
                font-size: 13px;
                font-weight: 500;
                color: #666;
                cursor: pointer;
                transition: all 150ms ease;
            }

            .type-chip:hover {
                border-color: #c06;
                color: #c06;
            }

            .type-chip.selected {
                border-color: #c06;
                background: #fef2f2;
                color: #c06;
            }

            .type-chip mat-icon {
                font-size: 18px;
                width: 18px;
                height: 18px;
            }

            /* ── Document upload for sickness ── */
            .doc-section {
                margin-bottom: 16px;
                padding: 14px;
                background: #fff8e1;
                border-radius: 12px;
                border: 1px dashed #ffc107;
            }

            .doc-label {
                display: flex;
                align-items: center;
                gap: 6px;
                font-size: 13px;
                font-weight: 500;
                color: #f57f17;
                margin-bottom: 8px;
            }

            .doc-label mat-icon {
                font-size: 18px;
                width: 18px;
                height: 18px;
            }

            .upload-btn {
                display: flex;
                align-items: center;
                gap: 6px;
                padding: 8px 16px;
                border: 1px solid #ffc107;
                border-radius: 10px;
                background: #fff;
                font-size: 12px;
                color: #f57f17;
                cursor: pointer;
                font-family: Roboto, sans-serif;
            }

            .upload-btn mat-icon {
                font-size: 18px;
                width: 18px;
                height: 18px;
            }

            .file-attached {
                display: flex;
                align-items: center;
                gap: 4px;
                font-size: 12px;
                color: #4caf50;
                margin-top: 8px;
            }

            .file-attached mat-icon {
                font-size: 16px;
                width: 16px;
                height: 16px;
            }

            /* ── Inline calendar range picker ── */
            .cal-section {
                margin-bottom: 16px;
            }

            .cal-label {
                font-size: 12px;
                font-weight: 500;
                color: #999;
                text-transform: uppercase;
                letter-spacing: 0.5px;
                margin-bottom: 8px;
            }

            .date-summary {
                display: flex;
                align-items: center;
                gap: 10px;
                padding: 10px 14px;
                background: #f7f5f3;
                border-radius: 10px;
                margin-bottom: 12px;
                font-size: 13px;
                color: #555;
            }

            .date-summary mat-icon {
                font-size: 18px;
                width: 18px;
                height: 18px;
                color: #c06;
            }

            .date-summary .date-value {
                font-weight: 500;
                color: #333;
            }

            .date-summary .arrow {
                color: #ccc;
            }

            .date-summary .day-count {
                margin-left: auto;
                font-size: 12px;
                color: #c06;
                font-weight: 500;
            }

            .cal-wrapper {
                border: 1px solid #f0f0f0;
                border-radius: 14px;
                padding: 14px;
            }

            .cal-nav {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 12px;
            }

            .cal-nav-btn {
                background: none;
                border: none;
                cursor: pointer;
                color: #999;
                display: flex;
                align-items: center;
                justify-content: center;
                width: 32px;
                height: 32px;
                border-radius: 50%;
                transition: color 150ms;
            }

            .cal-nav-btn:hover {
                color: #c06;
            }

            .cal-month {
                font-size: 14px;
                font-weight: 500;
                color: #333;
                text-transform: capitalize;
            }

            .cal-grid {
                display: grid;
                grid-template-columns: repeat(7, 1fr);
                gap: 2px;
            }

            .cal-weekday {
                text-align: center;
                font-size: 10px;
                font-weight: 600;
                color: #999;
                text-transform: uppercase;
                padding: 6px 0;
            }

            .cal-day {
                display: flex;
                align-items: center;
                justify-content: center;
                height: 34px;
                border: none;
                background: transparent;
                border-radius: 0;
                font-size: 13px;
                color: #333;
                cursor: pointer;
                transition: all 100ms;
                font-family: Roboto, sans-serif;
            }

            .cal-day:hover:not(.other):not(.disabled):not(.range-start):not(.range-end) {
                background: #f0e8ec;
                border-radius: 50%;
            }

            .cal-day.other {
                color: #ddd;
                cursor: default;
            }

            .cal-day.disabled {
                color: #ddd;
                cursor: not-allowed;
            }

            .cal-day.today {
                font-weight: 600;
            }

            .cal-day.range-start {
                background: #c06;
                color: #fff;
                font-weight: 600;
                border-radius: 50% 0 0 50%;
            }

            .cal-day.range-end {
                background: #c06;
                color: #fff;
                font-weight: 600;
                border-radius: 0 50% 50% 0;
            }

            .cal-day.range-start.range-end {
                border-radius: 50%;
            }

            .cal-day.in-range {
                background: #fef2f2;
                color: #a8385d;
            }

            /* ── Existing leave list styles ── */
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
                box-shadow: 0 1px 4px rgba(0, 0, 0, 0.07) !important;
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
        `,
    ],
})
export class EmployeeLeavesComponent implements OnInit {
    private leaveService = inject(EmployeeLeaveService);
    private snackBar = inject(MatSnackBar);
    private transloco = inject(TranslocoService);

    readonly leaves = signal<LeaveResponse[]>([]);
    readonly loadingLeaves = signal(true);
    readonly submitting = signal(false);

    // Calendar range picker state
    readonly calendarMonth = signal(new Date());
    readonly rangeStart = signal<Date | null>(null);
    readonly rangeEnd = signal<Date | null>(null);
    readonly selectedFile = signal<File | null>(null);

    readonly weekDayLabels = ['Lu', 'Ma', 'Me', 'Je', 'Ve', 'Sa', 'Di'];

    leaveType = '';
    startDate = '';
    endDate = '';
    reason = '';

    readonly monthLabel = computed(() => {
        const d = this.calendarMonth();
        return d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
    });

    readonly calendarDays = computed<CalDay[]>(() => {
        const month = this.calendarMonth();
        const year = month.getFullYear();
        const m = month.getMonth();
        const firstDay = new Date(year, m, 1);
        const lastDay = new Date(year, m + 1, 0);

        // Monday=0 adjustment (JS: Sunday=0)
        let startDow = firstDay.getDay() - 1;
        if (startDow < 0) startDow = 6;

        const today = new Date();
        today.setHours(0, 0, 0, 0);

        const days: CalDay[] = [];

        // Previous month padding
        for (let i = startDow - 1; i >= 0; i--) {
            const d = new Date(year, m, -i);
            days.push(this.buildDay(d, false, today));
        }

        // Current month
        for (let i = 1; i <= lastDay.getDate(); i++) {
            const d = new Date(year, m, i);
            days.push(this.buildDay(d, true, today));
        }

        // Next month padding (fill to 42 = 6 rows)
        const remaining = 42 - days.length;
        for (let i = 1; i <= remaining; i++) {
            const d = new Date(year, m + 1, i);
            days.push(this.buildDay(d, false, today));
        }

        return days;
    });

    readonly datesSummary = computed(() => {
        const start = this.rangeStart();
        const end = this.rangeEnd();
        if (!start) return null;
        const dayCount = end
            ? Math.round((end.getTime() - start.getTime()) / 86400000) + 1
            : 1;
        return { start, end: end ?? start, dayCount };
    });

    ngOnInit(): void {
        this.loadLeaves();
    }

    onDayClick(day: CalDay): void {
        if (day.isPast || !day.isCurrentMonth) return;
        const start = this.rangeStart();
        const end = this.rangeEnd();

        if (!start || end) {
            // Start new selection
            this.rangeStart.set(day.date);
            this.rangeEnd.set(null);
        } else {
            // Set end date
            if (day.date < start) {
                this.rangeStart.set(day.date);
                this.rangeEnd.set(start);
            } else {
                this.rangeEnd.set(day.date);
            }
        }
        // Update the string fields for form submission
        this.startDate = this.formatDate(this.rangeStart()!);
        this.endDate = this.rangeEnd()
            ? this.formatDate(this.rangeEnd()!)
            : this.startDate;
    }

    isInRange(day: CalDay): boolean {
        const start = this.rangeStart();
        const end = this.rangeEnd();
        if (!start || !end || !day.isCurrentMonth) return false;
        const t = day.date.getTime();
        return t > start.getTime() && t < end.getTime();
    }

    isRangeStart(day: CalDay): boolean {
        const start = this.rangeStart();
        if (!start || !day.isCurrentMonth) return false;
        return this.sameDay(day.date, start);
    }

    isRangeEnd(day: CalDay): boolean {
        const end = this.rangeEnd();
        if (!end || !day.isCurrentMonth) return false;
        return this.sameDay(day.date, end);
    }

    prevMonth(): void {
        const d = this.calendarMonth();
        this.calendarMonth.set(new Date(d.getFullYear(), d.getMonth() - 1, 1));
    }

    nextMonth(): void {
        const d = this.calendarMonth();
        this.calendarMonth.set(new Date(d.getFullYear(), d.getMonth() + 1, 1));
    }

    onFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files?.length) {
            this.selectedFile.set(input.files[0]);
        }
    }

    formatDisplayDate(date: Date): string {
        return date.toLocaleDateString('fr-FR', {
            day: 'numeric',
            month: 'short',
        });
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
                this.rangeStart.set(null);
                this.rangeEnd.set(null);
                this.selectedFile.set(null);
                this.snackBar.open(
                    this.transloco.translate('employee.leaves.success'),
                    'OK',
                    { duration: 3000 },
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

    private buildDay(date: Date, isCurrentMonth: boolean, today: Date): CalDay {
        return {
            date,
            dayOfMonth: date.getDate(),
            isCurrentMonth,
            isToday: date.getTime() === today.getTime(),
            isPast: date.getTime() < today.getTime(),
        };
    }

    private formatDate(d: Date): string {
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    }

    private sameDay(a: Date, b: Date): boolean {
        return (
            a.getFullYear() === b.getFullYear() &&
            a.getMonth() === b.getMonth() &&
            a.getDate() === b.getDate()
        );
    }
}
