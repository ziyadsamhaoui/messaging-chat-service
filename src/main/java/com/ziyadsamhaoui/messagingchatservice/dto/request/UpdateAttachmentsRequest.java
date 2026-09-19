package com.ziyadsamhaoui.messagingchatservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateAttachmentsRequest(
        @NotEmpty(message = "attachmentIds must not be empty") @Size(max = 10, message = "at most 10 attachments are accepted per request") List<String> attachmentIds) {
}
