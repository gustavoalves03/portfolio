package com.prettyface.app.bookings.app;

import com.prettyface.app.availability.domain.OpeningHour;
import com.prettyface.app.availability.repo.OpeningHourRepository;
import com.prettyface.app.bookings.domain.CareBooking;
import com.prettyface.app.bookings.domain.CareBookingStatus;
import com.prettyface.app.bookings.repo.CareBookingRepository;
import com.prettyface.app.bookings.web.dto.AnalyticsResponse;
import com.prettyface.app.bookings.web.dto.AnalyticsResponse.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final int AT_RISK_THRESHOLD_DAYS = 60;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CareBookingRepository bookingRepo;
    private final OpeningHourRepository openingHourRepo;

    public AnalyticsService(CareBookingRepository bookingRepo,
                            OpeningHourRepository openingHourRepo) {
        this.bookingRepo = bookingRepo;
        this.openingHourRepo = openingHourRepo;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse compute(String period, Long employeeId, Long careId) {
        LocalDate today = LocalDate.now();
        LocalDate[] currentRange = periodRange(period, today);
        LocalDate startDate = currentRange[0];
        LocalDate endDate = currentRange[1];

        long durationDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate prevStart = startDate.minusDays(durationDays);
        LocalDate prevEnd = startDate.minusDays(1);

        // Fetch all bookings for current and previous period in two queries
        List<CareBooking> currentAll = bookingRepo.findByAppointmentDateBetween(startDate, endDate);
        List<CareBooking> previousAll = bookingRepo.findByAppointmentDateBetween(prevStart, prevEnd);

        // Apply optional filters
        List<CareBooking> current = applyFilters(currentAll, employeeId, careId);
        List<CareBooking> previous = applyFilters(previousAll, employeeId, careId);

        // --- KPIs ---
        int totalBookings = current.size();
        int totalRevenue = sumRevenue(current);
        int cancelledCount = countByStatus(current, CareBookingStatus.CANCELLED);
        int noShowCount = countByStatus(current, CareBookingStatus.NO_SHOW);
        int attendedCount = countByStatus(current, CareBookingStatus.CONFIRMED)
                + countByStatus(current, CareBookingStatus.PENDING);
        double attendanceRate = totalBookings > 0
                ? (double) attendedCount / totalBookings
                : 0.0;
        int avgBasket = attendedCount > 0 ? sumRevenue(current, true) / attendedCount : 0;

        // Occupancy rate
        double occupancyRate = computeOccupancyRate(current, startDate, endDate);

        // Client metrics
        Set<Long> currentClientIds = current.stream()
                .map(b -> b.getUser().getId())
                .collect(Collectors.toSet());
        Set<Long> previousClientIds = previous.stream()
                .map(b -> b.getUser().getId())
                .collect(Collectors.toSet());
        int newClientsCount = 0;
        int recurringClientsCount = 0;
        for (Long clientId : currentClientIds) {
            if (previousClientIds.contains(clientId)) {
                recurringClientsCount++;
            } else {
                newClientsCount++;
            }
        }

        // --- Trends ---
        int prevTotalBookings = previous.size();
        int prevTotalRevenue = sumRevenue(previous);
        int prevAttendedCount = countByStatus(previous, CareBookingStatus.CONFIRMED)
                + countByStatus(previous, CareBookingStatus.PENDING);
        double prevAttendanceRate = prevTotalBookings > 0
                ? (double) prevAttendedCount / prevTotalBookings
                : 0.0;

        Double bookingsTrend = trend(totalBookings, prevTotalBookings);
        Double revenueTrend = trend(totalRevenue, prevTotalRevenue);
        Double attendanceTrend = trendRate(attendanceRate, prevAttendanceRate);

        // --- Series data ---
        List<DayData> bookingsPerDay = buildDaySeries(current, startDate, endDate, false);
        List<DayData> revenuePerDay = buildDaySeries(current, startDate, endDate, true);

        // --- Heatmap ---
        Map<String, Map<Integer, Integer>> heatmap = buildHeatmap(current);

        // --- Rankings ---
        List<EmployeeRanking> employeeRankings = buildEmployeeRankings(current);
        List<ClientRanking> clientRankings = buildClientRankings(current);
        List<CareRanking> careRankings = buildCareRankings(current);

        // --- Forecast ---
        List<CareBooking> futureConfirmed = bookingRepo
                .findByAppointmentDateGreaterThanEqualAndStatus(today, CareBookingStatus.CONFIRMED);
        int forecastRevenue = futureConfirmed.stream()
                .mapToInt(b -> b.getCare().getPrice() * b.getQuantity())
                .sum();
        // Compare forecast to current period revenue for trend
        Double forecastTrend = totalRevenue > 0
                ? ((double) forecastRevenue - totalRevenue) / totalRevenue * 100.0
                : null;

        // --- At-risk clients ---
        List<AtRiskClient> atRiskClients = findAtRiskClients(today);

        // --- Status breakdown ---
        Map<String, Integer> statusBreakdown = current.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getStatus().name(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        return new AnalyticsResponse(
                totalBookings, totalRevenue, attendanceRate, occupancyRate,
                avgBasket, cancelledCount, noShowCount,
                newClientsCount, recurringClientsCount,
                bookingsTrend, revenueTrend, attendanceTrend,
                bookingsPerDay, revenuePerDay,
                heatmap,
                employeeRankings, clientRankings, careRankings,
                forecastRevenue, forecastTrend,
                atRiskClients,
                statusBreakdown
        );
    }

    // --- Period calculation ---

    private LocalDate[] periodRange(String period, LocalDate today) {
        return switch (period.toLowerCase()) {
            case "week" -> new LocalDate[]{
                    today.with(DayOfWeek.MONDAY),
                    today.with(DayOfWeek.SUNDAY)
            };
            case "month" -> new LocalDate[]{
                    today.withDayOfMonth(1),
                    today.withDayOfMonth(today.lengthOfMonth())
            };
            case "quarter" -> {
                int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate qStart = today.withMonth(quarterMonth).withDayOfMonth(1);
                LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
                yield new LocalDate[]{qStart, qEnd};
            }
            case "year" -> new LocalDate[]{
                    today.withDayOfYear(1),
                    today.withMonth(12).withDayOfMonth(31)
            };
            default -> new LocalDate[]{
                    today.with(DayOfWeek.MONDAY),
                    today.with(DayOfWeek.SUNDAY)
            };
        };
    }

    // --- Filters ---

    private List<CareBooking> applyFilters(List<CareBooking> bookings, Long employeeId, Long careId) {
        return bookings.stream()
                .filter(b -> employeeId == null || employeeId.equals(b.getEmployeeId()))
                .filter(b -> careId == null || careId.equals(b.getCare().getId()))
                .toList();
    }

    // --- Revenue helpers ---

    private int sumRevenue(List<CareBooking> bookings) {
        return bookings.stream()
                .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                        && b.getStatus() != CareBookingStatus.NO_SHOW)
                .mapToInt(b -> b.getCare().getPrice() * b.getQuantity())
                .sum();
    }

    private int sumRevenue(List<CareBooking> bookings, boolean attendedOnly) {
        return bookings.stream()
                .filter(b -> !attendedOnly || (b.getStatus() != CareBookingStatus.CANCELLED
                        && b.getStatus() != CareBookingStatus.NO_SHOW))
                .mapToInt(b -> b.getCare().getPrice() * b.getQuantity())
                .sum();
    }

    // --- Count helpers ---

    private int countByStatus(List<CareBooking> bookings, CareBookingStatus status) {
        return (int) bookings.stream().filter(b -> b.getStatus() == status).count();
    }

    // --- Occupancy rate ---

    private double computeOccupancyRate(List<CareBooking> bookings, LocalDate start, LocalDate end) {
        List<OpeningHour> openingHours = openingHourRepo.findByEmployeeIdIsNullOrderByDayOfWeekAscOpenTimeAsc();
        if (openingHours.isEmpty()) {
            return 0.0;
        }

        // Build a map: dayOfWeek -> total open minutes
        Map<Integer, Long> minutesPerDay = new HashMap<>();
        for (OpeningHour oh : openingHours) {
            long mins = java.time.Duration.between(oh.getOpenTime(), oh.getCloseTime()).toMinutes();
            minutesPerDay.merge(oh.getDayOfWeek(), mins, Long::sum);
        }

        // Sum available minutes across all days in period
        long totalAvailable = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            int dow = cursor.getDayOfWeek().getValue(); // 1=Monday
            totalAvailable += minutesPerDay.getOrDefault(dow, 0L);
            cursor = cursor.plusDays(1);
        }

        if (totalAvailable == 0) {
            return 0.0;
        }

        // Sum booked minutes (only non-cancelled, non-no-show)
        long totalBooked = bookings.stream()
                .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                        && b.getStatus() != CareBookingStatus.NO_SHOW)
                .mapToLong(b -> b.getCare().getDuration())
                .sum();

        return Math.min(1.0, (double) totalBooked / totalAvailable);
    }

    // --- Trends ---

    private Double trend(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : null;
        }
        return ((double) current - previous) / previous * 100.0;
    }

    private Double trendRate(double current, double previous) {
        if (previous == 0.0) {
            return current > 0.0 ? 100.0 : null;
        }
        return ((current - previous) / previous) * 100.0;
    }

    // --- Day series ---

    private List<DayData> buildDaySeries(List<CareBooking> bookings, LocalDate start, LocalDate end,
                                          boolean revenue) {
        // Group bookings by date
        Map<LocalDate, List<CareBooking>> byDate = bookings.stream()
                .collect(Collectors.groupingBy(CareBooking::getAppointmentDate));

        List<DayData> series = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            List<CareBooking> dayBookings = byDate.getOrDefault(cursor, List.of());
            int value;
            if (revenue) {
                value = dayBookings.stream()
                        .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                                && b.getStatus() != CareBookingStatus.NO_SHOW)
                        .mapToInt(b -> b.getCare().getPrice() * b.getQuantity())
                        .sum();
            } else {
                value = dayBookings.size();
            }
            series.add(new DayData(cursor.format(DAY_FORMAT), value));
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    // --- Heatmap ---

    private Map<String, Map<Integer, Integer>> buildHeatmap(List<CareBooking> bookings) {
        Map<String, Map<Integer, Integer>> heatmap = new TreeMap<>();
        for (CareBooking b : bookings) {
            String hour = String.format("%02d", b.getAppointmentTime().getHour());
            int dayOfWeek = b.getAppointmentDate().getDayOfWeek().getValue();
            heatmap.computeIfAbsent(hour, k -> new HashMap<>())
                    .merge(dayOfWeek, 1, Integer::sum);
        }
        return heatmap;
    }

    // --- Rankings ---

    private List<EmployeeRanking> buildEmployeeRankings(List<CareBooking> bookings) {
        // Group by employeeId (skip unassigned)
        Map<Long, List<CareBooking>> byEmployee = bookings.stream()
                .filter(b -> b.getEmployeeId() != null)
                .collect(Collectors.groupingBy(CareBooking::getEmployeeId));

        return byEmployee.entrySet().stream()
                .map(e -> {
                    Long id = e.getKey();
                    List<CareBooking> empBookings = e.getValue();
                    int count = empBookings.size();
                    int rev = empBookings.stream()
                            .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                                    && b.getStatus() != CareBookingStatus.NO_SHOW)
                            .mapToInt(b -> b.getCare().getPrice() * b.getQuantity())
                            .sum();
                    long attended = empBookings.stream()
                            .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                                    && b.getStatus() != CareBookingStatus.NO_SHOW)
                            .count();
                    double rate = count > 0 ? (double) attended / count : 0.0;
                    // Employee name not stored on booking — use ID as label
                    return new EmployeeRanking(id, "Employee #" + id, count, rev, rate);
                })
                .sorted(Comparator.comparingInt(EmployeeRanking::revenue).reversed())
                .toList();
    }

    private List<ClientRanking> buildClientRankings(List<CareBooking> bookings) {
        Map<Long, List<CareBooking>> byClient = bookings.stream()
                .collect(Collectors.groupingBy(b -> b.getUser().getId()));

        return byClient.entrySet().stream()
                .map(e -> {
                    Long id = e.getKey();
                    List<CareBooking> clientBookings = e.getValue();
                    String name = clientBookings.get(0).getUser().getName();
                    int visitCount = (int) clientBookings.stream()
                            .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                                    && b.getStatus() != CareBookingStatus.NO_SHOW)
                            .count();
                    int cancelCount = countByStatus(clientBookings, CareBookingStatus.CANCELLED);
                    int noShow = countByStatus(clientBookings, CareBookingStatus.NO_SHOW);
                    int rev = clientBookings.stream()
                            .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                                    && b.getStatus() != CareBookingStatus.NO_SHOW)
                            .mapToInt(b -> b.getCare().getPrice() * b.getQuantity())
                            .sum();
                    int total = clientBookings.size();
                    double rate = total > 0 ? (double) visitCount / total : 0.0;
                    return new ClientRanking(id, name, visitCount, cancelCount, noShow, rev, rate);
                })
                .sorted(Comparator.comparingInt(ClientRanking::revenue).reversed())
                .toList();
    }

    private List<CareRanking> buildCareRankings(List<CareBooking> bookings) {
        Map<Long, List<CareBooking>> byCare = bookings.stream()
                .collect(Collectors.groupingBy(b -> b.getCare().getId()));

        return byCare.entrySet().stream()
                .map(e -> {
                    Long id = e.getKey();
                    List<CareBooking> careBookings = e.getValue();
                    String name = careBookings.get(0).getCare().getName();
                    int count = careBookings.size();
                    int rev = careBookings.stream()
                            .filter(b -> b.getStatus() != CareBookingStatus.CANCELLED
                                    && b.getStatus() != CareBookingStatus.NO_SHOW)
                            .mapToInt(b -> b.getCare().getPrice() * b.getQuantity())
                            .sum();
                    return new CareRanking(id, name, count, rev);
                })
                .sorted(Comparator.comparingInt(CareRanking::revenue).reversed())
                .toList();
    }

    // --- At-risk clients ---

    private List<AtRiskClient> findAtRiskClients(LocalDate today) {
        // Look back far enough to find clients who were active but stopped coming
        LocalDate lookbackStart = today.minusYears(1);
        List<CareBooking> recentBookings = bookingRepo.findByAppointmentDateBetween(lookbackStart, today);

        // Group by client, find latest non-cancelled booking
        Map<Long, CareBooking> latestByClient = new HashMap<>();
        for (CareBooking b : recentBookings) {
            if (b.getStatus() == CareBookingStatus.CANCELLED) {
                continue;
            }
            Long clientId = b.getUser().getId();
            CareBooking existing = latestByClient.get(clientId);
            if (existing == null || b.getAppointmentDate().isAfter(existing.getAppointmentDate())) {
                latestByClient.put(clientId, b);
            }
        }

        LocalDate threshold = today.minusDays(AT_RISK_THRESHOLD_DAYS);
        return latestByClient.values().stream()
                .filter(b -> b.getAppointmentDate().isBefore(threshold))
                .map(b -> new AtRiskClient(
                        b.getUser().getId(),
                        b.getUser().getName(),
                        b.getAppointmentDate().format(DAY_FORMAT),
                        (int) ChronoUnit.DAYS.between(b.getAppointmentDate(), today)
                ))
                .sorted(Comparator.comparingInt(AtRiskClient::daysSinceLastVisit).reversed())
                .toList();
    }
}
