package com.lapask;

import java.awt.*;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

public class FixedResizableHybridOverlay extends Overlay
{
    private static final int OVERLAY_WIDTH = 249;

    private final Client client;
    //private final FixedResizableHybridPlugin plugin;
    private final FixedResizableHybridConfig config;
    // Load the images for the chatbox background
    private static final Image leftChatboxBackground = ImageUtil.loadImageResource(FixedResizableHybridPlugin.class, "/LeftChatboxBackground.png");
    private static final Image middleChatboxBackground = ImageUtil.loadImageResource(FixedResizableHybridPlugin.class, "/MiddleChatboxBackground.png");
    private static final Image rightChatboxBackground = ImageUtil.loadImageResource(FixedResizableHybridPlugin.class, "/RightChatboxBackground.png");
    private static final Image chatboxButtonsBackground = ImageUtil.loadImageResource(FixedResizableHybridPlugin.class, "/ChatboxButtonsBackground.png");

    @Inject
    public FixedResizableHybridOverlay(Client client, FixedResizableHybridConfig config, FixedResizableHybridPlugin plugin)
    {
        this.client = client;
        this.config = config;
        //this.plugin = plugin;

        // Set the overlay position and layer
        setPosition(OverlayPosition.DYNAMIC);
        // Render behind widgets
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Get the canvas height dynamically
        Dimension clientDimensions = client.getRealDimensions();
        int clientWidth = (int) clientDimensions.getWidth();
        int clientHeight = (int) clientDimensions.getHeight();
        Rectangle overlayBounds = new Rectangle(clientWidth - OVERLAY_WIDTH, 0, OVERLAY_WIDTH, clientHeight);

        graphics.setColor(new Color(47, 42, 32));
        graphics.fill(overlayBounds);
        if (config.useGapBorders()) {
            Image gapBorder = ImageUtil.loadImageResource(FixedResizableHybridPlugin.class, "/border15px.png");

            // inventory gap border
            Widget inventoryParent = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT);
            if (inventoryParent != null) {
                int imageX = inventoryParent.getCanvasLocation().getX();
                int imageY = inventoryParent.getCanvasLocation().getY() - 15;
                graphics.drawImage(gapBorder, imageX, imageY, null);
            }

            // minimap gap border
            Widget minimapContainer = client.getWidget(ComponentID.MINIMAP_CONTAINER);
            if (minimapContainer != null) {
                int imageX = minimapContainer.getCanvasLocation().getX();
                int imageY = minimapContainer.getCanvasLocation().getY() + 158;
                graphics.drawImage(gapBorder, imageX, imageY, null);
            }
        }

        if (config.isWideChatbox()){
            // Get the chatbox widget location and dimensions
            Widget viewportWidget = client.getWidget(161, 91); // Assuming this provides the width of the viewport
            Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
            Widget chatboxButtons = client.getWidget(ComponentID.CHATBOX_BUTTONS);
            if (chatboxFrame != null && viewportWidget != null && chatboxButtons != null)
            {
                // Calculate Y position
                int yPosition = chatboxFrame.getCanvasLocation().getY();

                // Calculate dimensions
                int totalWidth = viewportWidget.getWidth() +3;
                int middleWidth = totalWidth - 30; // Subtract width of left and right borders (2x 15px)
                int frameHeight = 142; // Height for all three images
                int buttonsHeight = 23;
                int buttonsY = chatboxButtons.getCanvasLocation().getY();
                if (!chatboxFrame.isHidden()) {
                    // Draw left chatbox background (15x142, fixed position)
                    graphics.drawImage(leftChatboxBackground, 0, yPosition, null);

                    // Draw middle chatbox background (stretched to middleWidth, 142px height)
                    graphics.drawImage(
                            middleChatboxBackground,
                            15, // Positioned to the right of the left background
                            yPosition,
                            middleWidth + 15, // End X (start X + middleWidth)
                            yPosition + frameHeight, // End Y (start Y + height)
                            0, 0, middleChatboxBackground.getWidth(null), middleChatboxBackground.getHeight(null),
                            null
                    );

                    // Draw right chatbox background (15x142, fixed position)
                    graphics.drawImage(rightChatboxBackground, totalWidth - 15, yPosition, null);
                }

                graphics.drawImage(chatboxButtonsBackground,
                        0,
                        buttonsY,
                        totalWidth,
                        buttonsY + buttonsHeight,
                        0,
                        0,
                        chatboxButtonsBackground.getWidth(null),
                        chatboxButtonsBackground.getHeight(null),
                        null
                        );
            }
        }
        return overlayBounds.getSize();
    }
}