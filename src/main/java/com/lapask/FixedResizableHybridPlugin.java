package com.lapask;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.lapask.config.ResizeBy;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.awt.*;
import java.util.*;
import java.util.List;
import net.runelite.client.ui.overlay.OverlayManager;


@Slf4j
@PluginDescriptor(
	name = "Fixed Resizable Hybrid",
	description = "Skins the \"Resizable - Classic Layout\" to match fixed mode.",
	tags = {"resize", "resizable", "classic", "fixed", "widescreen", "legacy", "hybrid"}
)
public class FixedResizableHybridPlugin extends Plugin
{

	@Inject
	private Client client;

	@Inject
	private FixedResizableHybridConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FixedResizableHybridOverlay enabledOverlays;

	private boolean resizeOnGameTick = false;
	private boolean widgetsModified = false;
	private final HashMap<Integer, WidgetState> originalStates = new HashMap<>();
	private static final int classicResizableGroupId = InterfaceID.RESIZABLE_VIEWPORT;
	private static final int oldSchoolBoxId = ComponentID.RESIZABLE_VIEWPORT_RESIZABLE_VIEWPORT_OLD_SCHOOL_BOX;

	@Provides
	FixedResizableHybridConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FixedResizableHybridConfig.class);

	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Fixed Hybrid Plugin started!");
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			queuePluginInitialization();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Fixed Resizable Hybrid Plugin stopped!");
		resetWidgets();
	}

	//Tried to avoid using onBeforeRender as much as possible, but there are some minigame widgets that get adjusted seemingly without a script
	//attached, so I used this to modify those widgets prior to render
	@Subscribe
	public void onBeforeRender(final BeforeRender event)
	{
		if (widgetsModified)
		{
			fixIngameOverlayWidgets();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("fixedresizablehybrid"))
		{
			return;
		}
		//Resize client if config option is enabled
		String eventKey = event.getKey();
		if (eventKey.equals("aspectRatioResize") && config.aspectRatioResize())
		{
			clientThread.invoke(this::resizeByAspectRatio);
		}
		else if (eventKey.equals("fillGapBorders")
			|| eventKey.equals("isWideChatbox")
			|| eventKey.equals("chatboxViewportCentering"))
		{
			resetWidgets();
			queuePluginInitialization();
		}
	}


	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{

		int scriptId = event.getScriptId();
		switch (scriptId)
		{
			case 909: // Interface boxes recalculated (e.g., bank inventory, settings panel, etc)
				fixInterfaceDimensions();
				break;
			case 904: // Window resized
				if (widgetsModified && config.isWideChatbox() && getGameClientLayout() == 2)
				{
					widenChat();
				}
				break;
			case 1699: // Right-aligned minimap orbs repositioned
			case 3305:
				fixWorldMapWikiStoreActAdvOrbs();
				break;
			case 902: // Inventory background changed, revert it back to its old sprite
				fixInvBackground();
				break;
			case 901: // Game Interface Mode changes
				gameClientLayoutChanged();
				break;
			case 175:
			case 113:
			case ScriptID.MESSAGE_LAYER_OPEN:
			case ScriptID.MESSAGE_LAYER_CLOSE:
			case 664:
				// Chatbox opens/closes
				if (config.isWideChatbox())
				{
					chatboxChanged();
					widenChat();
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (resizeOnGameTick)
		{
			resizeByAspectRatio();
			resizeOnGameTick = false;
		}
	}

	// Will continue trying to initialize until the GameState has been stabilized as logged in (e.g. layout == 2 or 3)
	// For some reason you can't use invoke() here or else it will delete the minimap orbs when you change interface mode.
	private void queuePluginInitialization()
	{
		//invokeLater will keep running until it returns true
		clientThread.invokeLater(() ->
		{
			// Uses getGameClientLayout() to determine when the game is ready to be initialized.
			int gameClientLayout = getGameClientLayout();
			if (gameClientLayout != -1)
			{
				if (gameClientLayout == 2)
				{
					initializePlugin();
				}
				return true;
			}
			return false;
		});
	}

	// Initializes the plugin by modifying necessary widgets and creating custom sprites.
	// Ensures the minimap, inventory, and viewport are properly adjusted for fixed mode.
	// Also resizes 16:9 if config option is true.
	private void initializePlugin()
	{
		widgetsModified = true;
		resizeRenderViewport();
		resizeByAspectRatio();
		overlayManager.add(enabledOverlays);
		if (config.isWideChatbox())
		{
			hideChatSprites();
			widenChat();
		}
		fixInterfaceDimensions();
		repositionMinimapWidgets();
		createFixedSprites();
	}

	private void resizeByAspectRatio()
	{
		if (!config.aspectRatioResize())
		{
			return;
		}

		Dimension newDimensions = calculateAspectRatioDimensions();
		if (newDimensions != null)
		{
			resizeClient(newDimensions);
		}
	}

	private Dimension calculateAspectRatioDimensions()
	{
		Widget fullCanvas = client.getWidget(classicResizableGroupId, 34);
		if (fullCanvas == null || fullCanvas.isHidden())
		{
			return null;
		}

		fullCanvas.revalidateScroll();

		Dimension stretchedDimensions = client.getStretchedDimensions();
		int currentWidth = stretchedDimensions.width;
		int currentHeight = stretchedDimensions.height;

		int aspectWidth = config.aspectRatioWidthResize();
		int aspectHeight = config.aspectRatioHeightResize();

		if (config.resizeBy() == ResizeBy.WIDTH)
		{
			int newHeight = aspectHeight * currentWidth / aspectWidth;
			return new Dimension(currentWidth, newHeight);
		}
		else
		{ // ResizeBy.HEIGHT
			int newWidth = aspectWidth * currentHeight / aspectHeight;
			return new Dimension(newWidth, currentHeight);
		}
	}

	private void resizeClient(Dimension dimension)
	{
		// Validate and adjust the dimensions
		int processedWidth = Math.max(Math.min(dimension.width, 7680), Constants.GAME_FIXED_WIDTH);
		int processedHeight = Math.max(Math.min(dimension.height, 2160), Constants.GAME_FIXED_HEIGHT);
		Dimension processedGameSize = new Dimension(processedWidth, processedHeight);

		Dimension currentSize = configManager.getConfiguration("runelite", "gameSize", Dimension.class);
		if (processedGameSize.equals(currentSize))
		{
			Dimension processedGameSizePlus1 = new Dimension(processedWidth + 1, processedHeight);
			configManager.setConfiguration("runelite", "gameSize", processedGameSizePlus1);
			resizeOnGameTick = true;
		}
		else
		{
			configManager.setConfiguration("runelite", "gameSize", processedGameSize);
		}
	}

	private void hideChatSprites()
	{
		Widget chatboxBackground = client.getWidget(ComponentID.CHATBOX_TRANSPARENT_BACKGROUND);
		if (chatboxBackground != null)
		{
			chatboxBackground.setHidden(true);
		}
		Widget chatboxButtonsSprite = client.getWidget(InterfaceID.CHATBOX, 3);
		if (chatboxButtonsSprite != null)
		{
			chatboxButtonsSprite.setHidden(true);
		}
	}

	private void resetChatSprites()
	{
		Widget chatboxBackground = client.getWidget(ComponentID.CHATBOX_TRANSPARENT_BACKGROUND);
		if (chatboxBackground != null)
		{
			chatboxBackground.setHidden(false);
		}
		Widget chatboxButtonsSprite = client.getWidget(InterfaceID.CHATBOX, 3);
		if (chatboxButtonsSprite != null)
		{
			chatboxButtonsSprite.setHidden(false);
		}
	}

	// Saves the widget state under these conditions:
	// 1. The widget exists
	// 2. The widget has not already been saved
	//    - prevents overwriting of the vanilla state when functions are called more than once
	// The resetLast parameter is specified for the function resetWidgets() to allow for some saved widgets
	// to be reset after the others, preventing issue where parent widgets needs to be revalidated again.
	private void saveWidgetState(Widget widget)
	{
		saveWidgetState(widget, false);
	}

	private void saveWidgetState(Widget widget, boolean resetLast)
	{
		if (widget == null)
		{
			return;
		}
		int widgetId = widget.getId();
		if (originalStates.containsKey(widgetId))
		{
			return;
		}
		WidgetState widgetState = new WidgetState(
			widget.getSpriteId(),
			widget.getOriginalX(),
			widget.getOriginalY(),
			widget.getOriginalWidth(),
			widget.getOriginalHeight(),
			widget.getXPositionMode(),
			widget.getYPositionMode(),
			widget.getWidthMode(),
			widget.getHeightMode(),
			widget.isHidden(),
			widget.isSelfHidden(),
			resetLast
		);
		originalStates.put(widgetId, widgetState);
	}

	// Determines the current game client layout mode.
	//
	// @return 1 if the layout is Fixed mode.
	// 2 if the layout is Resizable - Classic mode.
	// 3 if the layout is Resizable - Modern mode.
	// -1 if the layout cannot be determined.
	private int getGameClientLayout()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			Widget classicResizableWidget = client.getWidget(InterfaceID.RESIZABLE_VIEWPORT, 0);
			if (classicResizableWidget != null && !classicResizableWidget.isHidden())
			{
				return 2;
			}
			Widget modernResizableWidget = client.getWidget(InterfaceID.RESIZABLE_VIEWPORT_BOTTOM_LINE, 0);
			if (modernResizableWidget != null && !modernResizableWidget.isHidden())
			{
				return 3;
			}
			Widget classicFixedWidget = client.getWidget(InterfaceID.FIXED_VIEWPORT, 0);
			if (classicFixedWidget != null && !classicFixedWidget.isHidden())
			{
				return 1;
			}
		}
		return -1;
	}

	// Handles changes in the game client layout and triggers appropriate actions.
	//
	// This function is called after `onScriptPostFired()` for `scriptId == 901`.
	// It offers two key benefits over using `onGameStateChange()` or `client.isResizable()`:
	// 1. Prevents premature initialization by ensuring widgets are fully drawn, as
	//    `getGameClientLayout()` will return -1 if called too early.
	// 2. Provides a more specific response based on the interface layout, unlike the
	//    more general `isResizable()` method.
	//
	// If the layout changes to classic-resizable, the plugin is initialized.
	// For other layouts (fixed or modern-resizable), widgets are reset to avoid
	// interference caused by switching layouts.
	private void gameClientLayoutChanged()
	{
		if (getGameClientLayout() == 2)
		{
			queuePluginInitialization();
		}
		else
		{
			resetWidgets();
		}
	}


	// Adjusts the positions of the World Map, Wiki, Store, and Activity Adviser orbs to match fixed mode alignment.
	//
	// This function is executed in the following cases:
	//  1. During initialization to set the positions of the four orbs.
	//  2. After `onScriptPostFired()` (scriptId == 1699 || scriptId == 3305)
	//     - Resets the positions of right-aligned minimap orbs.
	//
	// When the game layout is in classic-resizable mode (int 2), the function:
	// - Saves the current state of each orb.
	// - Sets or resets their positions to match the fixed mode layout.
	private void fixWorldMapWikiStoreActAdvOrbs()
	{
		if (getGameClientLayout() == 2)
		{
			Widget worldMapOrb = client.getWidget(ComponentID.MINIMAP_WORLDMAP_ORB);
			Widget wikiBanner = client.getWidget(ComponentID.MINIMAP_WIKI_BANNER_PARENT);
			Widget storeOrb = client.getWidget(160, 42);
			Widget activityAdviserOrb = client.getWidget(160, 47);

			if (worldMapOrb != null && worldMapOrb.getOriginalX() == 0)
			{
				saveWidgetState(worldMapOrb);
				setWidgetCoordinates(worldMapOrb, 23, 109);
			}
			if (wikiBanner != null && wikiBanner.getOriginalX() == 0)
			{
				saveWidgetState(wikiBanner);
				setWidgetCoordinates(wikiBanner, 21, 129);
			}
			if (storeOrb != null && storeOrb.getOriginalX() == 85)
			{
				saveWidgetState(storeOrb);
				setWidgetParameters(storeOrb, 0 + 13, 83 - 6, 34, 34, 2, 0, 0, 0);
			}
			if (activityAdviserOrb != null && activityAdviserOrb.getOriginalX() == 55)
			{
				saveWidgetState(activityAdviserOrb);
				setWidgetParameters(activityAdviserOrb, 0 + 13, 50 - 6, 34, 34, 2, 0, 0, 0);
			}
		}
	}

	// Used in volcanic mine overlay fix
	private void fixIngameOverlayWidgets()
	{
		int maxDepth = 3;
		if (!widgetsModified)
		{
			return;
		}
		Widget clickWindow = client.getWidget(classicResizableGroupId, 92);
		Widget renderViewport = client.getWidget(classicResizableGroupId, 91);
		if (clickWindow != null && renderViewport != null)
		{
			clickWindow.setXPositionMode(0);
			clickWindow.setYPositionMode(0);
			clickWindow.setOriginalWidth(renderViewport.getWidth());
			clickWindow.revalidateScroll();
			fixWidgetChildDimensions(clickWindow, maxDepth, 0); // Start recursive processing
		}
	}

	// Runs from onScriptPostFired() for the scriptId == 909 which resets the bounding boxes of game interfaces (e.g.
	// banks, deposit boxes, settings, etc). This function sets those back to their modified states. Also fixes the background
	// for the fairy ring interface
	private void fixInterfaceDimensions()
	{
		Widget renderViewport = client.getWidget(classicResizableGroupId, 91);

		fixIngameOverlayWidgets();

		Widget oldSchoolBox = client.getWidget(oldSchoolBoxId);
		if (oldSchoolBox != null && renderViewport != null)
		{
			Widget parent = oldSchoolBox.getParent();
			int parentHeight = parent.getOriginalHeight();
			int renderViewportHeight = renderViewport.getHeight();
			boolean chatIsOpen = isChatboxOpen();
			if (parent.getXPositionMode() == 1 || parent.getYPositionMode() == 1)
			{
				parent.setXPositionMode(0);
				parent.setYPositionMode(0);
				parent.setOriginalWidth(renderViewport.getWidth());
				parent.revalidateScroll();
			}
			if (!config.isWideChatbox() && parentHeight != renderViewportHeight)
			{
				parent.setOriginalHeight(renderViewportHeight);
				parent.revalidateScroll();
			}
			else if (config.isWideChatbox() && parentHeight != renderViewportHeight + 23)
			{
				parent.setOriginalHeight(renderViewportHeight + 23);
				parent.revalidateScroll();
			}

			if (oldSchoolBox.getOriginalWidth() == 250)
			{
				oldSchoolBox.setOriginalWidth(0);
				oldSchoolBox.revalidateScroll();
			}
			if (config.isWideChatbox() && chatIsOpen)
			{
				oldSchoolBox.setOriginalHeight(23);
				oldSchoolBox.revalidateScroll();
			}
			for (Widget child : oldSchoolBox.getStaticChildren())
			{
				child.revalidateScroll();
			}
			fixFairyBackground();
		}
	}

	private void fixFairyBackground()
	{
		Widget fairyBackground = client.getWidget(161, 14);
		Widget fairyWidget = client.getWidget(161, 16);
		Widget mainViewport = client.getWidget(classicResizableGroupId, 91);
		Widget oldSchoolBox = client.getWidget(oldSchoolBoxId);
		if (fairyBackground != null && fairyWidget != null && mainViewport != null)
		{
			Widget[] fairyBackgroundWidgets = fairyBackground.getDynamicChildren();
			if (fairyBackgroundWidgets.length == 4)
			{
				Widget topFairyBackground = fairyBackgroundWidgets[0];
				Widget bottomFairyBackground = fairyBackgroundWidgets[1];
				Widget leftFairyBackground = fairyBackgroundWidgets[2];
				Widget rightFairyBackground = fairyBackgroundWidgets[3];
				boolean chatIsOpen = isChatboxOpen();
				int lateralWidth = fairyWidget.getRelativeX();
				int topHeight;
				if (chatIsOpen && config.isWideChatbox() && !config.chatboxViewportCentering() && oldSchoolBox != null)
				{
					topHeight = (oldSchoolBox.getHeight() - fairyWidget.getHeight() - 165 + 23) / 2;
				}
				else
				{
					topHeight = fairyWidget.getRelativeY();
				}

				leftFairyBackground.setOriginalWidth(lateralWidth);
				leftFairyBackground.setOriginalY(topHeight);

				rightFairyBackground.setOriginalWidth(lateralWidth);
				rightFairyBackground.setOriginalY(topHeight);

				topFairyBackground.setOriginalHeight(topHeight);

				if (config.isWideChatbox())
				{
					int bottomHeight = topHeight + 1;
					bottomFairyBackground.setOriginalHeight(bottomHeight);
					if (!config.chatboxViewportCentering())
					{
						bottomFairyBackground.setOriginalY(165);
					}
				}
				else
				{
					int bottomHeight = mainViewport.getHeight() - topHeight - fairyWidget.getHeight();
					bottomFairyBackground.setOriginalHeight(bottomHeight);
				}
				fairyBackground.revalidateScroll();
			}
		}
	}

	private void fixWidgetChildDimensions(Widget widget, int maxDepth, int currentDepth)
	{
		// Recurse until max depth is reached (unless maxDepth is 0)
		if (maxDepth != 0 && currentDepth >= maxDepth)
		{
			return;
		}
		// Process both static and nested children using the helper method
		processClickWindowChildren(widget.getStaticChildren(), maxDepth, currentDepth, true);
		processClickWindowChildren(widget.getNestedChildren(), maxDepth, currentDepth, false);
	}

	private void processClickWindowChildren(Widget[] children, int maxDepth, int currentDepth, boolean staticChildren)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			if ((child.getOriginalWidth() >= 248 && child.getOriginalWidth() <= 254) && child.getWidthMode() == 1)
			{
				child.setOriginalWidth(0);
			}
			if (staticChildren)
			{
				child.revalidateScroll();
			}
			// Recurse into both static and nested children
			fixWidgetChildDimensions(child, maxDepth, currentDepth + 1);
		}
	}

	// Runs from onScriptPostFired() for the script which fires and resets the inventory background sprite
	private void fixInvBackground()
	{
		if (widgetsModified && getGameClientLayout() == 2)
		{
			Widget invBackground = client.getWidget(classicResizableGroupId, 38);
			if (invBackground != null && invBackground.getSpriteId() == 897)
			{
				saveWidgetState(invBackground);
				invBackground.setSpriteId(1031);
			}
		}
	}

	// Resets all modified widgets. See removeAddedWidgets() for how all non-vanilla and plugin-created widgets are reset.
	// Runs whenever the user logs out, hops worlds, or changes the game client layout to something other than classic-resizable
	// There are a few widgets which need to be revalidated last to make sure everything resets properly, which is done last.
	private void resetWidgets()
	{
		clientThread.invoke(() -> {
			removeAddedWidgets();
			resetRenderViewport();
			resetOriginalStates(); // sets widgetModified to false too
			resetChatSprites();
		});
	}

	private void resetOriginalStates()
	{
		List<Map.Entry<Integer, WidgetState>> resetLastEntries = new ArrayList<>();

		// Iterate through the originalStates map
		for (Map.Entry<Integer, WidgetState> entry : originalStates.entrySet())
		{
			int widgetId = entry.getKey();
			WidgetState state = entry.getValue();

			// Skip resetLast widgets for now, and adds to the list to reset last.
			if (state.isResetLast())
			{
				resetLastEntries.add(entry);
				continue;
			}

			// Retrieves the widget and resets it
			Widget widget = client.getWidget(widgetId);
			if (widget != null)
			{
				widget.setSpriteId(state.getSpriteId());
				widget.setOriginalX(state.getOriginalX());
				widget.setOriginalY(state.getOriginalY());
				widget.setOriginalWidth(state.getOriginalWidth());
				widget.setOriginalHeight(state.getOriginalHeight());
				widget.setXPositionMode(state.getXPositionMode());
				widget.setYPositionMode(state.getYPositionMode());
				widget.setWidthMode(state.getWidthMode());
				widget.setHeightMode(state.getHeightMode());
				widget.setHidden(state.isHidden() || state.isSelfHidden());
			}
		}

		// Revalidates reset widgets
		clientThread.invoke(() -> {
			for (Map.Entry<Integer, WidgetState> entry : originalStates.entrySet())
			{
				if (!entry.getValue().isResetLast())
				{
					Widget widget = client.getWidget(entry.getKey());
					if (widget != null)
					{
						widget.revalidateScroll();
					}
				}
			}
		});

		// Process widgets with isResetLast() set to true
		for (Map.Entry<Integer, WidgetState> entry : resetLastEntries)
		{
			int widgetId = entry.getKey();
			WidgetState state = entry.getValue();

			// Retrieve the widget and reset it
			Widget widget = client.getWidget(widgetId);
			if (widget != null)
			{
				widget.setSpriteId(state.getSpriteId());
				widget.setOriginalX(state.getOriginalX());
				widget.setOriginalY(state.getOriginalY());
				widget.setOriginalWidth(state.getOriginalWidth());
				widget.setOriginalHeight(state.getOriginalHeight());
				widget.setXPositionMode(state.getXPositionMode());
				widget.setYPositionMode(state.getYPositionMode());
				widget.setWidthMode(state.getWidthMode());
				widget.setHeightMode(state.getHeightMode());
				widget.setHidden(state.isHidden());
			}
		}

		// Revalidate widgets with isResetLast == true
		clientThread.invoke(() -> {
			for (Map.Entry<Integer, WidgetState> entry : resetLastEntries)
			{
				Widget widget = client.getWidget(entry.getKey());
				if (widget != null)
				{
					widget.revalidateScroll();
				}
			}
		});

		// Clear the originalStates map after resetting, and sets the boolean to reflect that
		originalStates.clear();
		widgetsModified = false;
	}

	// Removes all widgets that plugin created (sprites surrounding the minimap/inventory)
	public void removeAddedWidgets()
	{
		//Deletes added minimap sprites + bottom border sprite
		Widget minimapDrawArea = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
		if (minimapDrawArea != null && minimapDrawArea.getParent() != null)
		{
			minimapDrawArea.getParent().deleteAllChildren();
		}

		// Deletes added inventory sprites
		Widget invDynamicParent = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT);
		if (invDynamicParent != null)
		{
			invDynamicParent.deleteAllChildren();
		}
		overlayManager.remove(enabledOverlays);
	}

	// Sets a widget's coordinates, overloaded to be able to accept both ComponentIDs or the widget directly
	private void setWidgetCoordinates(int componentId, int newX, int newY)
	{
		setWidgetCoordinates(client.getWidget(componentId), newX, newY);
	}

	private void setWidgetCoordinates(Widget widget, int newX, int newY)
	{
		if (widget != null)
		{
			saveWidgetState(widget);
			widget.setOriginalX(newX);
			widget.setOriginalY(newY);
			widget.revalidateScroll();
		}
	}

	// Expanded version of the setWidgetCoordinates() function to accept more parameters
	private void setWidgetParameters(Widget widget,
									 int newX,
									 int newY,
									 int newOriginalWidth,
									 int newOriginalHeight,
									 int newXPositionMode,
									 int newYPositionMode,
									 int newWidthMode,
									 int newHeightMode)
	{
		if (widget != null)
		{
			saveWidgetState(widget);
			widget.setOriginalX(newX);
			widget.setOriginalY(newY);
			widget.setOriginalWidth(newOriginalWidth);
			widget.setOriginalHeight(newOriginalHeight);
			widget.setXPositionMode(newXPositionMode);
			widget.setYPositionMode(newYPositionMode);
			widget.setWidthMode(newWidthMode);
			widget.setHeightMode(newHeightMode);
			widget.revalidateScroll();
		}
	}

	// Positions the all the minimap elements to align with fixed mode, and saves the original widget parameters for
	//     resetWidgets() later. Could definitely be cleaned up.
	private void repositionMinimapWidgets()
	{
		Widget minimapWidget = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP);
		Widget minimapSprite = client.getWidget(classicResizableGroupId, 32);
		Widget minimapWidgetOrbsParent = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_ORB_HOLDER);
		Widget minimapWidgetOrbsInterface = client.getWidget(ComponentID.MINIMAP_CONTAINER);
		if (client.isResized() &&
			minimapWidget != null &&
			minimapSprite != null &&
			minimapWidgetOrbsParent != null &&
			minimapWidgetOrbsInterface != null)
		{
			saveWidgetState(minimapWidget, true);
			saveWidgetState(minimapSprite);
			saveWidgetState(minimapWidgetOrbsInterface);
			saveWidgetState(minimapWidgetOrbsParent);

			minimapSprite.setHidden(true);

			minimapWidget.setOriginalWidth(249);
			minimapWidget.setOriginalHeight(207);
			minimapWidget.revalidateScroll();

			minimapWidgetOrbsParent.setOriginalWidth(249);
			minimapWidgetOrbsParent.setOriginalHeight(197);
			minimapWidgetOrbsParent.revalidateScroll();

			minimapWidgetOrbsInterface.setOriginalWidth(249);
			minimapWidgetOrbsInterface.setOriginalHeight(197);
			minimapWidgetOrbsInterface.setWidthMode(0);
			minimapWidgetOrbsInterface.setHeightMode(0);
			minimapWidgetOrbsInterface.revalidateScroll();

			int[][] minimapViewportAdjustment = {
				{23, 44, 5},
				{24, 44, 45},
				{25, 44, 101},
				{26, 44, 126},
				{27, 44, 141},
				{28, 44, 156},
				{30, 50, 9},
				{32, 44, 1}
			};
			for (int[] widgetAdjustment : minimapViewportAdjustment)
			{
				int childId = widgetAdjustment[0];
				int newX = widgetAdjustment[1];
				int newY = widgetAdjustment[2];
				Widget wdgToAdj = (widgetAdjustment[0] == 30)
					? client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA)
					: client.getWidget(classicResizableGroupId, childId);
				if (wdgToAdj != null && wdgToAdj.getXPositionMode() == 2)
				{
					saveWidgetState(wdgToAdj, true);
					// Set the position mode to absolute
					wdgToAdj.setXPositionMode(0);

					// Set the absolute coordinates using setWidgetCoordinates
					setWidgetCoordinates(wdgToAdj, newX, newY);
					wdgToAdj.revalidateScroll();
				}
			}

			//xp button
			setWidgetCoordinates(ComponentID.MINIMAP_XP_ORB, 0, 11);
			//health orb
			setWidgetCoordinates(ComponentID.MINIMAP_HEALTH_ORB, 0, 31);
			//prayer orb
			setWidgetCoordinates(ComponentID.MINIMAP_PRAYER_ORB, 0, 65);
			//run orb
			setWidgetCoordinates(ComponentID.MINIMAP_RUN_ORB, 10, 97);
			//spec orb
			setWidgetCoordinates(ComponentID.MINIMAP_SPEC_ORB, 32, 122);
			//compass orb clickbox, doesn't have have componentID
			setWidgetCoordinates(client.getWidget(classicResizableGroupId, 31), 26, 1);
			//compass orb viewbox, doesn't have have componentID
			setWidgetCoordinates(client.getWidget(classicResizableGroupId, 29), 28, 3);
			//world map orb, wiki banner, store orb, and activity adviser orb all handled under this function
			fixWorldMapWikiStoreActAdvOrbs();
			minimapWidget.revalidateScroll();
		}
	}

	// Creates new widgets (defined by newSpriteConfigs) that weren't originally loaded in classic-resizable
	private void createFixedSprites()
	{
		// Get the parent widget the sprites should be under
		Widget minimapDrawArea = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
		Widget inventoryParentWidget = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT);
		// Define the configurations for all the sprites to be created.
		// Each row represents a sprite with the following columns:
		// [widget, type, spriteId, originalX, originalY, originalWidth, originalHeight, xPositionMode, yPositionMode, widthMode, heightMode, noclickthrough]
		if (minimapDrawArea != null && minimapDrawArea.getParent() != null && inventoryParentWidget != null)
		{
			int[][] newSpriteConfigs = {
				{1, 5, 1182, 29, 4, 172, 156, 0, 0, 0, 0, 0},  // centerMinimapSprite
				{1, 5, 1611, 0, 160, 249, 8, 1, 0, 0, 0, 0},   // bottomMinimapSprite
				{1, 5, 1037, 0, 4, 29, 156, 0, 0, 0, 0, 0},    // leftMinimapSprite
				{1, 5, 1038, 0, 4, 48, 156, 2, 0, 0, 0, 0},    // rightMinimapSprite
				{1, 5, 1039, 48, 0, 717, 4, 2, 0, 0, 0, 0},    // topThinBarRight
				{1, 5, 1441, 0, 0, 48, 4, 2, 0, 0, 0, 0},      // topThinBarLeft
				{2, 5, 1035, 0, 37, 28, 261, 2, 2, 0, 0, 0}, // right inv column
				{2, 5, 1033, 0, 38, 31, 133, 0, 0, 0, 0, 0}, // left inv column top half
				{2, 5, 1034, 3, 171, 28, 128, 0, 0, 0, 0, 0},  // left inv column bottom half
				{2, 5, 1033, 0, 0, 3, 170, 0, 2, 0, 0, 0} // left tiny strip to the left of bottom half
			};
			//Ensure the bounds on the parent container(s) are properly prepared.
			inventoryWidgetBoundsFix();

			// Create widgets using the configurations
			Widget minimapParentWidget = minimapDrawArea.getParent(); // same as client.getWidget(161,22); but uses ComponentID reference
			for (int[] newSpriteConfig : newSpriteConfigs)
			{
				Widget parentWidget = null;
				if (newSpriteConfig[0] == 1)
				{
					parentWidget = minimapParentWidget;
				}
				else if (newSpriteConfig[0] == 2)
				{
					parentWidget = inventoryParentWidget;
				}
				// extra null check here in case we add new added widgets later, should never be null given current newSpriteConfigs
				if (parentWidget != null)
				{
					Widget minimapSprite = parentWidget.createChild(newSpriteConfig[1]);
					minimapSprite.setSpriteId(newSpriteConfig[2]);
					minimapSprite.setOriginalX(newSpriteConfig[3]);
					minimapSprite.setOriginalY(newSpriteConfig[4]);
					minimapSprite.setOriginalWidth(newSpriteConfig[5]);
					minimapSprite.setOriginalHeight(newSpriteConfig[6]);
					minimapSprite.setXPositionMode(newSpriteConfig[7]);
					minimapSprite.setYPositionMode(newSpriteConfig[8]);
					minimapSprite.setWidthMode(newSpriteConfig[9]);
					minimapSprite.setHeightMode(newSpriteConfig[10]);
					if (newSpriteConfig[11] == 1)
					{
						minimapSprite.setNoClickThrough(true);
					}
					minimapSprite.getParent().revalidate();
					minimapSprite.revalidate();
				}
			}
		}
	}

	// Sets up the coordinates and bounds on the inventory panel widget prior to creating the fixed background sprites
	// and prior to modifying the existing inventory sprites.
	private void inventoryWidgetBoundsFix()
	{
		Widget invParent = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_INVENTORY_PARENT);
		if (invParent != null)
		{
			saveWidgetState(invParent, true);
			invParent.setOriginalWidth(249);
			invParent.setOriginalHeight(336);
			invParent.revalidate();
		}

		Widget invBackground = client.getWidget(classicResizableGroupId, 38);
		if (invBackground != null)
		{
			saveWidgetState(invBackground);
			invBackground.setOriginalX(28);
			invBackground.setOriginalY(37);
			invBackground.setOriginalWidth(190);
			invBackground.setOriginalHeight(261);
			invBackground.setSpriteId(1031);
			invBackground.revalidate();
		}

		Widget invLeftColumn = client.getWidget(classicResizableGroupId, 39);
		if (invLeftColumn != null)
		{
			saveWidgetState(invLeftColumn);
			invLeftColumn.setHidden(true);
			invLeftColumn.revalidate();
		}
		Widget invRightColumn = client.getWidget(classicResizableGroupId, 40);
		if (invRightColumn != null)
		{
			saveWidgetState(invRightColumn);
			invRightColumn.setHidden(true);
			invRightColumn.revalidate();
		}

		Widget invBottomBarSprite = client.getWidget(classicResizableGroupId, 41);
		if (invBottomBarSprite != null)
		{
			saveWidgetState(invBottomBarSprite);
			invBottomBarSprite.setOriginalWidth(246);
			invBottomBarSprite.setOriginalHeight(37);
			invBottomBarSprite.setSpriteId(1032);
			invBottomBarSprite.revalidate();
		}

		Widget invBottomTabsParent = client.getWidget(classicResizableGroupId, 42);
		if (invBottomTabsParent != null)
		{
			saveWidgetState(invBottomTabsParent, true);
			invBottomTabsParent.setOriginalX(2);
			invBottomTabsParent.revalidate();
		}

		Widget invTopBarSprite = client.getWidget(classicResizableGroupId, 57);
		if (invTopBarSprite != null)
		{
			saveWidgetState(invTopBarSprite);
			invTopBarSprite.setOriginalY(298);
			invTopBarSprite.setOriginalWidth(249);
			invTopBarSprite.setOriginalHeight(38);
			invTopBarSprite.setSpriteId(1036);
			invTopBarSprite.revalidate();
		}

		Widget invTopTabsParent = client.getWidget(classicResizableGroupId, 58);
		if (invTopTabsParent != null)
		{
			saveWidgetState(invTopTabsParent, true);
			invTopTabsParent.setOriginalX(2);
			invTopTabsParent.revalidate();
		}

		Widget invViewportInterfaceController = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_INTERFACE_CONTAINER);
		if (invViewportInterfaceController != null)
		{
			saveWidgetState(invViewportInterfaceController);
			invViewportInterfaceController.setOriginalX(26 + 2);
			invViewportInterfaceController.revalidate();
		}
	}

	// Resizes the main viewport of the game so that no rendering occurs underneath the minimap/inventory.
	// This also consequently centers the camera properly, one of my main annoyances with the original resizable mode
	private void resizeRenderViewport()
	{
		Widget mainViewport = client.getWidget(classicResizableGroupId, 91);
		if (mainViewport != null)
		{
			// Width is set to the width of the inventory and minimap widgets because widthMode = 1 (subtracts
			//     that value from the parent widget's dimensions).
			mainViewport.setOriginalWidth(249);
			if (config.isWideChatbox())
			{
				chatboxChanged();
			}
			// Configures height of viewport if wide chatbox is enabled
			mainViewport.revalidateScroll();
		}
	}

	// Reset's the plugin's changes on the render viewport back to the original fullscreen resizable mode.
	// Called during the resetWidgets() function.
	private void resetRenderViewport()
	{
		Widget mainViewport = client.getWidget(classicResizableGroupId, 91);
		if (mainViewport != null)
		{
			clientThread.invoke(() -> {
				mainViewport.setOriginalWidth(0);
				mainViewport.setOriginalHeight(0);
				mainViewport.setYPositionMode(1);
				mainViewport.revalidateScroll();
			});
		}
	}

	//Runs after onPostScript when opening or closing of the chatbox. Handles recentering the viewport. Wide chat mode only.
	private void chatboxChanged()
	{
		if (config.isWideChatbox() && getGameClientLayout() == 2)
		{
			Widget mainViewport = client.getWidget(classicResizableGroupId, 91);
			Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
			Widget oldSchoolBox = client.getWidget(oldSchoolBoxId);
			Widget osbParent = oldSchoolBox.getParent();
			if (mainViewport != null && chatboxFrame != null)
			{
				//chatbox opened
				if (isChatboxOpen() && config.chatboxViewportCentering())
				{
					mainViewport.setOriginalHeight(165);
					mainViewport.setYPositionMode(0);
					mainViewport.revalidateScroll();
					osbParent.setOriginalHeight(mainViewport.getHeight());
					osbParent.revalidateScroll();
					oldSchoolBox.setOriginalHeight(0);
					oldSchoolBox.revalidateScroll();
				}
				else
				{
					//chatbox closed
					mainViewport.setOriginalHeight(23);
					mainViewport.setYPositionMode(0);
					mainViewport.revalidateScroll();
					osbParent.setOriginalHeight(mainViewport.getHeight() + 23);
					osbParent.revalidateScroll();
					oldSchoolBox.setOriginalHeight(165);
					oldSchoolBox.revalidateScroll();
				}
			}
		}
	}


	private void widenChat()
	{
		if (!config.isWideChatbox() || !widgetsModified || getGameClientLayout() != 2)
		{
			return;
		}
		Widget mainViewport = client.getWidget(classicResizableGroupId, 91);
		if (mainViewport == null)
		{
			return;
		}
		int wideChatboxWidth = mainViewport.getWidth();

		Widget chatParent = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT);//161.96
		if (chatParent != null)
		{
			saveWidgetState(chatParent);
			chatParent.setOriginalWidth(wideChatboxWidth);
			chatParent.revalidateScroll();
		}
		Widget chatFrame = client.getWidget(ComponentID.CHATBOX_FRAME); //162.34
		if (chatFrame != null)
		{
			saveWidgetState(chatFrame);
			chatFrame.setOriginalWidth(wideChatboxWidth);
			chatFrame.revalidateScroll();
		}
		Widget dialogueOptions = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
		if (dialogueOptions != null)
		{
			saveWidgetState(dialogueOptions);
			dialogueOptions.setOriginalX(0);
			dialogueOptions.setXPositionMode(1);
			dialogueOptions.getParent().revalidateScroll();
		}
		Widget reportAbuseDialogueSprite = client.getWidget(875, 1);
		if (reportAbuseDialogueSprite != null)
		{
			saveWidgetState(reportAbuseDialogueSprite);
			reportAbuseDialogueSprite.setHidden(true);
		}
		// Cooking/fletching background removal
		Widget makingDialogSprite = client.getWidget(270, 1);
		if (makingDialogSprite != null)
		{
			saveWidgetState(makingDialogSprite);
			makingDialogSprite.setHidden(true);
		}
		//Center chat buttons on viewport
		Widget chatButtons = client.getWidget(ComponentID.CHATBOX_BUTTONS); //162.1
		if (chatButtons != null)
		{
			saveWidgetState(chatButtons);
			chatButtons.setXPositionMode(1);
			chatButtons.revalidateScroll();
		}
	}

	private boolean isChatboxOpen()
	{
		Widget chatboxFrame = client.getWidget(ComponentID.CHATBOX_FRAME);
		if (chatboxFrame != null)
		{
			return !chatboxFrame.isHidden();
		}
		return false;
	}
}