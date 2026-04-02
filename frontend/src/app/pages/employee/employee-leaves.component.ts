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

            <div class="leaves-layout">
                <!-- LEFT: Form -->
                <div class="form-col">
                    <form class="leave-form" (ngSubmit)="onSubmit()">
                        <!-- Type chips -->
                        <div class="type-chips">
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

                        <!-- Date summary bar -->
                        @if (datesSummary()) {
                            <div class="date-bar">
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

                        <!-- Mini calendar -->
                        <div class="mini-cal">
                            <div class="mc-nav">
                                <button
                                    type="button"
                                    class="mc-nav-btn"
                                    (click)="prevMonth()"
                                >
                                    <mat-icon>chevron_left</mat-icon>
                                </button>
                                <span class="mc-month">{{ monthLabel() }}</span>
                                <button
                                    type="button"
                                    class="mc-nav-btn"
                                    (click)="nextMonth()"
                                >
                                    <mat-icon>chevron_right</mat-icon>
                                </button>
                            </div>

                            <div class="mc-grid">
                                @for (wd of weekDayLabels; track wd) {
                                    <div class="mc-wd">{{ wd }}</div>
                                }
                                @for (day of calendarDays(); track day.date.getTime()) {
                                    <button
                                        type="button"
                                        class="mc-day"
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

                        <!-- Reason textarea -->
                        <textarea
                            class="reason-area"
                            [(ngModel)]="reason"
                            name="reason"
                            rows="2"
                            [placeholder]="'employee.leaves.reason' | transloco"
                        ></textarea>

                        <!-- Submit button -->
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
                </div>

                <!-- RIGHT: Leave list -->
                <div class="list-col">
                    <!-- Segmented toggle -->
                    <div class="seg-toggle">
                        <button
                            type="button"
                            class="seg-btn"
                            [class.active]="listView() === 'current'"
                            (click)="listView.set('current')"
                        >
                            {{ 'employee.leaves.currentTab' | transloco }}
                            <span class="seg-count">{{ pendingCount() }}</span>
                        </button>
                        <button
                            type="button"
                            class="seg-btn"
                            [class.active]="listView() === 'history'"
                            (click)="listView.set('history')"
                        >
                            {{ 'employee.leaves.historyTab' | transloco }}
                            <span class="seg-count">{{ historyLeaves().length }}</span>
                        </button>
                    </div>

                    @if (loadingLeaves()) {
                        <div class="loading-wrapper">
                            <mat-spinner diameter="28"></mat-spinner>
                        </div>
                    } @else {
                        @if (listView() === 'current') {
                            @if (currentLeaves().length === 0) {
                                <div class="empty-state">
                                    <mat-icon class="empty-icon">hourglass_empty</mat-icon>
                                    <p>{{ 'employee.leaves.noCurrentLeaves' | transloco }}</p>
                                </div>
                            } @else {
                                <div class="leaves-list">
                                    @for (leave of currentLeaves(); track leave.id) {
                                        <div class="leave-item">
                                            <span
                                                class="leave-type-badge"
                                                [class.type-vacation]="leave.type === 'VACATION'"
                                                [class.type-sickness]="leave.type === 'SICKNESS'"
                                            >
                                                @if (leave.type === 'VACATION') {
                                                    {{ 'employee.leaves.vacation' | transloco }}
                                                } @else {
                                                    {{ 'employee.leaves.sickness' | transloco }}
                                                }
                                            </span>
                                            <span class="leave-dates-compact">
                                                {{ formatCompactDate(leave.startDate) }}
                                                →
                                                {{ formatCompactDate(leave.endDate) }}
                                            </span>
                                            <span class="leave-status-badge status-pending">
                                                {{ statusLabel(leave.status) | transloco }}
                                            </span>
                                        </div>
                                    }
                                </div>
                            }
                        } @else {
                            @if (historyLeaves().length === 0) {
                                <div class="empty-state">
                                    <mat-icon class="empty-icon">history</mat-icon>
                                    <p>{{ 'employee.leaves.noHistoryLeaves' | transloco }}</p>
                                </div>
                            } @else {
                                <div class="leaves-list">
                                    @for (leave of historyLeaves(); track leave.id) {
                                        <div class="leave-item-history">
                                            <div class="leave-item-row">
                                                <span
                                                    class="leave-type-badge"
                                                    [class.type-vacation]="leave.type === 'VACATION'"
                                                    [class.type-sickness]="leave.type === 'SICKNESS'"
                                                >
                                                    @if (leave.type === 'VACATION') {
                                                        {{ 'employee.leaves.vacation' | transloco }}
                                                    } @else {
                                                        {{ 'employee.leaves.sickness' | transloco }}
                                                    }
                                                </span>
                                                <span class="leave-dates-compact">
                                                    {{ formatCompactDate(leave.startDate) }}
                                                    →
                                                    {{ formatCompactDate(leave.endDate) }}
                                                </span>
                                                <span
                                                    class="leave-status-badge"
                                                    [class.status-approved]="leave.status === 'APPROVED'"
                                                    [class.status-rejected]="leave.status === 'REJECTED'"
                                                >
                                                    {{ statusLabel(leave.status) | transloco }}
                                                </span>
                                            </div>
                                            @if (leave.reviewerNote) {
                                                <p class="reviewer-note">
                                                    {{ leave.reviewerNote }}
                                                </p>
                                            }
                                        </div>
                                    }
                                </div>
                            }
                        }
                    }
                </div>
            </div>
        </div>
    `,
    styles: [
        `
            .leaves-page {
                max-width: 960px;
                margin: 0 auto;
                padding: 1.5rem;
            }

            .page-title {
                font-size: 20px;
                font-weight: 600;
                color: #333;
                margin: 0 0 1.2rem;
            }

            /* ── Side-by-side layout ── */
            .leaves-layout {
                display: grid;
                grid-template-columns: 1fr 1fr;
                gap: 24px;
                align-items: start;
            }

            .form-col {
                background: #fff;
                border-radius: 14px;
                padding: 16px;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
            }

            .list-col {
                min-height: 200px;
            }

            .leave-form {
                display: flex;
                flex-direction: column;
                gap: 0;
            }

            /* ── Type chips (compact) ── */
            .type-chips {
                display: flex;
                gap: 8px;
                margin-bottom: 12px;
            }

            .type-chip {
                display: flex;
                align-items: center;
                gap: 6px;
                padding: 8px 16px;
                border: 1.5px solid #e0e0e0;
                border-radius: 10px;
                background: #fff;
                font-family: Roboto, sans-serif;
                font-size: 12px;
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
                font-size: 16px;
                width: 16px;
                height: 16px;
            }

            /* ── Document upload (compact) ── */
            .doc-section {
                margin-bottom: 10px;
                padding: 10px 12px;
                background: #fff8e1;
                border-radius: 10px;
                border: 1px dashed #ffc107;
            }

            .doc-label {
                display: flex;
                align-items: center;
                gap: 6px;
                font-size: 12px;
                font-weight: 500;
                color: #f57f17;
                margin-bottom: 6px;
            }

            .doc-label mat-icon {
                font-size: 16px;
                width: 16px;
                height: 16px;
            }

            .upload-btn {
                display: flex;
                align-items: center;
                gap: 6px;
                padding: 6px 12px;
                border: 1px solid #ffc107;
                border-radius: 8px;
                background: #fff;
                font-size: 11px;
                color: #f57f17;
                cursor: pointer;
                font-family: Roboto, sans-serif;
            }

            .upload-btn mat-icon {
                font-size: 16px;
                width: 16px;
                height: 16px;
            }

            .file-attached {
                display: flex;
                align-items: center;
                gap: 4px;
                font-size: 11px;
                color: #4caf50;
                margin-top: 6px;
            }

            .file-attached mat-icon {
                font-size: 14px;
                width: 14px;
                height: 14px;
            }

            /* ── Date summary bar (compact) ── */
            .date-bar {
                display: flex;
                align-items: center;
                gap: 8px;
                padding: 8px 12px;
                background: #f7f5f3;
                border-radius: 8px;
                margin-bottom: 10px;
                font-size: 12px;
                color: #555;
            }

            .date-bar mat-icon {
                font-size: 16px;
                width: 16px;
                height: 16px;
                color: #c06;
            }

            .date-bar .date-value {
                font-weight: 500;
                color: #333;
            }

            .date-bar .arrow {
                color: #ccc;
            }

            .date-bar .day-count {
                margin-left: auto;
                font-size: 11px;
                color: #c06;
                font-weight: 500;
            }

            /* ── Mini calendar ── */
            .mini-cal {
                border: 1px solid #f0f0f0;
                border-radius: 10px;
                padding: 10px;
                margin-bottom: 10px;
            }

            .mc-nav {
                display: flex;
                align-items: center;
                justify-content: space-between;
                margin-bottom: 8px;
            }

            .mc-nav-btn {
                background: none;
                border: none;
                cursor: pointer;
                color: #999;
                display: flex;
                align-items: center;
                justify-content: center;
                width: 24px;
                height: 24px;
                border-radius: 50%;
            }

            .mc-nav-btn:hover {
                color: #c06;
            }

            .mc-nav-btn mat-icon {
                font-size: 18px;
                width: 18px;
                height: 18px;
            }

            .mc-month {
                font-size: 12px;
                font-weight: 500;
                color: #333;
                text-transform: capitalize;
            }

            .mc-grid {
                display: grid;
                grid-template-columns: repeat(7, 1fr);
                gap: 1px;
            }

            .mc-wd {
                text-align: center;
                font-size: 9px;
                font-weight: 600;
                color: #bbb;
                text-transform: uppercase;
                padding: 3px 0;
            }

            .mc-day {
                display: flex;
                align-items: center;
                justify-content: center;
                height: 26px;
                border: none;
                background: transparent;
                border-radius: 0;
                font-size: 11px;
                color: #333;
                cursor: pointer;
                transition: all 80ms ease;
                font-family: Roboto, sans-serif;
            }

            .mc-day:hover:not(.other):not(.disabled) {
                background: #f0e8ec;
                border-radius: 50%;
            }

            .mc-day.other {
                color: #ddd;
                cursor: default;
            }

            .mc-day.disabled {
                color: #ddd;
                cursor: not-allowed;
            }

            .mc-day.today {
                font-weight: 700;
            }

            .mc-day.range-start {
                background: #c06;
                color: #fff;
                font-weight: 600;
                border-radius: 50% 0 0 50%;
            }

            .mc-day.range-end {
                background: #c06;
                color: #fff;
                font-weight: 600;
                border-radius: 0 50% 50% 0;
            }

            .mc-day.range-start.range-end {
                border-radius: 50%;
            }

            .mc-day.in-range {
                background: #fef2f2;
                color: #a8385d;
            }

            /* ── Reason textarea (compact) ── */
            .reason-area {
                width: 100%;
                padding: 8px 12px;
                border: 1.5px solid #e0e0e0;
                border-radius: 10px;
                font-family: Roboto, sans-serif;
                font-size: 12px;
                color: #333;
                resize: none;
                min-height: 40px;
                outline: none;
                margin-bottom: 10px;
                box-sizing: border-box;
            }

            .reason-area:focus {
                border-color: #c06;
                box-shadow: 0 0 0 2px rgba(192, 0, 102, 0.08);
            }

            .form-actions {
                display: flex;
                justify-content: flex-end;
            }

            .submit-btn {
                background-color: #cc0066 !important;
                color: white !important;
                border-radius: 8px !important;
                min-width: 140px;
                font-size: 13px;
            }

            /* ── Segmented toggle ── */
            .seg-toggle {
                display: flex;
                background: #f0f0f0;
                border-radius: 10px;
                padding: 2px;
                margin-bottom: 12px;
                width: fit-content;
            }

            .seg-btn {
                padding: 6px 18px;
                border: none;
                border-radius: 8px;
                font-family: Roboto, sans-serif;
                font-size: 12px;
                font-weight: 500;
                color: #888;
                background: transparent;
                cursor: pointer;
                display: flex;
                align-items: center;
                gap: 4px;
                transition: all 150ms ease;
            }

            .seg-btn.active {
                background: #fff;
                color: #333;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
            }

            .seg-count {
                background: #c06;
                color: #fff;
                font-size: 9px;
                font-weight: 700;
                padding: 1px 5px;
                border-radius: 6px;
                min-width: 14px;
                text-align: center;
            }

            .seg-btn:not(.active) .seg-count {
                background: #ccc;
            }

            /* ── Leave items (compact rows) ── */
            .loading-wrapper {
                display: flex;
                justify-content: center;
                padding: 2rem;
            }

            .empty-state {
                display: flex;
                flex-direction: column;
                align-items: center;
                padding: 2rem;
                color: #aaa;
            }

            .empty-icon {
                font-size: 36px;
                width: 36px;
                height: 36px;
                color: #ddd;
                margin-bottom: 0.5rem;
            }

            .empty-state p {
                font-size: 13px;
                margin: 0;
            }

            .leaves-list {
                display: flex;
                flex-direction: column;
                gap: 0;
            }

            .leave-item {
                display: flex;
                align-items: center;
                gap: 10px;
                padding: 10px 14px;
                background: #fff;
                border-radius: 10px;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
                margin-bottom: 6px;
            }

            .leave-item-history {
                background: #fff;
                border-radius: 10px;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
                margin-bottom: 6px;
                padding: 10px 14px;
            }

            .leave-item-row {
                display: flex;
                align-items: center;
                gap: 10px;
            }

            .leave-type-badge {
                padding: 2px 8px;
                border-radius: 8px;
                font-size: 11px;
                font-weight: 600;
                white-space: nowrap;
            }

            .type-vacation {
                background: #e3f2fd;
                color: #1565c0;
            }

            .type-sickness {
                background: #fff3e0;
                color: #e65100;
            }

            .leave-dates-compact {
                font-size: 12px;
                color: #555;
                flex: 1;
                white-space: nowrap;
            }

            .leave-status-badge {
                padding: 2px 8px;
                border-radius: 8px;
                font-size: 10px;
                font-weight: 600;
                white-space: nowrap;
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

            .reviewer-note {
                font-size: 11px;
                color: #888;
                font-style: italic;
                margin: 4px 0 0;
                padding-left: 2px;
            }

            /* ── Mobile: stack vertically ── */
            @media (max-width: 767px) {
                .leaves-layout {
                    grid-template-columns: 1fr;
                }
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
    readonly listView = signal<'current' | 'history'>('current');

    // Calendar range picker state
    readonly calendarMonth = signal(new Date());
    readonly rangeStart = signal<Date | null>(null);
    readonly rangeEnd = signal<Date | null>(null);
    readonly selectedFile = signal<File | null>(null);

    readonly weekDayLabels = ['Lu', 'Ma', 'Me', 'Je', 'Ve', 'Sa', 'Di'];

    readonly currentLeaves = computed(() => {
        return this.leaves()
            .filter(l => l.status === 'PENDING')
            .sort((a, b) => a.startDate.localeCompare(b.startDate));
    });

    readonly historyLeaves = computed(() => {
        return this.leaves()
            .filter(l => l.status !== 'PENDING')
            .sort((a, b) => b.startDate.localeCompare(a.startDate));
    });

    readonly pendingCount = computed(() => this.currentLeaves().length);

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

    formatCompactDate(dateStr: string): string {
        const parts = dateStr.split('-');
        return `${parts[2]}/${parts[1]}`;
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
