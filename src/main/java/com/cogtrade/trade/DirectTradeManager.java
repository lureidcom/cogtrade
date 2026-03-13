package com.cogtrade.trade;

import com.cogtrade.CogTrade;
import com.cogtrade.database.DatabaseManager;
import com.cogtrade.economy.PlayerEconomy;
import com.cogtrade.network.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages all live direct-trade sessions and pending requests.
 * All public methods must be called on the Minecraft server main thread.
 * The timeout scheduler dispatches back to the server thread via server.execute().
 */
public class DirectTradeManager {

    // ── Constants ─────────────────────────────────────────────────────────

    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final double MAX_COIN_OFFER = 10_000_000.0;

    // ── Runtime state (server-thread-only after server.execute() dispatch) ─

    /** sessionId → session */
    private static final Map<String, DirectTradeSession> sessions = new ConcurrentHashMap<>();

    /** playerUuid → sessionId */
    private static final Map<UUID, String> playerToSession = new ConcurrentHashMap<>();

    /** targetUuid → pending request */
    private static final Map<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    /** Tracks one outgoing request per initiator so they can't spam multiple targets. */
    private static final Map<UUID, UUID> initiatorToTarget = new ConcurrentHashMap<>();

    /** playerUuid → pending item returns (for offline recovery) */
    private static final Map<UUID, List<PendingItemReturn>> pendingReturns = new ConcurrentHashMap<>();

    private record PendingRequest(UUID initiatorUuid,
                                   String initiatorName,
                                   String requestId,
                                   ScheduledFuture<?> timeoutFuture) {}

    /** Single daemon thread for request expiry scheduling. */
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CogTrade-TradeExpiry");
                t.setDaemon(true);
                return t;
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Called on SERVER_STARTED to discard stale state from a previous world session. */
    public static void reset() {
        pendingRequests.values().forEach(pr -> pr.timeoutFuture().cancel(false));
        pendingRequests.clear();
        initiatorToTarget.clear();
        sessions.clear();
        playerToSession.clear();
        pendingReturns.clear();
    }

    /** Called on SERVER_STOPPING. */
    public static void shutdown() {
        reset();
        scheduler.shutdownNow();
    }

    // ── Trade request flow ────────────────────────────────────────────────

