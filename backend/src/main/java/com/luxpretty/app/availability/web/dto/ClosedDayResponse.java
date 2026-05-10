package com.luxpretty.app.availability.web.dto;

import com.luxpretty.app.availability.app.ClosedDaysService.ClosedDayReason;

import java.time.LocalDate;

public record ClosedDayResponse(LocalDate date, ClosedDayReason reason) {}
