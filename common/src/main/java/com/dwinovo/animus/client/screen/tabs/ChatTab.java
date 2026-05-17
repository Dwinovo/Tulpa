package com.dwinovo.animus.client.screen.tabs;

import com.dwinovo.animus.agent.llm.ConvoState;
import com.dwinovo.animus.agent.provider.AssistantTurn;
import com.dwinovo.animus.agent.provider.LlmToolCall;
import com.dwinovo.animus.client.agent.AgentLoopRegistry;
import com.dwinovo.animus.client.screen.AnimusManagerScreen;
import com.dwinovo.animus.client.screen.SimpleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat tab — talk to the PlayerAgent without touching the chat command bar.
 *
 * <h2>Layout</h2>
 * <pre>
 *  ┌──── content area ─────────────────────────────┐
 *  │  ▶ user prompt                                │  scrollable
 *  │  ● animus reply                               │  history
 *  │  [tool: assign_task(1, "...")]                │  view
 *  │  [report unit 3 ✓ done]                       │
 *  │  ...                                          │
 *  │                                               │
 *  ├───────────────────────────────────────────────┤
 *  │ [EditBox.....................]  [Send]        │  input row
 *  └───────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Auto-scroll</h2>
 * Conversation history scrolls automatically to the most recent message.
 * Manual scrollback can be added later (Phase-2: mouse-wheel + scroll bar).
 */
public final class ChatTab extends Tab {

    private static final int INPUT_HEIGHT = 18;
    private static final int SEND_BUTTON_WIDTH = 50;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;

    /** Auto-truncate per-message rendering to keep history readable. */
    private static final int MAX_LINES_PER_MESSAGE = 8;
    private static final int MAX_TOTAL_LINES = 80;

    private EditBox inputBox;
    private SimpleButton sendButton;
    private int contentX, contentY, contentWidth, contentHeight;
    private int historyX, historyY, historyWidth, historyHeight;

    public ChatTab(AnimusManagerScreen parent) {
        super(parent);
    }

    @Override
    public Component title() {
        return Component.literal("Chat");
    }

    @Override
    public void onEnter(int x, int y, int width, int height) {
        this.contentX = x;
        this.contentY = y;
        this.contentWidth = width;
        this.contentHeight = height;

        int inputRowY = y + height - INPUT_HEIGHT - PADDING;
        int inputWidth = width - SEND_BUTTON_WIDTH - PADDING * 3;
        Font font = Minecraft.getInstance().font;

        this.inputBox = new EditBox(font, x + PADDING, inputRowY, inputWidth, INPUT_HEIGHT,
                Component.literal("animus.chat.input"));
        this.inputBox.setMaxLength(2048);
        this.inputBox.setHint(Component.literal("Type a message for PlayerAgent..."));
        parent.registerTabWidget(this.inputBox);

        this.sendButton = new SimpleButton(
                x + width - SEND_BUTTON_WIDTH - PADDING, inputRowY,
                SEND_BUTTON_WIDTH, INPUT_HEIGHT,
                Component.literal("Send"),
                b -> sendCurrentInput());
        parent.registerTabWidget(this.sendButton);

        this.historyX = x + PADDING;
        this.historyY = y + PADDING;
        this.historyWidth = width - PADDING * 2;
        this.historyHeight = inputRowY - y - PADDING * 2;
    }

