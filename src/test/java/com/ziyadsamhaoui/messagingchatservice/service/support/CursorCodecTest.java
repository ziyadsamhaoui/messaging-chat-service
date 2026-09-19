package com.ziyadsamhaoui.messagingchatservice.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import com.ziyadsamhaoui.messagingchatservice.service.support.CursorCodec.Cursor;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    private final CursorCodec cursorCodec = new CursorCodec();

    @Test
    void roundTripsATimestampAndIdentifier() {
        Instant createdAt = Instant.parse("2026-02-03T04:05:06.789Z");

        String encoded = cursorCodec.encode(createdAt, "64b7f0c2f1a2b3c4d5e6f7a8");
        Cursor decoded = cursorCodec.decode(encoded).orElseThrow();

        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.id()).isEqualTo("64b7f0c2f1a2b3c4d5e6f7a8");
    }

    @Test
    void roundTripsAnIdentifierOnlyCursor() {
        String encoded = cursorCodec.encodeId("64b7f0c2f1a2b3c4d5e6f7a8");

        assertThat(cursorCodec.decodeId(encoded)).contains("64b7f0c2f1a2b3c4d5e6f7a8");
    }

    @Test
    void returnsAnEmptyCursorForBlankInput() {
        assertThat(cursorCodec.decode(null)).isEmpty();
        assertThat(cursorCodec.decode("  ")).isEmpty();
        assertThat(cursorCodec.decodeId("")).isEmpty();
    }

    @Test
    void rejectsCursorsThatAreNotBase64UrlEncoded() {
        assertThatThrownBy(() -> cursorCodec.decode("!!!not-a-cursor!!!"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cursor");
    }

    @Test
    void rejectsCursorsWithoutASeparator() {
        assertThatThrownBy(() -> cursorCodec.decode(cursorCodec.encodeId("64b7f0c2f1a2b3c4d5e6f7a8")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsIdentifierCursorsThatCarryATimestamp() {
        String encoded = cursorCodec.encode(Instant.now(), "64b7f0c2f1a2b3c4d5e6f7a8");

        assertThatThrownBy(() -> cursorCodec.decodeId(encoded)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void producesUrlSafeCursors() {
        String encoded = cursorCodec.encode(Instant.now(), "64b7f0c2f1a2b3c4d5e6f7a8");

        assertThat(encoded).doesNotContain("+").doesNotContain("/").doesNotContain("=");
    }
}
