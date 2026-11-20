package com.loleague.meme.post;

import com.loleague.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void roundTripsWithMicrosecondPrecision() {
        Cursor c = new Cursor(OffsetDateTime.parse("2026-11-20T21:17:03.123456+05:30"), UUID.randomUUID());
        assertThat(Cursor.decode(c.encode())).isEqualTo(c);
    }

    @Test
    void blankMeansFirstPage() {
        assertThat(Cursor.decode(null)).isNull();
        assertThat(Cursor.decode("")).isNull();
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> Cursor.decode("not-a-cursor")).isInstanceOf(ApiException.class);
    }
}
