package com.ziyadsamhaoui.messagingchatservice.dto.response;

import java.util.List;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }

    public static <T> CursorPage<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new CursorPage<>(items, nextCursor, hasMore);
    }
}
