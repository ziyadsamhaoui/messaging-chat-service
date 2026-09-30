package com.ziyadsamhaoui.messagingchatservice.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Document(collection = BlockCacheEntry.COLLECTION)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockCacheEntry {

    public static final String COLLECTION = "block_cache";

    @Id
    private String id;

    private String blockerId;

    private String blockedId;
}
