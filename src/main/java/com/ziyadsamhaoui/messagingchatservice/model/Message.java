package com.ziyadsamhaoui.messagingchatservice.model;

import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = Message.COLLECTION)
@Getter
@Setter
@NoArgsConstructor
public class Message {

    public static final String COLLECTION = "messages";

    @Id
    private String id;

    private String roomId;

    private String senderId;

    private String senderUsername;

    private MessageType type;

    private String content;

    private List<String> attachmentIds = new ArrayList<>();

    @Field("isDeleted")
    private boolean deleted;

    private Instant deletedAt;

    @Field("isEdited")
    private boolean edited;

    private Instant editedAt;

    private Instant createdAt;
}
