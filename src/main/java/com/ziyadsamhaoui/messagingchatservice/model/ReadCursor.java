package com.ziyadsamhaoui.messagingchatservice.model;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = ReadCursor.COLLECTION)
@Getter
@Setter
@NoArgsConstructor
public class ReadCursor {

    public static final String COLLECTION = "read_cursors";

    @Id
    private String id;

    private String roomId;

    private String userId;

    private String lastReadMessageId;

    private Instant lastReadAt;
}
