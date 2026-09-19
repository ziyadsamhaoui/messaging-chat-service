package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CursorCodec {

    private static final String SEPARATOR = "|";

    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public String encode(Instant createdAt, String id) {
        return encodeValue(createdAt.toEpochMilli() + SEPARATOR + id);
    }

    public String encodeId(String id) {
        return encodeValue(id);
    }

    public Optional<Cursor> decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return Optional.empty();
        }

        String raw = decodeValue(cursor);
        int separatorIndex = raw.indexOf(SEPARATOR);

        if (separatorIndex < 1 || separatorIndex == raw.length() - 1) {
            throw malformedCursor();
        }

        try {
            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separatorIndex)));
            return Optional.of(new Cursor(createdAt, raw.substring(separatorIndex + 1)));
        } catch (NumberFormatException exception) {
            throw malformedCursor();
        }
    }

    public Optional<String> decodeId(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return Optional.empty();
        }

        String raw = decodeValue(cursor);
        if (!StringUtils.hasText(raw) || raw.contains(SEPARATOR)) {
            throw malformedCursor();
        }

        return Optional.of(raw);
    }

    private String encodeValue(String value) {
        return encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeValue(String cursor) {
        try {
            return new String(decoder.decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw malformedCursor();
        }
    }

    private InvalidRequestException malformedCursor() {
        return new InvalidRequestException("INVALID_CURSOR", "The supplied cursor is malformed");
    }

    public record Cursor(Instant createdAt, String id) {
    }
}
