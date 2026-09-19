package com.ziyadsamhaoui.messagingchatservice.config;

import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.model.Invitation;
import com.ziyadsamhaoui.messagingchatservice.model.Message;
import com.ziyadsamhaoui.messagingchatservice.model.MessageReaction;
import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import com.ziyadsamhaoui.messagingchatservice.model.ReadCursor;
import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexFilter;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "badrlink.chat.ensure-indexes", havingValue = "true", matchIfMissing = true)
public class MongoIndexInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private static final IndexFilter DIRECT_KEY_TYPE_FILTER = () -> new Document("directKey",
            new Document("$type", "string"));

    private static final IndexFilter PENDING_INVITATION_FILTER = () -> new Document("status",
            InvitationStatus.PENDING.name());

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        ensure(ChatRoom.COLLECTION, new Index().on("directKey", Sort.Direction.ASC).unique()
                .partial(DIRECT_KEY_TYPE_FILTER).named("ux_chatrooms_direct_key"));
        ensure(ChatRoom.COLLECTION, new Index().on("createdAt", Sort.Direction.DESC).on("_id", Sort.Direction.DESC)
                .named("ix_chatrooms_created_at"));

        ensure(Participant.COLLECTION, new Index().on("roomId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC)
                .unique().named("ux_participants_room_user"));
        ensure(Participant.COLLECTION, new Index().on("userId", Sort.Direction.ASC).named("ix_participants_user_id"));

        ensure(Message.COLLECTION, new Index().on("roomId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC).named("ix_messages_room_created_at"));

        ensure(MessageReaction.COLLECTION,
                new Index().on("messageId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC).unique()
                        .named("ux_message_reactions_message_user"));

        ensure(ReadCursor.COLLECTION, new Index().on("roomId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC)
                .unique().named("ux_read_cursors_room_user"));

        ensure(Invitation.COLLECTION, new Index().on("expiresAt", Sort.Direction.ASC).expire(0)
                .named("ttl_invitations_expires_at"));
        ensure(Invitation.COLLECTION, new Index().on("invitedId", Sort.Direction.ASC).on("status", Sort.Direction.ASC)
                .named("ix_invitations_invited_status"));
        ensure(Invitation.COLLECTION, new Index().on("roomId", Sort.Direction.ASC).on("invitedId", Sort.Direction.ASC)
                .unique().partial(PENDING_INVITATION_FILTER).named("ux_invitations_pending_room_invited"));
    }

    private void ensure(String collection, Index index) {
        mongoTemplate.indexOps(collection).ensureIndex(index);
        log.debug("Ensured index {} on collection {}", index.getIndexKeys(), collection);
    }
}
