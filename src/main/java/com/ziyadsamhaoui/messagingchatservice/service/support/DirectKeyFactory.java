package com.ziyadsamhaoui.messagingchatservice.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class DirectKeyFactory {

    private static final String JOINING_SEPARATOR = ":";

    public String create(String firstUserId, String secondUserId) {
        List<String> userIds = Stream.of(firstUserId, secondUserId).sorted().toList();
        return sha256(String.join(JOINING_SEPARATOR, userIds));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
