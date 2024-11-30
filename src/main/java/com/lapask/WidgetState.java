package com.lapask;

public class WidgetState {
    private final int spriteId;
    private final int originalX;
    private final int originalY;
    private final int originalWidth;
    private final int originalHeight;
    private final int xPositionMode;
    private final int yPositionMode;
    private final int widthMode;
    private final int heightMode;
    private final boolean hidden;
    private final boolean selfHidden;
    private final boolean resetLast;

    public WidgetState(
            int spriteId,
            int originalX,
            int originalY,
            int originalWidth,
            int originalHeight,
            int xPositionMode,
            int yPositionMode,
            int widthMode,
            int heightMode,
            boolean hidden,
            boolean selfHidden,
            boolean resetLast
    ) {
        this.spriteId = spriteId;
        this.originalX = originalX;
        this.originalY = originalY;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        this.xPositionMode = xPositionMode;
        this.yPositionMode = yPositionMode;
        this.widthMode = widthMode;
        this.heightMode = heightMode;
        this.hidden = hidden;
        this.selfHidden = selfHidden;
        this.resetLast = resetLast;
    }

    public int getSpriteId() { return spriteId; }
    public int getOriginalX() { return originalX; }
    public int getOriginalY() { return originalY; }
    public int getOriginalWidth() { return originalWidth; }
    public int getXPositionMode() { return xPositionMode; }
    public int getYPositionMode() { return yPositionMode; }
    public int getWidthMode() { return widthMode; }
    public int getHeightMode() { return heightMode; }
    public int getOriginalHeight() { return originalHeight; }
    public boolean isHidden() { return hidden; }
    public boolean isSelfHidden() { return selfHidden; }
    public boolean isResetLast() { return resetLast; }

    @Override
    public String toString() {
        return "WidgetState{" +
                "spriteId=" + spriteId +
                ", originalX=" + originalX +
                ", originalY=" + originalY +
                ", originalWidth=" + originalWidth +
                ", originalHeight=" + originalHeight +
                ", xPositionMode=" + xPositionMode +
                ", yPositionMode=" + yPositionMode +
                ", widthMode=" + widthMode +
                ", heightMode=" + heightMode +
                ", hidden=" + hidden +
                ", resetLast=" + resetLast +
                '}';
    }
}
