package com.ziyadsamhaoui.messagingchatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingchatservice.model.ChatRoom;
import com.ziyadsamhaoui.messagingchatservice.repository.ChatRoomRepository;
import com.ziyadsamhaoui.messagingchatservice.service.support.DirectKeyFactory;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * A connection becoming established must produce exactly one direct room: repeated
 * deliveries and concurrent creations must converge, and an existing room must be
 * reused rather than duplicated.
 */
class DirectRoomProvisionerTest {

    private static final String USER_A = "user-1";
    private static final String USER_B = "user-2";

    private ChatRoomRepository chatRoomRepository;
    private RoomWriter roomWriter;
    private DirectKeyFactory directKeyFactory;
    private DirectRoomProvisioner provisioner;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        roomWriter = mock(RoomWriter.class);
        directKeyFactory = new DirectKeyFactory();
        provisioner = new DirectRoomProvisioner(chatRoomRepository, roomWriter, directKeyFactory);
    }

    @Test
    void createsExactlyOneRoomWhenThePairHasNone() {
        String key = directKeyFactory.create(USER_A, USER_B);
        when(chatRoomRepository.findActiveByDirectKey(key)).thenReturn(Optional.empty());
        when(roomWriter.insertDirectRoom(eq(USER_A), eq(USER_B), eq(key), any(Instant.class)))
                .thenReturn(mock(ChatRoom.class));

        boolean created = provisioner.ensureDirectRoom(USER_A, USER_B);

        assertThat(created).isTrue();
        verify(roomWriter, times(1)).insertDirectRoom(eq(USER_A), eq(USER_B), eq(key), any(Instant.class));
    }

    @Test
    void reusesTheExistingRoomWithoutCreatingADuplicate() {
        String key = directKeyFactory.create(USER_A, USER_B);
        when(chatRoomRepository.findActiveByDirectKey(key)).thenReturn(Optional.of(mock(ChatRoom.class)));

        boolean created = provisioner.ensureDirectRoom(USER_A, USER_B);

        assertThat(created).isFalse();
        verify(roomWriter, never()).insertDirectRoom(anyString(), anyString(), anyString(), any());
    }

    @Test
    void duplicateDeliveryIsIdempotentBecauseTheSecondCallFindsTheRoom() {
        String key = directKeyFactory.create(USER_A, USER_B);
        when(chatRoomRepository.findActiveByDirectKey(key)).thenReturn(Optional.empty());
        when(roomWriter.insertDirectRoom(anyString(), anyString(), anyString(), any()))
                .thenReturn(mock(ChatRoom.class));

        assertThat(provisioner.ensureDirectRoom(USER_A, USER_B)).isTrue();

        when(chatRoomRepository.findActiveByDirectKey(key)).thenReturn(Optional.of(mock(ChatRoom.class)));
        assertThat(provisioner.ensureDirectRoom(USER_A, USER_B)).isFalse();

        verify(roomWriter, times(1)).insertDirectRoom(anyString(), anyString(), anyString(), any());
    }

    @Test
    void aConcurrentCreationThatWinsTheUniqueIndexRaceIsSwallowed() {
        String key = directKeyFactory.create(USER_A, USER_B);
        when(chatRoomRepository.findActiveByDirectKey(key)).thenReturn(Optional.empty());
        when(roomWriter.insertDirectRoom(anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("directKey already exists"));

        boolean created = provisioner.ensureDirectRoom(USER_A, USER_B);

        assertThat(created).isFalse();
    }

    @Test
    void ignoresSelfConnectionsAndMissingParticipants() {
        assertThat(provisioner.ensureDirectRoom(USER_A, USER_A)).isFalse();
        assertThat(provisioner.ensureDirectRoom(null, USER_B)).isFalse();
        assertThat(provisioner.ensureDirectRoom(USER_A, null)).isFalse();

        verify(roomWriter, never()).insertDirectRoom(anyString(), anyString(), anyString(), any());
    }
}
