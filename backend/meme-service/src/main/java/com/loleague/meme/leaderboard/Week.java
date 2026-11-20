package com.loleague.meme.leaderboard;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Leaderboard weeks run Monday 00:00 -> next Monday 00:00, India time. */
public record Week(LocalDate start) {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    public static Week current() {
        return containing(LocalDate.now(ZONE));
    }

    public static Week containing(LocalDate day) {
        return new Week(day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    public Week previous() {
        return new Week(start.minusWeeks(1));
    }

    public OffsetDateTime from() {
        return start.atStartOfDay(ZONE).toOffsetDateTime();
    }

    public OffsetDateTime until() {
        return start.plusWeeks(1).atStartOfDay(ZONE).toOffsetDateTime();
    }

    public LocalDate lastDay() {
        return start.plusDays(6);
    }
}
