package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.ReadCursor;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReadCursorRepository extends MongoRepository<ReadCursor, String>, ReadCursorRepositoryCustom {

    Optional<ReadCursor> findByRoomIdAndUserId(String roomId, String userId);
}
