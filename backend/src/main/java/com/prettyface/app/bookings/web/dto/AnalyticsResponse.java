package com.prettyface.app.bookings.web.dto;

import java.util.List;
import java.util.Map;

public record AnalyticsResponse(
    // KPIs
    int totalBookings,
    int totalRevenue,        // in cents
    double attendanceRate,   // 0.0-1.0
    double occupancyRate,    // 0.0-1.0
    int avgBasket,           // in cents
    int cancelledCount,
    int noShowCount,
    int newClientsCount,
    int recurringClientsCount,

    // Comparison vs previous period
    Double bookingsTrend,    // % change
    Double revenueTrend,
    Double attendanceTrend,

    // Series data
    List<DayData> bookingsPerDay,
    List<DayData> revenuePerDay,

    // Heatmap: hour -> day -> count (e.g., "09" -> { "1": 5, "2": 3 })
    Map<String, Map<Integer, Integer>> heatmap,

    // Rankings
    List<EmployeeRanking> employeeRankings,
    List<ClientRanking> clientRankings,
    List<CareRanking> careRankings,

    // Forecast
    int forecastRevenue,      // in cents, based on future confirmed bookings
    Double forecastTrend,

    // At-risk clients
    List<AtRiskClient> atRiskClients,

    // Status breakdown
    Map<String, Integer> statusBreakdown  // CONFIRMED: 12, CANCELLED: 3, NO_SHOW: 1
) {
    public record DayData(String date, int value) {}
    public record EmployeeRanking(Long id, String name, int bookingCount, int revenue, double attendanceRate) {}
    public record ClientRanking(Long id, String name, int visitCount, int cancelCount, int noShowCount, int revenue, double attendanceRate) {}
    public record CareRanking(Long id, String name, int bookingCount, int revenue) {}
    public record AtRiskClient(Long id, String name, String lastVisitDate, int daysSinceLastVisit) {}
}
