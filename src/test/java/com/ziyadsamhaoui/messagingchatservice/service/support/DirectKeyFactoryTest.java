package com.ziyadsamhaoui.messagingchatservice.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DirectKeyFactoryTest {

    private final DirectKeyFactory directKeyFactory = new DirectKeyFactory();

    @Test
    void producesTheSameKeyRegardlessOfArgumentOrder() {
        assertThat(directKeyFactory.create("user-1", "user-2"))
                .isEqualTo(directKeyFactory.create("user-2", "user-1"));
    }

    @Test
    void producesAHexEncodedSha256Digest() {
        assertThat(directKeyFactory.create("user-1", "user-2")).matches("[0-9a-f]{64}");
    }

    @Test
    void producesDifferentKeysForDifferentPairs() {
        assertThat(directKeyFactory.create("user-1", "user-2"))
                .isNotEqualTo(directKeyFactory.create("user-1", "user-3"));
    }

    @Test
    void isStableAcrossInvocations() {
        assertThat(directKeyFactory.create("user-1", "user-2"))
                .isEqualTo(directKeyFactory.create("user-1", "user-2"));
    }
}
