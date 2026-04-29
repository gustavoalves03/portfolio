package com.prettyface.app.availability.web.dto;

import com.prettyface.app.availability.app.ClosedDaysService.ClosedDayReason;

import java.time.LocalDate;

public record ClosedDayResponse(LocalDate date, ClosedDayReason reason) {}
