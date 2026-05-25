package com.luxpretty.app.bookings.web.dto;

import java.util.List;

public record SlotWithEmployees(String time, List<EmployeeSlotState> employees) {}
