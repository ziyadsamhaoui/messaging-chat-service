package com.ziyadsamhaoui.messagingchatservice.model;

import com.ziyadsamhaoui.messagingchatservice.model.enums.RoomType;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = ChatRoom.COLLECTION)
@Getter
@Setter
@NoArgsConstructor
public class ChatRoom {

    public static final String COLLECTION = "chatrooms";

    @Id
    private String id;

    private RoomType type;

    private String name;

    @Field(write = Field.Write.NON_NULL)
    private String directKey;

    @Field("isFavorited")
    private boolean favorited;

    private String createdBy;

    private Instant createdAt;

    @Field("isDeleted")
    private boolean deleted;

    private Instant deletedAt;

    private String lastMessageId;

    public static ChatRoom group(String name, String createdBy, Instant createdAt) {
        ChatRoom room = new ChatRoom();
        room.type = RoomType.GROUP;
        room.name = name;
        room.createdBy = createdBy;
        room.createdAt = createdAt;
        return room;
    }

    public static ChatRoom direct(String directKey, String createdBy, Instant createdAt) {
        ChatRoom room = new ChatRoom();
        room.type = RoomType.DIRECT;
        room.directKey = directKey;
        room.createdBy = createdBy;
        room.createdAt = createdAt;
        return room;
    }
}
