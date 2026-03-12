package com.cogtrade.trade;

import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * Server-side state for one active direct player-to-player trade session.
 * All mutation goes through this class so ready-state resets are automatic.
 */
public class DirectTradeSession {

    public static final int OFFER_SLOTS = 9; // 3×3 grid per side

    private final String sessionId;
    private final UUID  initiatorUuid;
    private final UUID  targetUuid;
    private final String initiatorName;
    private final String targetName;

    private final ItemStack[] initiatorOffer;
    private final ItemStack[] targetOffer;

    private double  initiatorCoins;
    private double  targetCoins;
    private boolean initiatorReady;
    private boolean targetReady;

    /** Incremented on every mutation so the client can detect stale state. */
    private int revision;

    private final long createdAt;
    /** False once the session has been deactivated (complete or aborted). */
    private volatile boolean active;

    public DirectTradeSession(String sessionId,
                               UUID initiatorUuid, String initiatorName,
                               UUID targetUuid,    String targetName) {
        this.sessionId      = sessionId;
        this.initiatorUuid  = initiatorUuid;
        this.initiatorName  = initiatorName;
        this.targetUuid     = targetUuid;
        this.targetName     = targetName;

        this.initiatorOffer = new ItemStack[OFFER_SLOTS];
        this.targetOffer    = new ItemStack[OFFER_SLOTS];
        for (int i = 0; i < OFFER_SLOTS; i++) {
            initiatorOffer[i] = ItemStack.EMPTY;
            targetOffer[i]    = ItemStack.EMPTY;
        }

        this.initiatorCoins = 0;
        this.targetCoins    = 0;
        this.initiatorReady = false;
        this.targetReady    = false;
        this.revision       = 0;
        this.createdAt      = System.currentTimeMillis();
        this.active         = true;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public String    getSessionId()     { return sessionId; }
    public UUID      getInitiatorUuid() { return initiatorUuid; }
    public UUID      getTargetUuid()    { return targetUuid; }
    public String    getInitiatorName() { return initiatorName; }
    public String    getTargetName()    { return targetName; }
    public ItemStack[] getInitiatorOffer() { return initiatorOffer; }
    public ItemStack[] getTargetOffer()    { return targetOffer; }
    public double    getInitiatorCoins()  { return initiatorCoins; }
    public double    getTargetCoins()     { return targetCoins; }
    public boolean   isInitiatorReady()   { return initiatorReady; }
    public boolean   isTargetReady()      { return targetReady; }
    public int       getRevision()        { return revision; }
    public long      getCreatedAt()       { return createdAt; }
    public boolean   isActive()           { return active; }

    /** Mark the session as no longer active.  Call before cleanup. */
    public void deactivate() { this.active = false; }

    // ── Perspective helpers ───────────────────────────────────────────────

    public boolean involves(UUID uuid) {
        return uuid.equals(initiatorUuid) || uuid.equals(targetUuid);
    }

    /** Returns the offer array for the given player, or null if not in session. */
    public ItemStack[] getOfferFor(UUID uuid) {
        if (uuid.equals(initiatorUuid)) return initiatorOffer;
        if (uuid.equals(targetUuid))    return targetOffer;
        return null;
    }

    public UUID getPartnerUuid(UUID uuid) {
        if (uuid.equals(initiatorUuid)) return targetUuid;
        if (uuid.equals(targetUuid))    return initiatorUuid;
        return null;
    }

    public String getPartnerName(UUID uuid) {
        if (uuid.equals(initiatorUuid)) return targetName;
        if (uuid.equals(targetUuid))    return initiatorName;
        return null;
    }

    public double getCoinsFor(UUID uuid) {
        if (uuid.equals(initiatorUuid)) return initiatorCoins;
        if (uuid.equals(targetUuid))    return targetCoins;
        return 0;
    }

    public boolean isReadyFor(UUID uuid) {
        if (uuid.equals(initiatorUuid)) return initiatorReady;
        if (uuid.equals(targetUuid))    return targetReady;
        return false;
    }

    // ── Mutations (all reset ready states when offer changes) ─────────────

    /**
     * Places newItem into the given offer slot for uuid.
     * Returns the item that was previously in that slot (may be EMPTY).
     * Automatically resets both ready states on any change.
     */
    public ItemStack setOfferItem(UUID uuid, int slot, ItemStack newItem) {
        ItemStack[] offer = getOfferFor(uuid);
        if (offer == null || slot < 0 || slot >= OFFER_SLOTS) return ItemStack.EMPTY;

        ItemStack previous = offer[slot].copy();
        offer[slot] = newItem.isEmpty() ? ItemStack.EMPTY : newItem.copy();
        resetReady();
        revision++;
        return previous;
    }

    /**
     * Sets the coin offer for uuid.  Resets ready states.
     * @return false if uuid is not in this session
     */
    public boolean setCoinOffer(UUID uuid, double amount) {
        if (uuid.equals(initiatorUuid)) {
            initiatorCoins = amount;
        } else if (uuid.equals(targetUuid)) {
            targetCoins = amount;
        } else {
            return false;
        }
        resetReady();
        revision++;
        return true;
    }

    /** Explicitly sets one player's ready state without touching the other's. */
    public void setReady(UUID uuid, boolean ready) {
        if (uuid.equals(initiatorUuid)) initiatorReady = ready;
        else if (uuid.equals(targetUuid)) targetReady   = ready;
        revision++;
    }

    public boolean bothReady() {
        return initiatorReady && targetReady;
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private void resetReady() {
        initiatorReady = false;
        targetReady    = false;
    }
}
