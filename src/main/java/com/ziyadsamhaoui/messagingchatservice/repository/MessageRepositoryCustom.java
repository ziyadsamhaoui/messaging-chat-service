package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.Message;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepositoryCustom {

    List<Message> findPageByRoom(String roomId, Instant cursorCreatedAt, String cursorId, int limit);

    Optional<Message> findLatestActiveInRoom(String roomId);

    void updateContent(String messageId, String content, Instant editedAt);

    void softDelete(String messageId);

    void addAttachments(String messageId, Collection<String> attachmentIds);
}
