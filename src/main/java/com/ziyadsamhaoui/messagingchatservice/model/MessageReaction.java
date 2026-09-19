package com.ziyadsamhaoui.messagingchatservice.model;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = MessageReaction.COLLECTION)
@Getter
@Setter
@NoArgsConstructor
public class MessageReaction {

    public static final String COLLECTION = "message_reactions";

    @Id
    private String id;

    private String messageId;

    private String userId;

    private String emoji;

    private Instant reactedAt;
}
