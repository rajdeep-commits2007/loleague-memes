package com.loleague.meme.leaderboard;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeekTest {

    @Test
    void weekStartsOnMonday() {
        Week week = Week.containing(LocalDate.of(2026, 11, 20)); // a Friday
        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(week.start().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(week.lastDay()).isEqualTo(LocalDate.of(2026, 11, 22));
    }

    @Test
    void mondayBelongsToItsOwnWeek() {
        assertThat(Week.containing(LocalDate.of(2026, 11, 16)).start()).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void rangeIsSevenDaysInIndiaTime() {
        Week week = Week.containing(LocalDate.of(2026, 11, 20));
        assertThat(week.from().getOffset().getTotalSeconds()).isEqualTo(19800);
        assertThat(week.until()).isEqualTo(week.from().plusDays(7));
        assertThat(week.previous().start()).isEqualTo(LocalDate.of(2026, 11, 9));
    }
}
