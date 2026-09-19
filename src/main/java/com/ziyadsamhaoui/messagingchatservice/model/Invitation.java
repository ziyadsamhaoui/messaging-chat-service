package com.ziyadsamhaoui.messagingchatservice.model;

import com.ziyadsamhaoui.messagingchatservice.model.enums.InvitationStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = Invitation.COLLECTION)
@Getter
@Setter
@NoArgsConstructor
public class Invitation {

    public static final String COLLECTION = "invitations";

    @Id
    private String id;

    private String roomId;

    private String inviterId;

    private String invitedId;

    private InvitationStatus status;

    private Instant sentAt;

    private Instant expiresAt;

    public boolean isExpiredAt(Instant reference) {
        return expiresAt != null && !expiresAt.isAfter(reference);
    }
}
