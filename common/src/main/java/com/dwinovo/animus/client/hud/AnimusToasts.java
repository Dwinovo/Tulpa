package com.dwinovo.animus.client.hud;

import com.dwinovo.animus.agent.llm.ConvoState;
import com.dwinovo.animus.agent.provider.AssistantTurn;
import com.dwinovo.animus.agent.provider.LlmToolCall;
import com.dwinovo.animus.client.agent.AgentLoopRegistry;
import com.dwinovo.animus.client.agent.AnimusRoster;
import com.dwinovo.animus.client.screen.AnimusScreen;
import com.dwinovo.animus.client.screen.RosterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Left-side HUD activity cards. ONE card per Animus: new reactions append a line
 * to that companion's existing card. Each line carries its OWN lifetime — an old
 * line vanishes while newer ones linger, and the card itself stays until ALL its
 * lines have expired. Shown only when not watching a panel. Brief by design.
 */
public final class AnimusToasts {

    private static final int W = 172;
    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int HEADER_H = 13;
    private static final int LINE_H = 11;
    private static final int PAD = 4;
    private static final int MAX_LINES = 4;
    private static final int MAX_CARDS = 4;
    private static final long LINE_LIFE_MS = 5000;
    private static final long SLIDE_MS = 220;

    private static final int BG = 0xF00E1116;
    private static final int BORDER = 0xFF2B313B;
    private static final int ACCENT = 0xFF4F8CC9;
    private static final int NAME = 0xFFE6E8EB;
    private static final int REPLY = 0xFF6FC3FF;
    private static final int ACTION = 0xFF7FD4C8;

    private static final Map<UUID, Integer> SEEN = new HashMap<>();
    /** Insertion-ordered so cards stack stably. */
    private static final Map<UUID, Card> CARDS = new LinkedHashMap<>();

    private AnimusToasts() {}

    private record Line(String text, int color, long bornMs) {}

    private static final class Card {
        final String name;
        final long bornMs;
        final Deque<Line> lines = new ArrayDeque<>();   // oldest first
        Card(String name, long bornMs) { this.name = name; this.bornMs = bornMs; }
    }

    /** Drop expired lines / empty cards, then poll loops for new assistant turns. */
    public static void tick() {
        long now = System.currentTimeMillis();
        CARDS.values().forEach(c -> c.lines.removeIf(l -> now - l.bornMs() > LINE_LIFE_MS));
        CARDS.values().removeIf(c -> c.lines.isEmpty());

        for (AnimusRoster.Entry entry : AnimusRoster.instance().entries()) {
            UUID uuid = entry.uuid();
            AgentLoopRegistry.get(uuid).ifPresent(loop -> {
                List<ConvoState.Msg> snap = loop.convo().snapshot();
                int prev = SEEN.getOrDefault(uuid, -1);
                if (prev < 0) { SEEN.put(uuid, snap.size()); return; }   // skip backlog on first sight
                for (int i = prev; i < snap.size(); i++) {
                    if (snap.get(i) instanceof ConvoState.Msg.Assistant a) {
                        addLine(uuid, entry.name(), a.turn(), now);
                    }
                }
                SEEN.put(uuid, snap.size());
            });
        }
    }

    /** Append one line for an assistant turn: spoken reply if any, else the action it started. */
    private static void addLine(UUID uuid, String name, AssistantTurn turn, long now) {
        Line line;
        if (turn.content() != null && !turn.content().isBlank()) {
            line = new Line(snip(turn.content(), 36), REPLY, now);
        } else if (!turn.toolCalls().isEmpty()) {
            LlmToolCall tc = turn.toolCalls().get(turn.toolCalls().size() - 1);
            String extra = turn.toolCalls().size() > 1 ? " +" + (turn.toolCalls().size() - 1) : "";
            line = new Line("▸ " + tc.name() + extra, ACTION, now);
        } else {
            return;
        }
        Card card = CARDS.computeIfAbsent(uuid, u -> {
            while (CARDS.size() >= MAX_CARDS) {       // make room — drop the oldest card
                UUID oldest = CARDS.keySet().iterator().next();
                CARDS.remove(oldest);
            }
            return new Card(name, now);
        });
        card.lines.addLast(line);
        while (card.lines.size() > MAX_LINES) card.lines.removeFirst();
    }

    public static void render(GuiGraphicsExtractor g) {
        if (CARDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AnimusScreen || mc.screen instanceof RosterScreen) return;

        Font font = mc.font;
        long now = System.currentTimeMillis();
        int y = MARGIN;
        for (Card card : new ArrayList<>(CARDS.values())) {
            if (card.lines.isEmpty()) continue;
            int h = HEADER_H + card.lines.size() * LINE_H + PAD;
            int off = slideIn(now - card.bornMs);          // W → 0 from off-screen left
            int x = MARGIN - off;

            g.fill(x, y, x + W, y + h, BG);
            g.outline(x, y, W, h, BORDER);
            g.fill(x, y, x + 2, y + h, ACCENT);            // left accent bar
            g.text(font, Component.literal(card.name), x + 7, y + 3, NAME);
            int ly = y + HEADER_H;
            for (Line line : card.lines) {
                g.text(font, Component.literal(line.text()), x + 7, ly, line.color());
                ly += LINE_H;
            }
            y += h + GAP;
        }
    }

    private static int slideIn(long age) {
        if (age >= SLIDE_MS) return 0;
        float p = 1f - (float) age / SLIDE_MS;             // 1 → 0
        return (int) (W * p * p);
    }

    private static String snip(String s, int max) {
        String c = s.replaceAll("\\s+", " ").trim();
        return c.length() > max ? c.substring(0, max) + "…" : c;
    }

    public static void clear() {
        SEEN.clear();
        CARDS.clear();
    }
}
