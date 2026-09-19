package com.ziyadsamhaoui.messagingchatservice.service.support;

import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
public class PageSizeResolver {

    private final ChatProperties chatProperties;

    public PageSizeResolver(ChatProperties chatProperties) {
        this.chatProperties = chatProperties;
    }

    public int resolve(Integer requestedLimit) {
        if (requestedLimit == null) {
            return chatProperties.defaultPageSize();
        }

        if (requestedLimit < 1) {
            throw new InvalidRequestException("INVALID_LIMIT", "limit must be greater than zero");
        }

        return Math.min(requestedLimit, chatProperties.maxPageSize());
    }
}
