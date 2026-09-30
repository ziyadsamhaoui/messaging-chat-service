package com.ziyadsamhaoui.messagingchatservice.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = UserCacheEntry.COLLECTION)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCacheEntry {

    public static final String COLLECTION = "user_cache";

    @Id
    private String userId;

    @Field("username")
    private String username;

    @Field("updatedAt")
    private Instant updatedAt;
}
