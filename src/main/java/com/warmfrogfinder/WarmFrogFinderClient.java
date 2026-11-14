package com.warmfrogfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.FrogVariant;
import net.minecraft.entity.passive.FrogVariants;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WarmFrogFinderClient implements ClientModInitializer {

    private static final int HUD_X = 8;
    private static final int HUD_Y = 8;
    private static final int PADDING = 4;
    private static final int LINE_SPACING = 2;
    private static final int MAX_DISPLAYED_FROGS = 5;

    private static final int BG_COLOR = 0x80000000;
    private static final int HEADER_COLOR = 0xFFFF55FF;
    private static final int ENTRY_COLOR = 0xFFFFFFFF;
    private static final int MORE_COLOR = 0xFFAAAAAA;

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    private void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || client.options == null) {
            return;
        }

        // Respect F1 (Hide HUD)
        if (client.options.hudHidden) {
            return;
        }

        List<DetectedFrog> warmFrogs = new ArrayList<>();
        for (Entity entity : client.world.getEntities()) {
            if (entity instanceof FrogEntity frog && frog.isAlive() && !frog.isRemoved()) {
                if (isWarmFrog(frog)) {
                    double sqDist = frog.squaredDistanceTo(client.player);
                    int distMeters = (int) Math.round(Math.sqrt(sqDist));
                    warmFrogs.add(new DetectedFrog(
                            frog.getBlockX(),
                            frog.getBlockY(),
                            frog.getBlockZ(),
                            distMeters,
                            sqDist
                    ));
                }
            }
        }

        // Strict Render-Distance Auto-Toggle: render nothing when 0 Warm Frogs are loaded
        if (warmFrogs.isEmpty()) {
            return;
        }

        // Sort closest to farthest
        warmFrogs.sort(Comparator.comparingDouble(DetectedFrog::squaredDistance));

        TextRenderer textRenderer = client.textRenderer;
        List<HudLine> lines = new ArrayList<>();

        String headerText = "Warm Frogs Found: " + warmFrogs.size();
        lines.add(new HudLine(headerText, HEADER_COLOR));

        int displayLimit = Math.min(warmFrogs.size(), MAX_DISPLAYED_FROGS);
        for (int i = 0; i < displayLimit; i++) {
            DetectedFrog df = warmFrogs.get(i);
            String lineText = String.format(
                    "#%d: %d, %d, %d (%dm)",
                    i + 1,
                    df.x(),
                    df.y(),
                    df.z(),
                    df.distanceMeters()
            );
            lines.add(new HudLine(lineText, ENTRY_COLOR));
        }

        if (warmFrogs.size() > MAX_DISPLAYED_FROGS) {
            int remaining = warmFrogs.size() - MAX_DISPLAYED_FROGS;
            lines.add(new HudLine("+" + remaining + " more", MORE_COLOR));
        }

        int maxTextWidth = 0;
        for (HudLine line : lines) {
            int width = textRenderer.getWidth(line.text());
            if (width > maxTextWidth) {
                maxTextWidth = width;
            }
        }

        int lineHeight = textRenderer.fontHeight;
        int totalTextHeight = (lines.size() * lineHeight) + ((lines.size() - 1) * LINE_SPACING);
        int boxWidth = maxTextWidth + (PADDING * 2);
        int boxHeight = totalTextHeight + (PADDING * 2);

        drawContext.fill(HUD_X, HUD_Y, HUD_X + boxWidth, HUD_Y + boxHeight, BG_COLOR);

        int currentY = HUD_Y + PADDING;
        for (HudLine line : lines) {
            drawContext.drawTextWithShadow(textRenderer, line.text(), HUD_X + PADDING, currentY, line.color());
            currentY += lineHeight + LINE_SPACING;
        }
    }

    private static boolean isWarmFrog(FrogEntity frog) {
        RegistryEntry<FrogVariant> variantEntry = frog.get(DataComponentTypes.FROG_VARIANT);
        if (variantEntry == null) {
            return false;
        }
        if (variantEntry.matchesKey(FrogVariants.WARM)) {
            return true;
        }
        String idStr = variantEntry.getIdAsString();
        return "minecraft:warm".equals(idStr) || "warm".equals(idStr);
    }

    private record DetectedFrog(int x, int y, int z, int distanceMeters, double squaredDistance) {}

    private record HudLine(String text, int color) {}
}
