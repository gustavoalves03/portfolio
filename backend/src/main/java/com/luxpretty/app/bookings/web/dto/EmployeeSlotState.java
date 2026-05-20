package com.luxpretty.app.bookings.web.dto;

public record EmployeeSlotState(Long id, String name, boolean available, String reason) {
    public static EmployeeSlotState available(Long id, String name) {
        return new EmployeeSlotState(id, name, true, null);
    }
    public static EmployeeSlotState busy(Long id, String name) {
        return new EmployeeSlotState(id, name, false, "BUSY");
    }
    public static EmployeeSlotState onLeave(Long id, String name) {
        return new EmployeeSlotState(id, name, false, "ON_LEAVE");
    }
}
