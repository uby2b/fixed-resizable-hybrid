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
    private static final Image gapBorder = ImageUtil.loadImageResource(FixedResizableHybridPlugin.class, "/border15px.png");
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

        graphics.setColor(config.gapColor());
        graphics.fill(overlayBounds);
        if (config.useGapBorders()) 
        {
            final Color borderTint = config.gapBorderColor();
            final boolean tintHasAlpha = borderTint.getAlpha() > 0;
            // inventory gap border
            Widget inventoryParent = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT);
            if (inventoryParent != null) 
            {
              int imageX = inventoryParent.getCanvasLocation().getX();
              int imageY = inventoryParent.getCanvasLocation().getY() - 15;
              graphics.drawImage(gapBorder, imageX, imageY, null);
              // overlay the tint only where the image pixels are
              if (tintHasAlpha) {
                Composite old = graphics.getComposite();
                graphics.setComposite(AlphaComposite.SrcAtop);
                graphics.setColor(borderTint);
                graphics.fillRect(imageX, imageY, gapBorder.getWidth(null), gapBorder.getHeight(null));
                graphics.setComposite(old);
            }
          }

            // minimap gap border
          Widget minimapContainer = client.getWidget(ComponentID.MINIMAP_CONTAINER);
          if (minimapContainer != null) 
          {
            int imageX = minimapContainer.getCanvasLocation().getX();
            int imageY = minimapContainer.getCanvasLocation().getY() + 158;
            graphics.drawImage(gapBorder, imageX, imageY, null);
            if (tintHasAlpha) {
                Composite old = graphics.getComposite();
                graphics.setComposite(AlphaComposite.SrcAtop);
                graphics.setColor(borderTint);
                graphics.fillRect(imageX, imageY, gapBorder.getWidth(null), gapBorder.getHeight(null));
                graphics.setComposite(old);
              }
            }
          }
      return overlayBounds.getSize();
      }
    }
