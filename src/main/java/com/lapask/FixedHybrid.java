package com.lapask;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;
import java.awt.*;
import java.lang.reflect.Field;

@Slf4j
@PluginDescriptor(
		name = "Fixed Hybrid",
		description = "Automatically resizes the client when enabled",
		tags = {"resize", "client", "fixed"}
)
public class FixedHybrid extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private FixedHybridConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	private Dimension draggingEdgesSecondResizeDimension;
	private boolean resizeOnGameTick = false;
	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.resizeTrigger())
	{
		@Override
		public void hotkeyPressed()
		{
			resizeClient(new Dimension(config.clientWidth(),config.clientHeight()));
		}
	};
	@Override
	protected void startUp() throws Exception
	{
		log.info("Fixed Hybrid Plugin started!");
		keyManager.registerKeyListener(hotkeyListener);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Fixed Hybrid Plugin stopped!");
		keyManager.unregisterKeyListener(hotkeyListener);
	}

	@Provides
	FixedHybridConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FixedHybridConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{

	}
	@Subscribe
	public void onGameTick(GameTick gameTick) {
		//onGameTick only fires while logged in!
		if (resizeOnGameTick){
			Dimension configDimensions = new Dimension(config.clientWidth(), config.clientHeight());
			resizeClient(configDimensions);
			resizeOnGameTick = false;
		}
	}
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == 595){
			setWidgetCoordinates(client.getWidget(160, 48).getId(), 23, 109);
		}
		log.info("WidgetLoaded: {}",event);
	}
	@Subscribe
	public void onWidgetClosed(WidgetClosed event) {
		if (event.getGroupId() == 595){
			setWidgetCoordinates(client.getWidget(160, 48).getId(), 23, 109);
		}
		log.info("WidgetUnloaded: {}",event);
	}
	@Subscribe
	public void onBeforeRender(final BeforeRender event)
	{
		if (client.isResized()) {
			Widget invBackground = client.getWidget(161, 38);
			Widget worldMapOrb = client.getWidget(160, 48);
			Widget minimapSpriteResized = client.getWidget(161, 32);
			Widget interfacesParent = client.getWidget(161,15);
			if (invBackground != null){
				if (invBackground.getSpriteId() == 897 && invBackground.getOriginalX() == 28) {
					invBackground.setSpriteId(1031);
				}
			}
			if (worldMapOrb != null && minimapSpriteResized != null) {
				if (minimapSpriteResized.isHidden() && worldMapOrb.getOriginalX() == 0) {
					//setWidgetCoordinates(worldMapOrb.getId(), 23, 109);
				}
			}
			if (interfacesParent != null){
				if (interfacesParent.getWidth() < 644) {
					//interfacesParent.setWidth(644);
				}
			}
		}
	}
	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event) {
		if ("adjustEnum".equals(event.getEventName())) {
			int[] intStack = client.getIntStack();
			int intStackSize = client.getIntStackSize();

			// Access $enum1 (assume it's at a specific position, adjust as needed)
			int enumIndex = intStackSize - 1;
			int enumValue = intStack[enumIndex];

			// Adjust the enum value if it's 1130
			if (enumValue == 1130) {
				intStack[enumIndex] = 1129;
			}
		}
	}
	/**
	 * Resizes the client to the specified dimension.
	 */
	private void resizeClient(Dimension dimension) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			log.warn("Client is not logged in. Cannot resize.");
			return;
		}

		// Validate and adjust the dimensions
		int processedWidth = Math.max(Math.min(dimension.width, 7680), Constants.GAME_FIXED_WIDTH);
		int processedHeight = Math.max(Math.min(dimension.height, 2160), Constants.GAME_FIXED_HEIGHT);
		Dimension processedGameSize = new Dimension(processedWidth, processedHeight);
		Dimension currentSize = configManager.getConfiguration("runelite", "gameSize", Dimension.class);
		if (processedGameSize.equals(currentSize)){
			Dimension processedGameSizePlus1 = new Dimension(processedWidth + 1, processedHeight);
			configManager.setConfiguration("runelite", "gameSize", processedGameSizePlus1);
			resizeOnGameTick = true;
			log.info("Client resized to: {}x{}", processedGameSizePlus1.width, processedGameSizePlus1.height);
		}
		if (!processedGameSize.equals(currentSize)){
			configManager.setConfiguration("runelite", "gameSize", processedGameSize);
			log.info("Client resized to: {}x{}", processedGameSize.width, processedGameSize.height);
			//redundancy
			resizeOnGameTick = false;
			//logAllWidgetCoordinates();
			repositionMinimapWidgets();
		}
	}
	public void logWorldMapWidgetInfo() {
		// Check if the WORLD_MAP widget exists
		Widget worldMapWidget = client.getWidget(InterfaceID.WORLD_MAP);

		if (worldMapWidget == null) {
			// Log that the WORLD_MAP widget is null
			log.info("WORLD_MAP widget is null.");
		} else {
			// Log the information if the widget is not null
			int interfaceId = InterfaceID.WORLD_MAP;
			int widgetId = worldMapWidget.getId();
			boolean isHidden = worldMapWidget.isHidden();

			log.info("WORLD_MAP Widget Details - InterfaceID: {}, WidgetID: {}, isHidden: {}",
					interfaceId, widgetId, isHidden);
		}
	}
	public void logAllWidgetCoordinates() {
		// Log coordinates for each widget
		//Widget inventoryWidget = client.getWidget(161, 97);
		//Widget inventoryWidget = client.getWidget(ComponentID.MINIMAP_CONTAINER);
		//int inventoryWidgetId = inventoryWidget.getId();
		//log.info("XPosMode: {} YPosMode: {} OriginalX: {} OriginalY: {} RelativeX: {} RelativeY: {}",inventoryWidget.getXPositionMode(),inventoryWidget.getYPositionMode(),inventoryWidget.getOriginalX(),inventoryWidget.getOriginalY(),inventoryWidget.getRelativeX(),inventoryWidget.getRelativeY());
		//setWidgetOffsetCoordinates(client.getWidget(161, 95).getId(),41,0);
		//logWidgetCoordinates("RESIZEABLE_VIEWPORT_INTERFACE_CONTAINER", inventoryWidgetId);
		//logWidgetCoordinates("MINIMAP_HEALTH_ORB", ComponentID.MINIMAP_HEALTH_ORB);
		//setWidgetOffsetCoordinates(client.getWidget(161, 97).getId(),2,0);
		//setWidgetOffsetCoordinates(ComponentID.MINIMAP_XP_ORB,3,-6);
		//setWidgetOffsetCoordinates(ComponentID.MINIMAP_SPEC_ORB, 3, -6);

		Widget miniViewportFixed = client.getWidget(548,21);


		if (miniViewportFixed!=null) {
			logWidgetCoordinates("MINIMAP_VIEWPORT_FIXED", miniViewportFixed.getId());
		}
		Widget miniViewportResize = client.getWidget(161,30);
		if (miniViewportResize!=null) {
			//setWidgetOffsetCoordinates(miniViewportResize.getId(),48,-3);
			logWidgetCoordinates("MINIMAP_VIEWPORT_RESIZE", miniViewportResize.getId());
		}
		logWidgetCoordinates("MINIMAP_CONTAINER", ComponentID.MINIMAP_CONTAINER);
		logWidgetCoordinates("MINIMAP_XP_ORB", ComponentID.MINIMAP_XP_ORB);
		logWidgetCoordinates("MINIMAP_HEALTH_ORB", ComponentID.MINIMAP_HEALTH_ORB);
		logWidgetCoordinates("MINIMAP_PRAYER_ORB", ComponentID.MINIMAP_PRAYER_ORB);
		logWidgetCoordinates("MINIMAP_RUN_ORB", ComponentID.MINIMAP_RUN_ORB);
		logWidgetCoordinates("MINIMAP_SPEC_ORB", ComponentID.MINIMAP_SPEC_ORB);
		logWidgetCoordinates("MINIMAP_WORLDMAP_ORB", ComponentID.MINIMAP_WORLDMAP_ORB);
		logWidgetCoordinates("MINIMAP_WIKI_BANNER_PARENT", ComponentID.MINIMAP_WIKI_BANNER_PARENT);
		Widget compassWidgetFixed = client.getWidget(548,20);
		if (compassWidgetFixed!=null){
			logWidgetCoordinates("COMPASS_WIDGET_FIXED", compassWidgetFixed.getId());
		}
		Widget compassWidgetClickBoxFixed = client.getWidget(548,23);
		if (compassWidgetClickBoxFixed!=null){
			logWidgetCoordinates("COMPASS_WIDGET_CLICKBOX_FIXED", compassWidgetClickBoxFixed.getId());
		}
		Widget compassWidgetResize = client.getWidget(161,29);
		if (compassWidgetResize!=null){
			logWidgetCoordinates("COMPASS_WIDGET_RESIZE", compassWidgetResize.getId());
		}
		Widget compassWidgetClickBoxResize = client.getWidget(161,31);
		if (compassWidgetClickBoxResize!=null){
			logWidgetCoordinates("COMPASS_WIDGET_CLICKBOX_RESIZE", compassWidgetClickBoxResize.getId());
		}
		//createMinimapWidget();
	}
	private void logWidgetCoordinates(String widgetName, int componentId) {
		Widget widget = client.getWidget(componentId);

		if (widget != null) {
			// Log the original X and Y coordinates
			int x = widget.getOriginalX();
			int y = widget.getOriginalY();
			int posModeX = widget.getXPositionMode();
			int posModeY = widget.getYPositionMode();
			log.info("{} Widget coordinates: OrigX = {}, OrigY = {}, PosModeX = {}, PosModeY = {}", widgetName, x, y, posModeX, posModeY);
		} else {
			log.warn("Widget is null! Cannot log info");
		}
	}
	private void logWidgetCoordinates(String widgetName, Widget widget) {
		if (widget != null) {
			// Log the original X and Y coordinates
			int x = widget.getOriginalX();
			int y = widget.getOriginalY();
			int posModeX = widget.getXPositionMode();
			int posModeY = widget.getYPositionMode();
			log.info("{} Widget coordinates: OrigX = {}, OrigY = {}, PosModeX = {}, PosModeY = {}", widgetName, x, y, posModeX, posModeY);
		} else {
			log.warn("Widget is null! Cannot log info");
		}
	}
	public void setWidgetOffsetCoordinates(int componentId, int offsetX, int offsetY) {
		Widget widget = client.getWidget(componentId);

		if (widget != null) {
			// Get the current coordinates
			int currentX = widget.getOriginalX();
			int currentY = widget.getOriginalY();

			// Calculate new coordinates using the offsets
			int newX = currentX + offsetX;
			int newY = currentY + offsetY;

			// Set the new coordinates
			widget.setOriginalX(newX);
			widget.setOriginalY(newY);

			// Log the updated coordinates
			log.info("Widget {} moved by offset: X = {}, Y = {}. New coordinates: X = {}, Y = {}",
					componentId, offsetX, offsetY, newX, newY);
		}
	}
	public void setWidgetOffsetCoordinates(Widget widget, int offsetX, int offsetY) {
		if (widget != null) {
			// Get the current coordinates
			int currentX = widget.getOriginalX();
			int currentY = widget.getOriginalY();

			// Calculate new coordinates using the offsets
			int newX = currentX + offsetX;
			int newY = currentY + offsetY;

			// Set the new coordinates
			widget.setOriginalX(newX);
			widget.setOriginalY(newY);

			// Log the updated coordinates
			log.info("Widget {} moved by offset: X = {}, Y = {}. New coordinates: X = {}, Y = {}",
					widget.getId(), offsetX, offsetY, newX, newY);
		}
	}
	public void setWidgetCoordinates(int componentId, int newX, int newY) {
		Widget widget = client.getWidget(componentId);

		if (widget != null) {

			// Set the new coordinates
			widget.setOriginalX(newX);
			widget.setOriginalY(newY);
			widget.revalidate(); // Apply the changes

			// Log the updated coordinates
			log.info("Widget {} moved to: X = {}, Y = {}.",
					componentId, newX, newY);
		} else {
			log.warn("Widget with component ID {} is null!", componentId);
		}
	}
	public void repositionMinimapWidgets(){
		Widget minimapWidget = client.getWidget(161,95);
		Widget minimapWidgetOrbsParent = client.getWidget(161,33);
		log.info("Reposition minimap widgets function run");
		if (minimapWidget != null && client.isResized())
		{
			client.getWidget(161, 32).setHidden(true);
			if (minimapWidgetOrbsParent != null) {
				minimapWidget.setOriginalWidth(249);
				minimapWidget.setOriginalHeight(207);
				minimapWidgetOrbsParent.setOriginalWidth(249);
				minimapWidgetOrbsParent.setOriginalHeight(197);
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
				for (int[] adjustment : minimapViewportAdjustment) {
					int childId = adjustment[0];
					int newX = adjustment[1];
					int newY = adjustment[2];

					Widget wdgToAdj = client.getWidget(161, childId);
					if (wdgToAdj != null && wdgToAdj.getXPositionMode() == 2) {
						// Set the position mode to absolute
						wdgToAdj.setXPositionMode(0);

						// Set the absolute coordinates using setWidgetCoordinates
						setWidgetCoordinates(wdgToAdj.getId(), newX, newY);
					}
				}
			}
			//xp button
			setWidgetCoordinates(ComponentID.MINIMAP_XP_ORB,0,11);
			//health orb
			setWidgetCoordinates(ComponentID.MINIMAP_HEALTH_ORB, 0, 31);
			//prayer orb
			setWidgetCoordinates(ComponentID.MINIMAP_PRAYER_ORB, 0, 65);
			//run orb
			setWidgetCoordinates(ComponentID.MINIMAP_RUN_ORB, 10, 97);
			//spec orb
			setWidgetCoordinates(ComponentID.MINIMAP_SPEC_ORB, 32, 122);
			//wiki button
			setWidgetCoordinates(ComponentID.MINIMAP_WIKI_BANNER_PARENT, 21, 129);
			//worldmap orb
			setWidgetCoordinates(client.getWidget(160, 48).getId(), 23, 109);
			//compass orb clickbox
			setWidgetCoordinates(client.getWidget(161, 31).getId(), 26, 1);
			//compass orb viewbox
			setWidgetCoordinates(client.getWidget(161, 29).getId(), 28, 3);
			//inventory
			//setWidgetCoordinates(client.getWidget(161, 97).getId(), 2, 0);

			logWorldMapWidgetInfo();
			resizeViewport();
//			//xp button
//			setWidgetOffsetCoordinates(ComponentID.MINIMAP_XP_ORB,0,-6);
//			//health orb
//			setWidgetOffsetCoordinates(ComponentID.MINIMAP_HEALTH_ORB, 0, -6);
//			//prayer orb
//			setWidgetOffsetCoordinates(ComponentID.MINIMAP_PRAYER_ORB, 0, -6);
//			//run orb
//			setWidgetOffsetCoordinates(ComponentID.MINIMAP_RUN_ORB, 0, -6);
//			//spec orb
//			setWidgetOffsetCoordinates(ComponentID.MINIMAP_SPEC_ORB, 0, -6);
//			//wiki button
//			setWidgetOffsetCoordinates(ComponentID.MINIMAP_WIKI_BANNER_PARENT, 21, -6);
//			//worldmap orb
//			setWidgetOffsetCoordinates(client.getWidget(160, 48).getId(), 23, -6);
//			//compass orb clickbox
//			setWidgetOffsetCoordinates(client.getWidget(161, 31).getId(), -6, -2);
//			//compass orb viewbox
//			setWidgetOffsetCoordinates(client.getWidget(161, 29).getId(), -6, -2);
//			//inventory
//			setWidgetOffsetCoordinates(client.getWidget(161, 97).getId(), 2, 0);
			showFixedSprites();
		}
	}
	public void showFixedSprites() {
		// Get the parent widget the sprites should be under
		Widget minimapParentWidget = client.getWidget(161, 22);
		Widget inventoryParentWidget = client.getWidget(161, 97);
		// Define the configurations for all the sprites
		// Each row represents a sprite with the following columns:
		// [groupId, childId, type, spriteId, originalX, originalY, originalWidth, originalHeight, xPositionMode, yPositionMode, widthMode, heightMode]
		int[][] spriteConfigs = {
				{161, 22, 5, 1182, 29, 4, 172, 156, 0, 0, 0, 0, 0},  // centerMinimapSprite
				{161, 22, 5, 1611, 0, 160, 249, 8, 1, 0, 0, 0, 0},   // bottomMinimapSprite
				{161, 22, 5, 1037, 0, 4, 29, 156, 0, 0, 0, 0, 0},    // leftMinimapSprite
				{161, 22, 5, 1038, 0, 4, 48, 156, 2, 0, 0, 0, 0},    // rightMinimapSprite
				{161, 22, 5, 1039, 48, 0, 717, 4, 2, 0, 0, 0, 0},    // topThinBarRight
				{161, 22, 5, 1441, 0, 0, 48, 4, 2, 0, 0, 0, 0},      // topThinBarLeft
				{161, 97, 5, 1035, 0, 37, 28, 261, 2, 2, 0, 0, 0}, // right inv column
				{161, 97, 5, 1033, 0, 38, 31, 133, 0, 0, 0, 0, 0}, // left inv column top half
				{161, 97, 5, 1034, 3, 171, 28, 128, 0, 0, 0, 0, 0},  // left inv column bottom half
				{161, 97, 5, 1033, 0, 0, 3, 170, 0, 2, 0, 0, 0} // left tiny strip to the left of bottom half
		};
		if (minimapParentWidget != null && inventoryParentWidget != null) {
			inventoryWidgetBoundsFix();
			// Create widgets using the configurations
			for (int[] config : spriteConfigs) {
				Widget parentWidget = client.getWidget(config[0],config[1]);
				Widget minimapSprite = parentWidget.createChild(config[2]);
				minimapSprite.setSpriteId(config[3]);
				minimapSprite.setOriginalX(config[4]);
				minimapSprite.setOriginalY(config[5]);
				minimapSprite.setOriginalWidth(config[6]);
				minimapSprite.setOriginalHeight(config[7]);
				minimapSprite.setXPositionMode(config[8]);
				minimapSprite.setYPositionMode(config[9]);
				minimapSprite.setWidthMode(config[10]);
				minimapSprite.setHeightMode(config[11]);
				if (config[11] == 1){
					minimapSprite.setNoClickThrough(true);
				}
				minimapSprite.revalidate();
			}
		}
	}
	public void inventoryWidgetBoundsFix(){
		Widget invParent = client.getWidget(161, 97);
		Widget invBackground = client.getWidget(161,38);
		Widget invLeftColumn = client.getWidget(161,39);
		Widget invRightColumn = client.getWidget(161,40);
		Widget invBottomBarSprite = client.getWidget(161,41);
		Widget invBottomTabsParent = client.getWidget(161,42);
		Widget invTopBarSprite = client.getWidget(161,57);
		Widget invTopTabsParent = client.getWidget(161,58);
		Widget invViewportInterfaceController = client.getWidget(161,73);

		invParent.setOriginalWidth(249);
		invParent.setOriginalHeight(336);

		invBackground.setOriginalX(28);
		invBackground.setOriginalY(37);
		invBackground.setOriginalWidth(190);
		invBackground.setOriginalHeight(261);
		invBackground.setSpriteId(1031);

		invLeftColumn.setHidden(true);
		invRightColumn.setHidden(true);

		invBottomBarSprite.setOriginalWidth(246);
		invBottomBarSprite.setOriginalHeight(37);
		invBottomBarSprite.setSpriteId(1032);

		invBottomTabsParent.setOriginalX(2);

		invTopBarSprite.setOriginalY(298);
		invTopBarSprite.setOriginalWidth(249);
		invTopBarSprite.setOriginalHeight(38);
		invTopBarSprite.setSpriteId(1036);

		invTopTabsParent.setOriginalX(2);

		invViewportInterfaceController.setOriginalX(26+2);

	}

	public void resizeViewport(){
		Widget mainViewport = client.getWidget(161,91);
		if (mainViewport != null){
			//client.getWidget(161,94).setOriginalWidth(894);
			mainViewport.setOriginalWidth(249);
			Widget oldSchoolBoxParent = client.getWidget(161,94);
			Widget fixedViewport = client.getWidget(548,9);
			if (fixedViewport != null){
				log.info("fixed viewport exists, isHidden: {}, isSelfHidden: {}",fixedViewport.isHidden(),fixedViewport.isSelfHidden());
				//fixedViewport.setHidden(false);
			}
			else{
				log.info("fixedViewportNotFound");
			}
		//(Math.floor(0.5*mainViewport.getOriginalWidth()));
			//oldSchoolBoxParent.setXPositionMode(0);
			//oldSchoolBoxParent.setOriginalWidth(894);
		}
	}
}