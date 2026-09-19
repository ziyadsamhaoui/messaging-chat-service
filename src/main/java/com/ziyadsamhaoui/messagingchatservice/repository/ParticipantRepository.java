package com.ziyadsamhaoui.messagingchatservice.repository;

import com.ziyadsamhaoui.messagingchatservice.model.Participant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParticipantRepository extends MongoRepository<Participant, String>, ParticipantRepositoryCustom {

    Optional<Participant> findByRoomIdAndUserId(String roomId, String userId);

    List<Participant> findByRoomId(String roomId);

    List<Participant> findByUserId(String userId);

    boolean existsByRoomIdAndUserId(String roomId, String userId);

    long deleteByRoomIdAndUserId(String roomId, String userId);
}
