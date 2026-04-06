export interface TimeSeriesPoint {
  date: string;
  value: number;
}

export interface EmployeeRanking {
  id: number;
  name: string;
  bookingCount: number;
  revenue: number;
  attendanceRate: number;
}

export interface ClientRanking {
  id: number;
  name: string;
  visitCount: number;
  cancelCount: number;
  noShowCount: number;
  revenue: number;
  attendanceRate: number;
}

export interface CareRanking {
  id: number;
  name: string;
  bookingCount: number;
  revenue: number;
}

export interface AtRiskClient {
  id: number;
  name: string;
  lastVisitDate: string;
  daysSinceLastVisit: number;
}

export interface AnalyticsResponse {
  totalBookings: number;
  totalRevenue: number;
  attendanceRate: number;
  occupancyRate: number;
  avgBasket: number;
  cancelledCount: number;
  noShowCount: number;
  newClientsCount: number;
  recurringClientsCount: number;
  bookingsTrend: number | null;
  revenueTrend: number | null;
  attendanceTrend: number | null;
  bookingsPerDay: TimeSeriesPoint[];
  revenuePerDay: TimeSeriesPoint[];
  heatmap: Record<string, Record<number, number>>;
  employeeRankings: EmployeeRanking[];
  clientRankings: ClientRanking[];
  careRankings: CareRanking[];
  forecastRevenue: number;
  forecastTrend: number | null;
  atRiskClients: AtRiskClient[];
  statusBreakdown: Record<string, number>;
}
