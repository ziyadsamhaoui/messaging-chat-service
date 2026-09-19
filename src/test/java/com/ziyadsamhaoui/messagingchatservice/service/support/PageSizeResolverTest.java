package com.ziyadsamhaoui.messagingchatservice.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PageSizeResolverTest {

    private final PageSizeResolver pageSizeResolver = new PageSizeResolver(
            new ChatProperties(30, 100, 100, 4000, Duration.ofHours(72), Duration.ofDays(30), true, "token"));

    @Test
    void fallsBackToTheDefaultPageSize() {
        assertThat(pageSizeResolver.resolve(null)).isEqualTo(30);
    }

    @Test
    void capsLimitsAtTheConfiguredMaximum() {
        assertThat(pageSizeResolver.resolve(500)).isEqualTo(100);
    }

    @Test
    void keepsLimitsWithinTheAllowedRange() {
        assertThat(pageSizeResolver.resolve(5)).isEqualTo(5);
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThatThrownBy(() -> pageSizeResolver.resolve(0)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> pageSizeResolver.resolve(-1)).isInstanceOf(InvalidRequestException.class);
    }
}
