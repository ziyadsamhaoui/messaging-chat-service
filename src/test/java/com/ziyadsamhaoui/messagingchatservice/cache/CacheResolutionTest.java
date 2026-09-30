package com.ziyadsamhaoui.messagingchatservice.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.exception.DependencyUnavailableException;
import com.ziyadsamhaoui.messagingchatservice.exception.ForbiddenOperationException;
import com.ziyadsamhaoui.messagingchatservice.security.CurrentUserProvider;
import com.ziyadsamhaoui.messagingchatservice.service.support.BlockPolicy;
import com.ziyadsamhaoui.messagingchatservice.service.support.SenderIdentityResolver;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sprint 6 §2.3 / §3.5 (no Docker): the two cache-first behaviours that replaced
 * Chat's synchronous calls to User.
 *
 * <ul>
 *   <li><b>Username</b> — cache first, JWT-claim fallback for cold start; the
 *       synchronous profile call is gone entirely (cosmetic data).</li>
 *   <li><b>Blocks</b> — cache first, then the synchronous call as a fail-closed
 *       cache-miss fallback; a miss is "unknown", never "not blocked" (policy data).</li>
 * </ul>
 */
class CacheResolutionTest {

    private UserCacheService userCacheService;
    private BlockCacheService blockCacheService;
    private UserServiceClient userServiceClient;
    private CurrentUserProvider currentUserProvider;

    private SenderIdentityResolver senderIdentityResolver;
    private BlockPolicy blockPolicy;

    @BeforeEach
    void setUp() {
        userCacheService = mock(UserCacheService.class);
        blockCacheService = mock(BlockCacheService.class);
        userServiceClient = mock(UserServiceClient.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        senderIdentityResolver = new SenderIdentityResolver(userCacheService, currentUserProvider);
        blockPolicy = new BlockPolicy(userServiceClient, blockCacheService);
    }

    @Test
    void usernameComesFromCacheWithoutAnyRemoteCall() {
        when(userCacheService.lookup("user-1")).thenReturn(Optional.of("zara"));

        assertThat(senderIdentityResolver.resolveUsername("user-1")).isEqualTo("zara");
        verify(userServiceClient, never()).getProfile("user-1");
    }

    @Test
    void usernameColdStartFallsBackToJwtClaim() {
        when(userCacheService.lookup("new-user")).thenReturn(Optional.empty());
        when(currentUserProvider.usernameClaim()).thenReturn(Optional.of("fresh_user"));

        assertThat(senderIdentityResolver.resolveUsername("new-user")).isEqualTo("fresh_user");
        verify(userServiceClient, never()).getProfile("new-user");
    }

    @Test
    void usernameIsNullWhenNeitherCacheNorClaimHasAValue() {
        when(userCacheService.lookup("ghost")).thenReturn(Optional.empty());
        when(currentUserProvider.usernameClaim()).thenReturn(Optional.empty());

        assertThat(senderIdentityResolver.resolveUsername("ghost")).isNull();
    }

    @Test
    void cachedBlockRejectsWithoutCallingTheUserService() {
        when(blockCacheService.isKnownBlocked("a", "b")).thenReturn(true);

        assertThatThrownBy(() -> blockPolicy.assertNotBlocked("a", "b"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(userServiceClient, never()).isBlockedBetween("a", "b");
    }

    @Test
    void cacheMissFallsBackToTheSynchronousCheck() {
        when(blockCacheService.isKnownBlocked("a", "b")).thenReturn(false);
        when(userServiceClient.isBlockedBetween("a", "b")).thenReturn(true);

        assertThatThrownBy(() -> blockPolicy.assertNotBlocked("a", "b"))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(userServiceClient).isBlockedBetween("a", "b");
    }

    @Test
    void cacheMissAndCleanSyncCheckAllowsTheOperation() {
        when(blockCacheService.isKnownBlocked("a", "b")).thenReturn(false);
        when(userServiceClient.isBlockedBetween("a", "b")).thenReturn(false);

        blockPolicy.assertNotBlocked("a", "b");

        verify(userServiceClient).isBlockedBetween("a", "b");
    }

    @Test
    void syncCheckOutageFailsClosedWhenTheCacheIsCold() {
        when(blockCacheService.isKnownBlocked("a", "b")).thenReturn(false);
        when(userServiceClient.isBlockedBetween("a", "b"))
                .thenThrow(new DependencyUnavailableException("user service down"));

        assertThatThrownBy(() -> blockPolicy.assertNotBlocked("a", "b"))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