    private void sendCurrentInput() {
        String text = inputBox.getValue().trim();
        if (text.isEmpty()) return;
        AgentLoopRegistry.playerAgent().submitPrompt(text);
        inputBox.setValue("");
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Font font = Minecraft.getInstance().font;

        // Build the line list (newest at bottom) — bounded.
        List<RenderedLine> lines = buildLines(font);
        int totalHeight = lines.size() * LINE_HEIGHT;

        // Top of the viewport: align to bottom (auto-scroll to newest).
        int firstLineY;
        if (totalHeight <= historyHeight) {
            firstLineY = historyY;
        } else {
            firstLineY = historyY + historyHeight - totalHeight;
        }

        g.enableScissor(historyX, historyY, historyX + historyWidth, historyY + historyHeight);
        int y = firstLineY;
        for (RenderedLine line : lines) {
            if (y + LINE_HEIGHT >= historyY) {
                g.text(font, line.text, historyX, y, line.color);
            }
            y += LINE_HEIGHT;
            if (y > historyY + historyHeight) break;
        }
        g.disableScissor();

        // Subtle separator above the input row.
        int separatorY = historyY + historyHeight + 1;
        g.fill(historyX, separatorY, historyX + historyWidth, separatorY + 1, 0x44FFFFFF);
    }

    private List<RenderedLine> buildLines(Font font) {
        ConvoState convo = AgentLoopRegistry.playerAgent().convo();
        List<RenderedLine> out = new ArrayList<>();
        for (ConvoState.Msg msg : convo.snapshot()) {
            switch (msg) {
                case ConvoState.Msg.User u -> appendWrapped(out, font, "▶ " + u.content(), 0xFFFFFFFF, "▶ ");
                case ConvoState.Msg.Assistant a -> renderAssistant(out, font, a.turn());
                case ConvoState.Msg.Tool t -> appendWrapped(out, font, "  ← " + summarise(t.content()),
                        0xFF808080, "    ");
            }
            if (out.size() > MAX_TOTAL_LINES) {
                // Drop oldest, keep the tail. Mark truncation.
                int over = out.size() - MAX_TOTAL_LINES;
                for (int i = 0; i < over; i++) out.remove(0);
                out.add(0, new RenderedLine(
                        FormattedCharSequence.forward("[... older messages truncated ...]",
                                net.minecraft.network.chat.Style.EMPTY),
                        0xFF505050));
            }
        }
        if (out.isEmpty()) {
            out.add(new RenderedLine(
                    FormattedCharSequence.forward("Type below to talk to PlayerAgent.",
                            net.minecraft.network.chat.Style.EMPTY),
                    0xFF606060));
        }
        return out;
    }

    private void renderAssistant(List<RenderedLine> out, Font font, AssistantTurn turn) {
        String content = turn.content();
        if (content != null && !content.isBlank()) {
            appendWrapped(out, font, "● " + content, 0xFFFFE082, "● ");
        }
        for (LlmToolCall tc : turn.toolCalls()) {
            String args = tc.arguments();
            if (args == null) args = "";
            if (args.length() > 80) args = args.substring(0, 80) + "...";
            String line = "  → " + tc.name() + "(" + args + ")";
            appendWrapped(out, font, line, 0xFF80CBC4, "    ");
        }
    }

    private void appendWrapped(List<RenderedLine> out, Font font,
                                String text, int color, String continuationPrefix) {
        // Use vanilla split for wrapping; iterate FormattedCharSequence list.
        List<FormattedCharSequence> wrapped = font.split(Component.literal(text), historyWidth - 4);
        int emitted = 0;
        for (FormattedCharSequence seq : wrapped) {
            if (emitted >= MAX_LINES_PER_MESSAGE) {
                out.add(new RenderedLine(
                        FormattedCharSequence.forward(continuationPrefix + "[...truncated...]",
                                net.minecraft.network.chat.Style.EMPTY),
                        color));
                break;
            }
            out.add(new RenderedLine(seq, color));
            emitted++;
        }
    }

    private static String summarise(String raw) {
        if (raw == null) return "";
        String s = raw.replace('\n', ' ').replace('\r', ' ');
        if (s.length() > 120) s = s.substring(0, 120) + "...";
        return s;
    }

    @Override
    public void tick() {
        // EditBox cursor blink etc. is now driven internally in MC 26.1.2 — no manual tick needed.
    }

    private record RenderedLine(FormattedCharSequence text, int color) {}
}
