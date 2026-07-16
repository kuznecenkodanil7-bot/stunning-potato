package dev.raidmine.stafftool.util;

import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolves only the author token from the coordinates produced by ChatHud itself. */
public final class NicknameHitConsumer implements DrawnTextConsumer {
    private static final int LINE_HEIGHT = 9;
    private static final float HORIZONTAL_TOLERANCE = 3F;
    private static final float VERTICAL_TOLERANCE = 4F;
    private static final Transformation DEFAULT_TRANSFORMATION = new Transformation(new Matrix3x2f());

    private final TextRenderer textRenderer;
    private final int clickX;
    private final int clickY;
    private Transformation transformation = DEFAULT_TRANSFORMATION;
    private Hit hit;
    private float bestScore = Float.MAX_VALUE;

    public NicknameHitConsumer(TextRenderer textRenderer, int clickX, int clickY) {
        this.textRenderer = textRenderer;
        this.clickX = clickX;
        this.clickY = clickY;
    }

    @Override
    public Transformation getTransformation() {
        return transformation;
    }

    @Override
    public void setTransformation(Transformation transformation) {
        this.transformation = transformation;
    }

    @Override
    public void text(Alignment alignment, int x, int y, Transformation transformation, OrderedText text) {
        ScreenRect scissor = transformation.scissor();
        if (scissor != null && !expandedContains(scissor, clickX, clickY, 5)) return;

        List<Piece> pieces = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int adjustedX = alignment.getAdjustedX(x, textRenderer, text);
        final float[] cursor = {adjustedX};
        text.accept((index, style, codePoint) -> {
            String character = new String(Character.toChars(codePoint));
            int start = line.length();
            line.append(character);
            float advance = textRenderer.getTextHandler().getWidth(OrderedText.styled(codePoint, style));
            pieces.add(new Piece(start, line.length(), cursor[0], cursor[0] + advance, style));
            cursor[0] += advance;
            return true;
        });

        Optional<NicknameResolver.AuthorMatch> match = NicknameResolver.resolveAuthorMatch(line.toString());
        if (match.isEmpty()) return;
        NicknameResolver.AuthorMatch author = match.get();

        float tokenLeft = Float.MAX_VALUE;
        float tokenRight = Float.MIN_VALUE;
        for (Piece piece : pieces) {
            if (piece.end() > author.start() && piece.start() < author.end()) {
                tokenLeft = Math.min(tokenLeft, piece.left());
                tokenRight = Math.max(tokenRight, piece.right());
            }
        }
        if (tokenLeft == Float.MAX_VALUE || tokenRight <= tokenLeft) return;

        Matrix3x2f pose = new Matrix3x2f(transformation.pose());
        Vector2f topLeft = pose.transformPosition(new Vector2f(tokenLeft, y));
        Vector2f bottomRight = pose.transformPosition(new Vector2f(tokenRight, y + LINE_HEIGHT));
        float left = Math.min(topLeft.x, bottomRight.x);
        float right = Math.max(topLeft.x, bottomRight.x);
        float top = Math.min(topLeft.y, bottomRight.y);
        float bottom = Math.max(topLeft.y, bottomRight.y);

        float dx = clickX < left ? left - clickX : clickX > right ? clickX - right : 0F;
        float dy = clickY < top ? top - clickY : clickY > bottom ? clickY - bottom : 0F;
        if (dx > HORIZONTAL_TOLERANCE || dy > VERTICAL_TOLERANCE) return;
        float score = dx * dx + dy * dy;
        if (score >= bestScore) return;
        bestScore = score;

        hit = new Hit(author.name(), line.substring(author.start(), author.end()),
                Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
    }

    @Override
    public void marqueedText(Text text, int x, int left, int right, int top, int bottom,
                             Transformation transformation) {
        int width = textRenderer.getWidth(text);
        int y = (top + bottom - LINE_HEIGHT) / 2 + 1;
        int adjustedX = Math.max(left, Math.min(right - width, x - width / 2));
        text(Alignment.LEFT, adjustedX, y, transformation, text.asOrderedText());
    }

    public Optional<Hit> hit() {
        return Optional.ofNullable(hit);
    }

    private static boolean expandedContains(ScreenRect rect, int x, int y, int margin) {
        return x >= rect.getLeft() - margin && x <= rect.getRight() + margin
                && y >= rect.getTop() - margin && y <= rect.getBottom() + margin;
    }

    private record Piece(int start, int end, float left, float right, Style style) {
    }

    public record Hit(String nickname, String visibleToken, int left, int top, int right, int bottom) {
        public int width() { return Math.max(1, right - left); }
        public int height() { return Math.max(1, bottom - top); }
    }
}
