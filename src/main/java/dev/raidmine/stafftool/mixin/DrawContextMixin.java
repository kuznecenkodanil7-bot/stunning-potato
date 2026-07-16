package dev.raidmine.stafftool.mixin;

import dev.raidmine.stafftool.chat.ChatTextProcessor;
import dev.raidmine.stafftool.ui.UiTheme;
import dev.raidmine.stafftool.util.ChatRenderTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Shadow @Final private int mouseX;
    @Shadow @Final private int mouseY;
    private static final ThreadLocal<Boolean> RMTOOLS_OVERLAY = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
            at = @At("HEAD")
    )
    private void rmtools$trackAndHighlightAuthor(TextRenderer renderer, OrderedText text,
                                                 int x, int y, int color, boolean shadow,
                                                 CallbackInfo ci) {
        if (RMTOOLS_OVERLAY.get()) return;
        DrawContext context = (DrawContext) (Object) this;
        ChatRenderTracker.observeAndFindHover(context, renderer, text, x, y, mouseX, mouseY).ifPresent(local -> {
            context.setCursor(net.minecraft.client.gui.cursor.StandardCursors.POINTING_HAND);
            int accent = UiTheme.accent();
            // Draw in the same local coordinate system as the glyphs. No glow and no second transform.
            UiTheme.roundedRectExact(context, local.x(), local.y(), local.width(), local.height(), 3,
                    UiTheme.withAlpha(accent, 205));
            UiTheme.roundedRectExact(context, local.x() + 1, local.y() + 1,
                    Math.max(1, local.width() - 2), Math.max(1, local.height() - 2), 2,
                    UiTheme.withAlpha(accent, 72));
        });
    }

    @Inject(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
            at = @At("TAIL")
    )
    private void rmtools$renderForbiddenGradient(TextRenderer renderer, OrderedText text,
                                                  int x, int y, int color, boolean shadow,
                                                  CallbackInfo ci) {
        if (RMTOOLS_OVERLAY.get()) return;
        if (!ChatRenderTracker.isChatLikeScreen(MinecraftClient.getInstance().currentScreen)) return;

        StringBuilder raw = new StringBuilder();
        List<GlyphPiece> pieces = new ArrayList<>();
        final float[] cursor = {x};
        text.accept((index, style, codePoint) -> {
            int start = raw.length();
            raw.appendCodePoint(codePoint);
            float width = renderer.getTextHandler().getWidth(OrderedText.styled(codePoint, style));
            pieces.add(new GlyphPiece(start, raw.length(), codePoint, style, cursor[0]));
            cursor[0] += width;
            return true;
        });

        ChatTextProcessor.MessageInfo info = ChatTextProcessor.inspectRenderedLine(raw.toString());
        if (info.forbiddenRanges().isEmpty()) return;

        RMTOOLS_OVERLAY.set(true);
        try {
            DrawContext context = (DrawContext) (Object) this;
            for (GlyphPiece piece : pieces) {
                ChatTextProcessor.ForbiddenRange range = containing(info.forbiddenRanges(), piece.start());
                if (range == null) continue;
                float t = range.end() <= range.start() + 1 ? 0F
                        : Math.max(0F, Math.min(1F,
                        (piece.start() - range.start()) / (float) (range.end() - range.start() - 1)));
                int gradient = UiTheme.blend(ChatTextProcessor.FORBIDDEN_START, ChatTextProcessor.FORBIDDEN_END, t);
                Style styled = piece.style().withColor(gradient).withBold(true);
                context.drawText(renderer, OrderedText.styled(piece.codePoint(), styled),
                        Math.round(piece.x()), y, 0xFFFFFFFF, false);
            }
        } finally {
            RMTOOLS_OVERLAY.set(false);
        }
    }

    private static ChatTextProcessor.ForbiddenRange containing(
            List<ChatTextProcessor.ForbiddenRange> ranges, int position) {
        for (ChatTextProcessor.ForbiddenRange range : ranges) {
            if (position >= range.start() && position < range.end()) return range;
        }
        return null;
    }

    private record GlyphPiece(int start, int end, int codePoint, Style style, float x) { }
}