    /**
     * @return "sent" | "self" | "target_offline" | "already_in_session"
     *         | "target_in_session" | "request_pending" | "target_busy"
     */
    public static String sendRequest(MinecraftServer server,
                                      ServerPlayerEntity initiator,
                                      ServerPlayerEntity target) {
        UUID initUuid = initiator.getUuid();
        UUID tgtUuid  = target.getUuid();

        if (initUuid.equals(tgtUuid)) return "self";
        if (playerToSession.containsKey(initUuid)) return "already_in_session";
        if (playerToSession.containsKey(tgtUuid))  return "target_in_session";

        // Initiator already has a pending outgoing request
        if (initiatorToTarget.containsKey(initUuid)) return "request_pending";

        // Target already has a pending request from someone → they can only handle one
        if (pendingRequests.containsKey(tgtUuid)) return "target_busy";

        String requestId = UUID.randomUUID().toString();

        ScheduledFuture<?> future = scheduler.schedule(
                () -> server.execute(() -> expireRequest(server, tgtUuid, requestId)),
                REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        pendingRequests.put(tgtUuid, new PendingRequest(initUuid, initiator.getName().getString(), requestId, future));
        initiatorToTarget.put(initUuid, tgtUuid);

        // Notify target with clickable accept/reject buttons
        String initName = initiator.getName().getString();
        Text msg = Text.literal("§e[CogTrade] §f" + initName + " §7sana doğrudan takas isteği gönderdi.  ")
                .append(Text.literal("§a[Kabul Et]").setStyle(
                        Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"))))
                .append(Text.literal(" §c[Reddet]").setStyle(
                        Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade reject"))));
        target.sendMessage(msg, false);
        target.sendMessage(Text.literal("§8(" + REQUEST_TIMEOUT_SECONDS + " saniye içinde yanıtlamazsan istek iptal olur.)"), false);

        return "sent";
    }

    private static void expireRequest(MinecraftServer server, UUID targetUuid, String requestId) {
        PendingRequest pr = pendingRequests.get(targetUuid);
        if (pr == null || !pr.requestId().equals(requestId)) return;

        pendingRequests.remove(targetUuid);
        initiatorToTarget.remove(pr.initiatorUuid());

        ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(pr.initiatorUuid());
        if (initiator != null) {
            initiator.sendMessage(Text.literal("§e[CogTrade] §7Takas isteğin zaman aşımına uğradı."), false);
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(Text.literal("§e[CogTrade] §7Takas isteği süresi doldu."), false);
        }
    }

    /** @return "ok" | "no_request" | "already_in_session" | "initiator_offline" | "initiator_in_session" */
    public static String acceptRequest(MinecraftServer server, ServerPlayerEntity target) {
        UUID tgtUuid = target.getUuid();

        PendingRequest pr = pendingRequests.remove(tgtUuid);
        if (pr == null) return "no_request";
        pr.timeoutFuture().cancel(false);
        initiatorToTarget.remove(pr.initiatorUuid());

        if (playerToSession.containsKey(tgtUuid)) return "already_in_session";

        ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(pr.initiatorUuid());
        if (initiator == null) {
            target.sendMessage(Text.literal("§c[CogTrade] İsteği gönderen oyuncu artık çevrimiçi değil."), false);
            return "initiator_offline";
        }
        if (playerToSession.containsKey(pr.initiatorUuid())) {
            target.sendMessage(Text.literal("§c[CogTrade] İsteği gönderen oyuncu başka bir takasta."), false);
            return "initiator_in_session";
        }

        String sessionId = UUID.randomUUID().toString();
        DirectTradeSession session = new DirectTradeSession(
                sessionId,
                pr.initiatorUuid(), pr.initiatorName(),
                tgtUuid, target.getName().getString());

        sessions.put(sessionId, session);
        playerToSession.put(pr.initiatorUuid(), sessionId);
        playerToSession.put(tgtUuid, sessionId);

        OpenDirectTradePacket.send(initiator, session, true);
        OpenDirectTradePacket.send(target,    session, false);

        CogTrade.LOGGER.info("[CogTrade] Takas başladı: {} ↔ {} (session: {})",
                pr.initiatorName(), target.getName().getString(), sessionId);
        return "ok";
    }

    public static void rejectRequest(MinecraftServer server, ServerPlayerEntity target) {
        UUID tgtUuid = target.getUuid();
        PendingRequest pr = pendingRequests.remove(tgtUuid);
        if (pr == null) {
            target.sendMessage(Text.literal("§7Bekleyen bir takas isteğin yok."), false);
            return;
        }
        pr.timeoutFuture().cancel(false);
        initiatorToTarget.remove(pr.initiatorUuid());

        target.sendMessage(Text.literal("§e[CogTrade] §7Takas isteği reddedildi."), false);

        ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(pr.initiatorUuid());
        if (initiator != null) {
            initiator.sendMessage(Text.literal(
                    "§e[CogTrade] §f" + target.getName().getString() + " §7takas isteğini reddetti."), false);
        }
    }

    // ── In-session actions ────────────────────────────────────────────────

    /**
     * Moves an item between player inventory and their offer slot.
     * Supports partial stack moves based on click type.
     *
     * @param offerSlot 0–8  target offer slot; -1 = auto-pick first empty or matching stack
     * @param invSlot   0–35 inventory slot to take from; -1 = return offer slot to inventory
     * @param clickType 0 = left (full stack), 1 = right (single item)
     */
    public static void handleItemMove(MinecraftServer server,
                                       ServerPlayerEntity player,
                                       String sessionId, int offerSlot, int invSlot, byte clickType) {
        DirectTradeSession session = getValidSession(player, sessionId);
        if (session == null) return;

        ItemStack[] offer = session.getOfferFor(player.getUuid());
        if (offer == null) return;

        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        boolean isRightClick = (clickType == TradeItemMovePacket.CLICK_RIGHT);

        if (invSlot == -1) {
            // Return item from offer slot to inventory
            if (offerSlot < 0 || offerSlot >= DirectTradeSession.OFFER_SLOTS) return;
            ItemStack inOffer = offer[offerSlot];
            if (inOffer.isEmpty()) return;

            int amount = isRightClick ? 1 : inOffer.getCount();
            int removed = session.removeFromOfferSlot(player.getUuid(), offerSlot, amount);

            if (removed > 0) {
                ItemStack toReturn = inOffer.copyWithCount(removed);
                if (!inv.insertStack(toReturn)) {
                    // Failed to insert - restore to offer
                    session.addToOfferSlot(player.getUuid(), offerSlot, inOffer, removed);
                    player.sendMessage(Text.literal("§c[CogTrade] Envanter dolu — eşya iade edilemedi."), false);
                    return;
                }
                inv.markDirty();  // CRITICAL: Ensure inventory sync
            }

        } else {
            // Move item from inventory slot to offer
            if (invSlot < 0 || invSlot >= 36) return;
            ItemStack inInv = inv.getStack(invSlot);
            if (inInv.isEmpty()) return;

            // Determine amount to move
            int amount = isRightClick ? 1 : inInv.getCount();

            // Resolve target slot
            int targetSlot = offerSlot;
            if (targetSlot == -1) {
                // Auto-pick: try to merge first, then find empty
                targetSlot = -1;
                for (int i = 0; i < DirectTradeSession.OFFER_SLOTS; i++) {
                    if (ItemStack.canCombine(offer[i], inInv) && offer[i].getCount() < offer[i].getMaxCount()) {
                        targetSlot = i;
                        break;
                    }
                }
                if (targetSlot == -1) {
                    for (int i = 0; i < DirectTradeSession.OFFER_SLOTS; i++) {
                        if (offer[i].isEmpty()) {
                            targetSlot = i;
                            break;
                        }
                    }
                }
                if (targetSlot == -1) {
                    player.sendMessage(Text.literal("§c[CogTrade] Teklif alanı dolu!"), false);
                    return;
                }
            }

            if (targetSlot < 0 || targetSlot >= DirectTradeSession.OFFER_SLOTS) return;

            // Add to offer
            int added = session.addToOfferSlot(player.getUuid(), targetSlot, inInv, amount);
            if (added > 0) {
                // Remove from inventory
                inInv.decrement(added);
                inv.markDirty();  // CRITICAL: Ensure inventory sync
            }
        }

        syncSessionToPlayers(server, session);
    }

    /** Legacy overload for backward compatibility during transition */
    public static void handleItemMove(MinecraftServer server,
                                       ServerPlayerEntity player,
                                       String sessionId, int offerSlot, int invSlot) {
        handleItemMove(server, player, sessionId, offerSlot, invSlot, TradeItemMovePacket.CLICK_LEFT);
    }

    /** Sets a player's coin offer; clamps to [0, balance] and resets ready states. */
    public static void handleCoinOffer(MinecraftServer server,
                                        ServerPlayerEntity player,
                                        String sessionId, double amount) {
        DirectTradeSession session = getValidSession(player, sessionId);
        if (session == null) return;

        amount = Math.max(0, Math.min(amount, MAX_COIN_OFFER));
        double balance = PlayerEconomy.getBalance(player.getUuid());
        if (amount > balance) {
            amount = balance;
            player.sendMessage(Text.literal(
                    "§c[CogTrade] Bakiye yetersiz — miktar mevcut bakiyene düzeltildi."), false);
        }

        session.setCoinOffer(player.getUuid(), amount);
        syncSessionToPlayers(server, session);
    }

    /** Toggles the player's ready flag; if both are ready, attempts finalization. */
    public static void handleReadyToggle(MinecraftServer server,
                                          ServerPlayerEntity player,
                                          String sessionId) {
        DirectTradeSession session = getValidSession(player, sessionId);
        if (session == null) return;

        boolean newReady = !session.isReadyFor(player.getUuid());
        session.setReady(player.getUuid(), newReady);
        syncSessionToPlayers(server, session);

        if (session.bothReady()) {
            finalizeSession(server, session);
        }
    }

    /** Player-initiated cancel (from GUI button or /trade cancel). */
    public static void handleCancel(MinecraftServer server,
                                     ServerPlayerEntity player,
                                     String sessionId) {
        DirectTradeSession session = getValidSession(player, sessionId);
        if (session == null) return;
        abortSession(server, session, player.getName().getString() + " takas iptali.");
    }

    // ── Finalization ──────────────────────────────────────────────────────

    private static void finalizeSession(MinecraftServer server, DirectTradeSession session) {
        // Synchronized block to prevent race conditions during finalization
        synchronized (session) {
            // Guard against double-finalization from concurrent ready packets
            if (!session.isActive()) return;

            ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(session.getInitiatorUuid());
            ServerPlayerEntity target    = server.getPlayerManager().getPlayer(session.getTargetUuid());

            // Both players must be online
            if (initiator == null || target == null) {
                abortSession(server, session, "Bir oyuncu bağlantısı koptu.");
                return;
            }

            // Validate session membership is still consistent
            String initSid = playerToSession.get(session.getInitiatorUuid());
            String tgtSid  = playerToSession.get(session.getTargetUuid());
            if (!session.getSessionId().equals(initSid) || !session.getSessionId().equals(tgtSid)) {
                abortSession(server, session, "Oturum tutarsızlığı tespit edildi.");
                return;
            }

            // Re-check ready (another packet might have reset it between bothReady() and here)
            if (!session.bothReady()) return;

        double initCoins = session.getInitiatorCoins();
        double tgtCoins  = session.getTargetCoins();

        // Validate balances
        double initBalance = PlayerEconomy.getBalance(session.getInitiatorUuid());
        double tgtBalance  = PlayerEconomy.getBalance(session.getTargetUuid());

        if (initCoins > initBalance) {
            failSession(server, session, initiator, target,
                    "§c" + session.getInitiatorName() + " bakiyesi yetersiz!");
            return;
        }
        if (tgtCoins > tgtBalance) {
            failSession(server, session, initiator, target,
                    "§c" + session.getTargetName() + " bakiyesi yetersiz!");
            return;
        }

        // Validate inventory space (accurate simulation)
        // Note: offered items are ALREADY removed from their inventories, so check current state.
        if (!hasInventorySpace(initiator, session.getTargetOffer())) {
            failSession(server, session, initiator, target,
                    "§c" + session.getInitiatorName() + " envanteri almak için yeterli yer yok!");
            return;
        }
        if (!hasInventorySpace(target, session.getInitiatorOffer())) {
            failSession(server, session, initiator, target,
                    "§c" + session.getTargetName() + " envanteri almak için yeterli yer yok!");
            return;
        }

        // === COMMIT — mark inactive to prevent any further mutations ===
        session.deactivate();

        // Currency exchange (two separate transfers, with reversal if second fails)
        boolean economyOk = true;

        if (initCoins > 0) {
            if (!PlayerEconomy.transfer(session.getInitiatorUuid(), session.getTargetUuid(), initCoins)) {
                economyOk = false;
            }
        }
        if (economyOk && tgtCoins > 0) {
            if (!PlayerEconomy.transfer(session.getTargetUuid(), session.getInitiatorUuid(), tgtCoins)) {
                // Attempt to reverse the first transfer
                if (initCoins > 0) {
                    PlayerEconomy.transfer(session.getTargetUuid(), session.getInitiatorUuid(), initCoins);
                }
                economyOk = false;
            }
        }

        if (!economyOk) {
            // Return items; economy is (hopefully) restored via reversal above
            returnItemsToOwner(initiator, session.getInitiatorOffer());
            returnItemsToOwner(target,    session.getTargetOffer());
            cleanupSession(session);

            String errMsg = "§c[CogTrade] Para transferi başarısız! Eşyalarınız iade edildi.";
            initiator.sendMessage(Text.literal(errMsg), false);
            target.sendMessage(Text.literal(errMsg), false);
            DirectTradeCancelledPacket.send(initiator, "Para transferi başarısız.");
            DirectTradeCancelledPacket.send(target,    "Para transferi başarısız.");

            CogTrade.LOGGER.error("[CogTrade] Economy transfer failed during finalization: {} ↔ {}",
                    session.getInitiatorName(), session.getTargetName());
            return;
        }

        // Record daily stats
        if (initCoins > 0) {
            PlayerEconomy.recordDailySpent(session.getInitiatorUuid(), initCoins);
            PlayerEconomy.recordDailyEarned(session.getTargetUuid(), initCoins);
        }
        if (tgtCoins > 0) {
            PlayerEconomy.recordDailySpent(session.getTargetUuid(), tgtCoins);
            PlayerEconomy.recordDailyEarned(session.getInitiatorUuid(), tgtCoins);
        }

        // Item exchange: give target's offer to initiator, initiator's offer to target
        giveItemsToPlayer(initiator, session.getTargetOffer());
        giveItemsToPlayer(target,    session.getInitiatorOffer());

        // HUD balance update
        sendBalanceUpdate(initiator);
        sendBalanceUpdate(target);

        // Success packets (close GUI on client)
        TradeCompletePacket.send(initiator, session.getTargetName(),    initCoins, tgtCoins);
        TradeCompletePacket.send(target,    session.getInitiatorName(), tgtCoins,  initCoins);

        // Audit log
        logTradeCompleted(session, initCoins, tgtCoins);

        cleanupSession(session);
        }  // End synchronized block
    }

    // ── Abort helpers ─────────────────────────────────────────────────────

    /** Aborts a session for any reason, safely returning items to owners. */
    public static void abortSession(MinecraftServer server,
                                     DirectTradeSession session, String reason) {
        if (!session.isActive()) return;
        session.deactivate();

        ServerPlayerEntity initiator = server.getPlayerManager().getPlayer(session.getInitiatorUuid());
        ServerPlayerEntity target    = server.getPlayerManager().getPlayer(session.getTargetUuid());

        // Safe item return - use pending queue if player offline
        if (initiator != null) {
            returnItemsToOwner(initiator, session.getInitiatorOffer());
        } else {
            queuePendingReturn(session.getInitiatorUuid(), session.getInitiatorName(),
                    session.getInitiatorOffer(), "Takas iptal: " + reason);
        }

        if (target != null) {
            returnItemsToOwner(target, session.getTargetOffer());
        } else {
            queuePendingReturn(session.getTargetUuid(), session.getTargetName(),
                    session.getTargetOffer(), "Takas iptal: " + reason);
        }

        cleanupSession(session);

        String msg = "§e[CogTrade] §7Takas iptal edildi: " + reason;
        if (initiator != null) {
            initiator.sendMessage(Text.literal(msg), false);
            DirectTradeCancelledPacket.send(initiator, reason);
        }
        if (target != null) {
            target.sendMessage(Text.literal(msg), false);
            DirectTradeCancelledPacket.send(target, reason);
        }

        CogTrade.LOGGER.info("[CogTrade] Takas iptal edildi: {} ↔ {} — {}",
                session.getInitiatorName(), session.getTargetName(), reason);
    }

    private static void failSession(MinecraftServer server,
                                     DirectTradeSession session,
                                     ServerPlayerEntity initiator,
                                     ServerPlayerEntity target,
                                     String reason) {
        session.deactivate();

        returnItemsToOwner(initiator, session.getInitiatorOffer());
        returnItemsToOwner(target,    session.getTargetOffer());
        cleanupSession(session);

        String msg = reason + " §7Eşyalarınız iade edildi.";
        initiator.sendMessage(Text.literal(msg), false);
        target.sendMessage(Text.literal(msg), false);
        DirectTradeCancelledPacket.send(initiator, reason);
        DirectTradeCancelledPacket.send(target,    reason);

        CogTrade.LOGGER.info("[CogTrade] Takas başarısız: {} ↔ {} — {}",
                session.getInitiatorName(), session.getTargetName(), reason);
    }

    // ── Disconnect / death handling ───────────────────────────────────────

    /** Call from ServerPlayConnectionEvents.DISCONNECT for the disconnecting player. */
    public static void handleDisconnect(MinecraftServer server, UUID playerUuid) {
        // Cancel any pending request where this player is the initiator
        for (Iterator<Map.Entry<UUID, PendingRequest>> it = pendingRequests.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, PendingRequest> entry = it.next();
            PendingRequest pr = entry.getValue();
            if (pr.initiatorUuid().equals(playerUuid)) {
                it.remove();
                initiatorToTarget.remove(playerUuid);
                pr.timeoutFuture().cancel(false);
                ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(entry.getKey());
                if (targetPlayer != null) {
                    targetPlayer.sendMessage(
                            Text.literal("§e[CogTrade] §7Takas isteği gönderen çevrimdışı oldu."), false);
                }
            }
        }

        // Cancel any pending request where this player is the target
        PendingRequest targetedPr = pendingRequests.remove(playerUuid);
        if (targetedPr != null) {
            initiatorToTarget.remove(targetedPr.initiatorUuid());
            targetedPr.timeoutFuture().cancel(false);
            ServerPlayerEntity initiatorPlayer = server.getPlayerManager().getPlayer(targetedPr.initiatorUuid());
            if (initiatorPlayer != null) {
                initiatorPlayer.sendMessage(
                        Text.literal("§e[CogTrade] §7Takas isteği gönderilen oyuncu çevrimdışı oldu."), false);
            }
        }

        // Abort active session
        String sid = playerToSession.get(playerUuid);
        if (sid != null) {
            DirectTradeSession session = sessions.get(sid);
            if (session != null) {
                abortSession(server, session, "Bir oyuncu bağlantısı koptu.");
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private static void syncSessionToPlayers(MinecraftServer server, DirectTradeSession session) {
        ServerPlayerEntity init = server.getPlayerManager().getPlayer(session.getInitiatorUuid());
        ServerPlayerEntity tgt  = server.getPlayerManager().getPlayer(session.getTargetUuid());
        if (init != null) TradeStateUpdatePacket.send(init, session, true);
        if (tgt  != null) TradeStateUpdatePacket.send(tgt,  session, false);
    }

    /** Simulates inserting all non-empty items into a copy of the player's inventory. */
    private static boolean hasInventorySpace(ServerPlayerEntity player, ItemStack[] incoming) {
        List<ItemStack> sim = new ArrayList<>(36);
        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) sim.add(inv.getStack(i).copy());

        for (ItemStack item : incoming) {
            if (item.isEmpty()) continue;
            ItemStack rem = item.copy();

            // Try to stack onto existing matching stacks
            for (int i = 0; i < sim.size() && !rem.isEmpty(); i++) {
                ItemStack slot = sim.get(i);
                if (!slot.isEmpty() && ItemStack.canCombine(slot, rem)) {
                    int canAdd = slot.getMaxCount() - slot.getCount();
                    if (canAdd > 0) {
                        int add = Math.min(canAdd, rem.getCount());
                        slot.increment(add);
                        rem.decrement(add);
                    }
                }
            }
            // Find an empty slot
            if (!rem.isEmpty()) {
                boolean placed = false;
                for (int i = 0; i < sim.size(); i++) {
                    if (sim.get(i).isEmpty()) {
                        sim.set(i, rem.copy());
                        placed = true;
                        break;
                    }
                }
                if (!placed) return false;
            }
        }
        return true;
    }

    private static void returnItemsToOwner(ServerPlayerEntity player, ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            if (!items[i].isEmpty()) {
                ItemStack copy = items[i].copy();
                if (!player.getInventory().insertStack(copy)) {
                    player.dropItem(copy, false);
                }
                items[i] = ItemStack.EMPTY;
            }
        }
    }

    private static void giveItemsToPlayer(ServerPlayerEntity player, ItemStack[] items) {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                ItemStack copy = s.copy();
                if (!player.getInventory().insertStack(copy)) {
                    player.dropItem(copy, false);
                }
            }
        }
    }

    private static void cleanupSession(DirectTradeSession session) {
        sessions.remove(session.getSessionId());
        playerToSession.remove(session.getInitiatorUuid());
        playerToSession.remove(session.getTargetUuid());
    }

    private static void sendBalanceUpdate(ServerPlayerEntity player) {
        double balance = PlayerEconomy.getBalance(player.getUuid());
        double earned  = PlayerEconomy.getDailyEarned(player.getUuid());
        double spent   = PlayerEconomy.getDailySpent(player.getUuid());
        BalanceUpdatePacket.send(player, balance, earned, spent);
    }

    private static void logTradeCompleted(DirectTradeSession session, double initCoins, double tgtCoins) {
        int initItems = 0, tgtItems = 0;
        for (ItemStack s : session.getInitiatorOffer()) if (!s.isEmpty()) initItems++;
        for (ItemStack s : session.getTargetOffer())    if (!s.isEmpty()) tgtItems++;

        CogTrade.LOGGER.info("[CogTrade] Takas tamamlandı: {} ↔ {} | ⬡{}←→⬡{} | {}->{} eşya, {}->{} eşya",
                session.getInitiatorName(), session.getTargetName(),
                String.format("%.0f", initCoins), String.format("%.0f", tgtCoins),
                initItems, tgtItems, tgtItems, initItems);

        if (DatabaseManager.getConnection() == null) return;
        try {
            String sql = """
                INSERT INTO direct_trades
                    (session_id, initiator_uuid, initiator_name, target_uuid, target_name,
                     initiator_coins, target_coins, initiator_items, target_items,
                     status, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?)
            """;
            PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql);
            ps.setString(1, session.getSessionId());
            ps.setString(2, session.getInitiatorUuid().toString());
            ps.setString(3, session.getInitiatorName());
            ps.setString(4, session.getTargetUuid().toString());
            ps.setString(5, session.getTargetName());
            ps.setDouble(6, initCoins);
            ps.setDouble(7, tgtCoins);
            ps.setInt(8, initItems);
            ps.setInt(9, tgtItems);
            ps.setLong(10, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            CogTrade.LOGGER.warn("[CogTrade] Takas kaydı DB'ye yazılamadı: {}", e.getMessage());
        }
    }

    // ── Public query helpers ──────────────────────────────────────────────

    public static DirectTradeSession getValidSession(ServerPlayerEntity player, String sessionId) {
        if (sessionId == null) return null;
        DirectTradeSession session = sessions.get(sessionId);
        if (session == null || !session.isActive()) return null;
        if (!session.involves(player.getUuid())) return null;
        return session;
    }

    public static DirectTradeSession getSessionForPlayer(UUID playerUuid) {
        String sid = playerToSession.get(playerUuid);
        return sid != null ? sessions.get(sid) : null;
    }

    public static boolean isInSession(UUID playerUuid) {
        return playerToSession.containsKey(playerUuid);
    }

    public static boolean hasPendingRequest(UUID targetUuid) {
        return pendingRequests.containsKey(targetUuid);
    }

    // ── Pending item return system (safe offline recovery) ────────────────

    /**
     * Queues items for return to an offline player.
     * Items will be delivered when the player logs back in.
     */
    private static void queuePendingReturn(UUID ownerUuid, String ownerName,
                                            ItemStack[] items, String reason) {
        PendingItemReturn pending = new PendingItemReturn(ownerUuid, ownerName, items, reason);
        if (pending.isEmpty()) return;

        pendingReturns.computeIfAbsent(ownerUuid, k -> new ArrayList<>()).add(pending);

        CogTrade.LOGGER.info("[CogTrade] {} için {} eşya beklemede — oyuncu offline olduğunda güvenli iade.",
                ownerName, pending.getItems().size());
    }

    /**
     * Called when a player joins to return any pending items from
     * trades that were cancelled while they were offline.
     */
    public static void handlePlayerJoin(MinecraftServer server, ServerPlayerEntity player) {
        List<PendingItemReturn> returns = pendingReturns.remove(player.getUuid());
        if (returns == null || returns.isEmpty()) return;

        int totalItems = 0;
        for (PendingItemReturn pending : returns) {
            for (ItemStack stack : pending.getItems()) {
                if (!stack.isEmpty()) {
                    if (!player.getInventory().insertStack(stack.copy())) {
                        // Last resort: drop at player feet if inventory completely full
                        player.dropItem(stack.copy(), false);
                    }
                    totalItems++;
                }
            }
        }

        if (totalItems > 0) {
            player.sendMessage(Text.literal(
                    "§e[CogTrade] §7Offline olduğun sırada iptal edilen takaslardan §a" +
                            totalItems + " eşya §7iade edildi."), false);
            CogTrade.LOGGER.info("[CogTrade] {} için {} bekleyen eşya iade edildi.",
                    player.getName().getString(), totalItems);
        }
    }
}
