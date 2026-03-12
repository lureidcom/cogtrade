package com.cogtrade.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class CogTradeHud {

    // Animasyon için
    private static double displayedBalance = -1;
    private static double animationProgress = 1.0;
    private static double previousBalance = 0;
    private static final double ANIMATION_SPEED = 0.15;

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            if (client.options.debugEnabled) return;
            if (!ClientEconomyData.isInitialized()) return;

            double targetBalance = ClientEconomyData.getBalance();
            double dailyEarned = ClientEconomyData.getDailyEarned();
            double dailySpent = ClientEconomyData.getDailySpent();
            double dailyNet = dailyEarned - dailySpent;

            // Animasyon — bakiye değişince smooth geçiş
            if (displayedBalance < 0) displayedBalance = targetBalance;
            if (Math.abs(displayedBalance - targetBalance) > 0.5) {
                displayedBalance += (targetBalance - displayedBalance) * ANIMATION_SPEED;
            } else {
                displayedBalance = targetBalance;
            }

            TextRenderer font = client.textRenderer;

            // Metinler
            String balanceText = "\u29C1 " + formatNumber((long) displayedBalance);
            String dailyText = (dailyNet >= 0 ? "+" : "") + formatNumber((long) dailyNet) + " bugün";

            int balanceWidth = font.getWidth(balanceText);
            int dailyWidth = font.getWidth(dailyText);
            int panelWidth = Math.max(balanceWidth, dailyWidth) + 2;

            int x = 6;
            int y = 6;

            // Gölge efekti — arka plan yok, sadece text shadow
            // Bakiye — beyaz, büyük
            drawContext.drawTextWithShadow(font, balanceText, x, y, 0xFFAA00);

            // Günlük net — yeşil/kırmızı, küçük
            int dailyColor = dailyNet >= 0 ? 0x55FF55 : 0xFF5555;
            if (dailyNet == 0) dailyColor = 0xAAAAAA;
            drawContext.drawTextWithShadow(font, dailyText, x, y + 11, dailyColor);
        });
    }

    private static String formatNumber(long number) {
        if (number >= 1_000_000) return String.format("%.1fM", number / 1_000_000.0);
        if (number >= 1_000) return String.format("%.1fK", number / 1_000.0);
        return String.valueOf(number);
    }
}