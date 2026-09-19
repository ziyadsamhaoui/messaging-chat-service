package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepositoryCustom {

    Optional<ChatRoom> findActiveByDirectKey(String directKey);

    List<ChatRoom> findActivePageByIds(Collection<String> roomIds, Instant cursorCreatedAt, String cursorId, int limit);

    void setLastMessage(String roomId, String lastMessageId);

    void softDelete(String roomId, boolean releaseDirectKey);
}
