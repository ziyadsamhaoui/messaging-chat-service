package com.ziyadsamhaoui.messagingchatservice.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingchatservice.model.enums.MessageType;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.convert.UpdateMapper;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

class ChatDocumentMappingTest {

    private static final String OBJECT_ID = "64b7f0c2f1a2b3c4d5e6f7a8";
    private static final Instant REFERENCE_TIME = Instant.parse("2026-01-01T10:00:00Z");

    private MongoMappingContext mappingContext;
    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());

        mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.setInitialEntitySet(Set.of(ChatRoom.class, Participant.class, Message.class,
                MessageReaction.class, ReadCursor.class, Invitation.class));
        mappingContext.afterPropertiesSet();

        MongoDatabaseFactory databaseFactory = mock(MongoDatabaseFactory.class);
        when(databaseFactory.getExceptionTranslator()).thenReturn(new MongoExceptionTranslator());

        DbRefResolver dbRefResolver = new DefaultDbRefResolver(databaseFactory);
        converter = new MappingMongoConverter(dbRefResolver, mappingContext);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @Test
    void storesTheIdentifierAsAnObjectIdAndMapsIdCriteriaToTheSameValue() {
        ChatRoom room = ChatRoom.group("badrlink-core", "user-1", REFERENCE_TIME);
        room.setId(OBJECT_ID);

        Document storedDocument = write(room);
        Object storedId = storedDocument.get("_id");

        Document mappedCriteria = mapper().getMappedObject(Criteria.where("_id").is(OBJECT_ID).getCriteriaObject(),
                mappingContext.getPersistentEntity(ChatRoom.class));

        assertThat(storedId).isEqualTo(new ObjectId(OBJECT_ID));
        assertThat(mappedCriteria.get("_id")).isEqualTo(storedId);
        assertThat(converter.read(ChatRoom.class, storedDocument).getId()).isEqualTo(OBJECT_ID);
    }

    @Test
    void mapsJavaPropertyNamesToDocumentFieldNamesInCriteria() {
        Document mappedCriteria = mapper().getMappedObject(
                Criteria.where("deleted").is(false).and("directKey").is("direct-key").getCriteriaObject(),
                mappingContext.getPersistentEntity(ChatRoom.class));

        assertThat(mappedCriteria).containsEntry("isDeleted", false).containsEntry("directKey", "direct-key");
    }

    @Test
    void mapsJavaPropertyNamesToDocumentFieldNamesInUpdates() {
        UpdateMapper updateMapper = new UpdateMapper(converter);

        Document roomUpdate = updateMapper.getMappedObject(
                new Update().set("deleted", true).set("favorited", true).unset("directKey").getUpdateObject(),
                mappingContext.getPersistentEntity(ChatRoom.class));
        Document messageUpdate = updateMapper.getMappedObject(
                new Update().set("deleted", true).set("edited", true).set("editedAt", REFERENCE_TIME)
                        .getUpdateObject(),
                mappingContext.getPersistentEntity(Message.class));
        Document participantUpdate = updateMapper.getMappedObject(
                new Update().set("muted", true).set("mutedUntil", REFERENCE_TIME).getUpdateObject(),
                mappingContext.getPersistentEntity(Participant.class));

        Document roomFields = (Document) roomUpdate.get("$set");
        Document messageFields = (Document) messageUpdate.get("$set");
        Document participantFields = (Document) participantUpdate.get("$set");

        assertThat(roomFields).containsEntry("isDeleted", true).containsEntry("isFavorited", true);
        assertThat((Document) roomUpdate.get("$unset")).containsKey("directKey");
        assertThat(messageFields).containsEntry("isDeleted", true).containsEntry("isEdited", true);
        assertThat(participantFields).containsEntry("isMuted", true);

        assertThat(((Date) messageFields.get("editedAt")).toInstant()).isEqualTo(REFERENCE_TIME);
        assertThat(((Date) participantFields.get("mutedUntil")).toInstant()).isEqualTo(REFERENCE_TIME);
    }

    @Test
    void omitsTheDirectKeyForGroupRoomsAndStoresItForDirectRooms() {
        assertThat(write(ChatRoom.group("badrlink-core", "user-1", REFERENCE_TIME))).doesNotContainKey("directKey");
        assertThat(write(ChatRoom.direct("direct-key", "user-1", REFERENCE_TIME))).containsEntry("directKey",
                "direct-key");
    }

    @Test
    void storesAttachmentIdentifiersAsAList() {
        Message message = new Message();
        message.setRoomId(OBJECT_ID);
        message.setSenderId("user-1");
        message.setType(MessageType.TEXT);
        message.setContent("hello");
        message.setCreatedAt(REFERENCE_TIME);
        message.setAttachmentIds(List.of("attachment-1", "attachment-2"));

        Document storedDocument = write(message);

        assertThat(storedDocument.getList("attachmentIds", String.class))
                .containsExactly("attachment-1", "attachment-2");
    }

    @Test
    void storesBooleanFlagsUnderTheirDocumentFieldNames() {
        ChatRoom room = ChatRoom.group("badrlink-core", "user-1", REFERENCE_TIME);
        room.setDeleted(true);
        room.setFavorited(true);

        Document storedDocument = write(room);

        assertThat(storedDocument.getBoolean("isDeleted")).isTrue();
        assertThat(storedDocument.getBoolean("isFavorited")).isTrue();
        assertThat(storedDocument).doesNotContainKey("deleted").doesNotContainKey("favorited");
    }

    private QueryMapper mapper() {
        return new QueryMapper(converter);
    }

    private Document write(Object entity) {
        Document document = new Document();
        converter.write(entity, document);
        return document;
    }
}
