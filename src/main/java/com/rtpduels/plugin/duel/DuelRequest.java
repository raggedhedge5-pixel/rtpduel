package com.rtpduels.plugin.duel;

import java.util.UUID;

/** One pending duel challenge: sender -> target, with a timestamp for expiry. */
public class DuelRequest {

    private final UUID senderUuid;
    private final String senderName;
    private final long createdAtMillis;

    public DuelRequest(UUID senderUuid, String senderName, long createdAtMillis) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.createdAtMillis = createdAtMillis;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public String getSenderName() {
        return senderName;
    }

    public boolean isExpired(long nowMillis, long timeoutSeconds) {
        return nowMillis - createdAtMillis > timeoutSeconds * 1000L;
    }
}
