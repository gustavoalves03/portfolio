export type LeaveType = 'VACATION' | 'SICKNESS';
export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface LeaveResponse {
  id: number;
  employeeId: number;
  employeeName: string;
  type: LeaveType;
  status: LeaveStatus;
  startDate: string;
  endDate: string;
  reason: string | null;
  hasDocument: boolean;
  reviewerNote: string | null;
  createdAt: string;
  reviewedAt: string | null;
}

export interface LeaveReviewDto {
  status: LeaveStatus;
  reviewerNote?: string;
}
