package com.lapask;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;
import java.awt.*;
import java.util.*;
import java.util.List;

import net.runelite.api.events.GameStateChanged;

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

	private boolean resizeOnGameTick = false;

	boolean widgetsModified = false;

	boolean testBool = false;

	private final HashMap<Integer, WidgetState> originalStates = new HashMap<>();

	private void saveWidgetState(Widget widget) {
		saveWidgetState(widget,false);
	}
	private void saveWidgetState(Widget widget, boolean resetLast) {
		if (widget == null) {
			return;
		}

		int widgetId = widget.getId();
		// Check if this widget's state has already been saved
		if (originalStates.containsKey(widgetId)) {
			return;
		}

		// Save the current state of the widget
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
	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.resizeTrigger())
	{
		@Override
		public void hotkeyPressed()
		{
			log.info("hotkeyPressed");
		}
	};
	@Override
	protected void startUp() throws Exception
	{
		log.info("Fixed Hybrid Plugin started!");
		GameState gameState = client.getGameState();
		keyManager.registerKeyListener(hotkeyListener);
		if (client.getGameState() == GameState.LOGGED_IN && !widgetsModified) {
			queueUpdateAllOverrides();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Fixed Hybrid Plugin stopped!");
		keyManager.unregisterKeyListener(hotkeyListener);
		resetWidgets();
	}
	private Dimension calcSixteenByNineDimensions(){
		Dimension currentSize = configManager.getConfiguration("runelite", "gameSize", Dimension.class);
		int currentHeight = currentSize.height;
		int calcWidth = 16*currentHeight/9;
        return new Dimension(calcWidth,currentHeight);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState gameState = gameStateChanged.getGameState();
		if (gameState == GameState.LOGGED_IN && !widgetsModified)
		{
			queueUpdateAllOverrides();
		} else if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING && widgetsModified){
			resetWidgets();
			widgetsModified = false;
		}
	}
	// returns 1 for fixed mode
	// returns 2 for resizeable - classic mode
	// returns 3 for resizeable - modern mode
	// returns -1 if it can't determine mode
	private int getGameClientLayout(){
		if (client.getGameState() == GameState.LOGGED_IN){
			Widget classicResizableWidget = client.getWidget(InterfaceID.RESIZABLE_VIEWPORT,0);
			if (classicResizableWidget != null && !classicResizableWidget.isHidden()){
				log.info("gameClientLayout = 2");
				return 2;
			}
			Widget modernResizableWidget = client.getWidget(InterfaceID.RESIZABLE_VIEWPORT_BOTTOM_LINE,0);
			if (modernResizableWidget!=null && !modernResizableWidget.isHidden()){
				log.info("gameClientLayout = 3");
				return 3;
			}
			Widget classicFixedWidget = client.getWidget(InterfaceID.FIXED_VIEWPORT,0);
			if (classicFixedWidget != null && !classicFixedWidget.isHidden()){
				log.info("gameClientLayout = 1");
				return 1;
			}
		}
		log.info("gameClientLayout = -1");
		return -1;
	}
//	public int getGameClientLayout(){
//		Widget gameClientLayoutParent = client.getWidget(116,27);
//		boolean resized = client.isResized();
//		if (gameClientLayoutParent == null){
//			return -1;
//		}
//		if (!resized){
//			return 1;
//		}
//		Widget[] gameClientLayoutChildren = gameClientLayoutParent.getDynamicChildren();
//		for (Widget child:gameClientLayoutChildren){
//			String childText = child.getText();
//			if (childText!=null){
//				if (childText.equals("Resizable - Classic layout")){
//					return 2;
//				} else if (childText.equals("Resizable - Modern layout")) {
//					return 3;
//				}
//			}
//		}
//		return -1;
//	}
//	@Subscribe
//	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
//	{
//		Widget widget = client.getWidget(widgetLoaded.getGroupId(),0);
//		if (widget != null){
//			//log.info("Widget Loaded, groupId = {}, widgetId = {}",widgetLoaded.getGroupId(),widget.getId());
//			Widget oldSchoolBox = client.getWidget(161,15);
//			if (oldSchoolBox!=null){
//				int widgetParentId = widget.getParentId();
//				Widget[] oldSchoolBoxChildren = oldSchoolBox.getStaticChildren();
//				for (Widget osbChild : oldSchoolBoxChildren) {
//					if (osbChild.getId() == widgetParentId) {
//						log.info("Widget Loaded, revalidated Scroll");
//						osbChild.revalidateScroll();
//						//log.info("origX/Y:{}/{}  origW/H:{}/{}  X/YMode:{}/{}  W/HMode{}/{}", widget.getOriginalX(),widget.getOriginalY(),widget.getOriginalWidth(),widget.getOriginalHeight(),widget.getXPositionMode(),widget.getYPositionMode(),widget.getWidthMode(),widget.getHeightMode());
////						boolean widgetChanged = false;
////						if (widget.getWidthMode() == 1){
////							widget.setWidthMode(0);
////							widget.setOriginalWidth(osbChild.getWidth());
////							widgetChanged = true;
////						}
////						if (widget.getHeightMode() == 1 && widget.getOriginalHeight() == 0) {
////							widget.setHeightMode(0);
////							widget.setOriginalHeight(osbChild.getHeight());
////							widgetChanged = true;
////						}
////						if (widget.getXPositionMode() == 1){
////							widget.setXPositionMode(0);
////							if (widget.getOriginalX()!=0){
////								widget.setOriginalX(osbChild.getOriginalWidth()/2);
////							}
////							widgetChanged = true;
////						}
////						if (widget.getYPositionMode() == 1){
////							widget.setYPositionMode(0);
////							if (widget.getOriginalY()!=0){
////								widget.setOriginalY(osbChild.getOriginalHeight()/2);
////							}
////							widgetChanged = true;
////						}
////						if (widgetChanged) {
////							widget.revalidate();
////						}
//					}
//				}
//			}
//		}
//	}

	@Provides
	FixedHybridConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FixedHybridConfig.class);
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


	private void queueUpdateAllOverrides()
	{
		clientThread.invokeLater(() ->
		{
			// Cross sprites and widget sprite cache are not setup until login screen
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return false;
			}
			int gameClientLayout = getGameClientLayout();
			if (gameClientLayout!=-1) {
				updateAllOverrides();
				if (config.keepSixteenByNine()){
					resizeClient(calcSixteenByNineDimensions());
				}
				return true;
			}
			return false;
		});
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		int scriptId = event.getScriptId();
		if (scriptId == 909) {
			fixNestedInterfaceDimensions();
			fixInvBackground();
		}
		if (scriptId == 1699 || scriptId == 3305) {
			fixWorldMapWikiStoreActAdvOrbs();
		}
		// Fires when the inventory background is changed, reverting it back to its old sprite
		if (scriptId == 113) {
			fixInvBackground();
		}
		// Fires when Game Interface Mode changes
		if (scriptId == 901){ //3998
			gameClientLayoutChanged();
		}
	}
	public void gameClientLayoutChanged(){
		int newGameClientLayout = getGameClientLayout();
		if (newGameClientLayout == 2){
			queueUpdateAllOverrides();
		} else if (newGameClientLayout == 1 || newGameClientLayout == 3){
			resetWidgets();
		}
	}
	// Detects the scripts which will occasionally reset the positions of the world map orb and wiki banner, and resets their modified positions to matched fixed mode.
	// Script 1699 = [clientscript,orbs_worldmap_setup]
	// Script 3305 = 3305 [clientscript,wiki_icon_update]
	private void fixWorldMapWikiStoreActAdvOrbs(){
		if (getGameClientLayout() == 2) {
			Widget worldMapOrb = client.getWidget(160, 48);
			Widget wikiBanner = client.getWidget(ComponentID.MINIMAP_WIKI_BANNER_PARENT);
			Widget storeOrb = client.getWidget(160, 42);
			Widget activityAdviserOrb = client.getWidget(160, 47);

			if (worldMapOrb != null && worldMapOrb.getOriginalX() == 0) {
				saveWidgetState(worldMapOrb);
				setWidgetCoordinates(worldMapOrb, 23, 109);
			}
			if (wikiBanner != null && wikiBanner.getOriginalX() == 0) {
				saveWidgetState(wikiBanner);
				setWidgetCoordinates(wikiBanner, 21, 129);
			}
			if (storeOrb != null && storeOrb.getOriginalX() != 13) {
				saveWidgetState(storeOrb);
				setWidgetParameters(client.getWidget(160, 42), 0+13, 83-6, 34, 34, 2, 0, 0, 0);
			}
			if (activityAdviserOrb != null && activityAdviserOrb.getOriginalX() != 13) {
				saveWidgetState(activityAdviserOrb);
				setWidgetParameters(client.getWidget(160, 47), 0+13, 50-6, 34, 34, 2, 0, 0, 0);
			}
		}
	}
	private void fixNestedInterfaceDimensions(){
		if (widgetsModified) {
			Widget clickWindow = client.getWidget(161, 92);
			if (clickWindow != null) {
				clickWindow.setXPositionMode(0);
				clickWindow.revalidate();
				Widget[] clickWindowSChildren = clickWindow.getStaticChildren();
				for (Widget clickWindowSChild : clickWindowSChildren) {
					if (clickWindowSChild.getOriginalWidth() == 250)
					{
						clickWindowSChild.setOriginalWidth(0);
					}
					clickWindowSChild.revalidateScroll();
				}
			}
			Widget oldSchoolBoxParent = client.getWidget(161, 94);
			if (oldSchoolBoxParent != null && oldSchoolBoxParent.getXPositionMode() == 1){
				oldSchoolBoxParent.setXPositionMode(0);
				oldSchoolBoxParent.revalidate();
			}
			Widget oldSchoolBox = client.getWidget(161, 15);
			if (oldSchoolBox != null){
				if (oldSchoolBox.getOriginalWidth() == 250){
					oldSchoolBox.setOriginalWidth(0);
					oldSchoolBox.revalidate();
				}
				Widget[] oldSchoolBoxChildren = oldSchoolBox.getStaticChildren();
				for (Widget oldSchoolBoxChild : oldSchoolBoxChildren){
					oldSchoolBoxChild.revalidateScroll();
				}
			}
		}
	}
	private void fixInvBackground(){
		if (widgetsModified && client.isResized()) {
			Widget invBackground = client.getWidget(161, 38);
			if (invBackground != null){
				if (invBackground.getSpriteId() == 897) {
					saveWidgetState(invBackground);
					invBackground.setSpriteId(1031);
				}
			}
		}
	}
	@Subscribe
	public void onBeforeRender(final BeforeRender event)
	{

	}
	private void updateAllOverrides()
	{
		widgetsModified = true;
		repositionMinimapWidgets();
		showFixedSprites();
		resizeRenderViewport();
	}
	public void resetWidgets() {
		removeAddedWidgets();
		resetRenderViewport();
		List<Map.Entry<Integer, WidgetState>> resetLastEntries = new ArrayList<>();

		// Iterate through the originalStates map
		for (Map.Entry<Integer, WidgetState> entry : originalStates.entrySet()) {
			int widgetId = entry.getKey();
			WidgetState state = entry.getValue();

			if (state.isResetLast()) {
				resetLastEntries.add(entry); // Skip for now, add to the list to reset later
				continue;
			}

			// Retrieve the widget and reset it
			Widget widget = client.getWidget(widgetId);
			if (widget != null) {
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

		// Revalidate widgets for isResetLast == false
		clientThread.invoke(() -> {
			for (Map.Entry<Integer, WidgetState> entry : originalStates.entrySet()) {
				if (!entry.getValue().isResetLast()) {
					Widget widget = client.getWidget(entry.getKey());
					if (widget != null) {
						widget.revalidateScroll();
					}
				}
			}
		});

		// Process widgets with isResetLast() set to true
		for (Map.Entry<Integer, WidgetState> entry : resetLastEntries) {
			int widgetId = entry.getKey();
			WidgetState state = entry.getValue();

			// Retrieve the widget and reset it
			Widget widget = client.getWidget(widgetId);
			if (widget != null) {
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

		// Revalidate widgets for isResetLast == true
		clientThread.invoke(() -> {
			for (Map.Entry<Integer, WidgetState> entry : resetLastEntries) {
				Widget widget = client.getWidget(entry.getKey());
				if (widget != null) {
					widget.revalidateScroll();
				}
			}
		});

		// Clear the originalStates map after resetting
		originalStates.clear();
		widgetsModified = false;
	}
	public void removeAddedWidgets(){
		Widget minimapDynamicParent = client.getWidget(161,22);
		if (minimapDynamicParent!=null){
			minimapDynamicParent.deleteAllChildren();
		}
		Widget invDynamicParent = client.getWidget(161,97);
		if (invDynamicParent!=null){
			invDynamicParent.deleteAllChildren();
		}

	}
	public void testWidget(){
		Widget testWidget = client.getWidget(161, 18);
		if (testWidget != null) {
			// Dynamic children
			Widget[] dynamicChildren = testWidget.getDynamicChildren();
			if (dynamicChildren != null) {
				log.info("dynamicChildren != null");
				log.info("length of Widget[] dynamicChildren = {}", dynamicChildren.length);
				for (int i = 0; i < dynamicChildren.length; i++) {
					log.info("dynamicChildren[{}].getId() = {}", i, dynamicChildren[i].getId());
				}
			} else {
				log.info("dynamicChildren == null");
			}



			// Static children
			Widget[] staticChildren = testWidget.getStaticChildren();
			if (staticChildren != null) {
				log.info("staticChildren != null");
				log.info("length of Widget[] staticChildren = {}", staticChildren.length);
				for (int i = 0; i < staticChildren.length; i++) {
					log.info("staticChildren[{}].getId() = {}", i, staticChildren[i].getId());
				}
			} else {
				log.info("staticChildren == null");
			}
			// Children
			Widget[] children = testWidget.getChildren();
			if (children != null) {
				log.info("children != null");
				log.info("length of Widget[] children = {}", children.length);
				for (int i = 0; i < children.length; i++) {
					log.info("children[{}].getId() = {}", i, children[i].getId());
				}
			} else {
				log.info("children == null");
			}

			// First child
			Widget firstChild = testWidget.getChild(0);
			if (firstChild != null) {
				log.info("firstChild != null");
				log.info("firstChild.getId() = {}", firstChild.getId());
			} else {
				log.info("firstChild == null");
			}
			// Nested children - must run on the client thread
			clientThread.invoke(() -> {
				Widget[] nestedChildren = testWidget.getNestedChildren();
				if (nestedChildren != null) {
					log.info("nestedChildren != null");
					log.info("length of Widget[] nestedChildren = {}", nestedChildren.length);
					for (int i = 0; i < nestedChildren.length; i++) {
						log.info("nestedChildren[{}].getId() = {}, parentId = {}", i, nestedChildren[i].getId(),nestedChildren[i].getParentId());
					}
				} else {
					log.info("nestedChildren == null");
				}
			});
			Widget settingsPanel = client.getWidget(134,0);
			log.info("settingsPanel.getParentId() = {}",settingsPanel.getParentId());
		}
	}
	/**
	 * Resizes the client to the specified dimension.
	 */
	private void resizeClient(Dimension dimension) {
//		if (client.getGameState() != GameState.LOGGED_IN) {
//			return;
//		}

		// Validate and adjust the dimensions
		int processedWidth = Math.max(Math.min(dimension.width, 7680), Constants.GAME_FIXED_WIDTH);
		int processedHeight = Math.max(Math.min(dimension.height, 2160), Constants.GAME_FIXED_HEIGHT);
		Dimension processedGameSize = new Dimension(processedWidth, processedHeight);
		Dimension currentSize = configManager.getConfiguration("runelite", "gameSize", Dimension.class);
		if (processedGameSize.equals(currentSize)){
			Dimension processedGameSizePlus1 = new Dimension(processedWidth + 1, processedHeight);
			configManager.setConfiguration("runelite", "gameSize", processedGameSizePlus1);
			resizeOnGameTick = true;
		}
		if (!processedGameSize.equals(currentSize)){
			configManager.setConfiguration("runelite", "gameSize", processedGameSize);
			//redundancy
			resizeOnGameTick = false;
		}
	}

	public void setWidgetCoordinates(int componentId, int newX, int newY) {
		setWidgetCoordinates(client.getWidget(componentId),newX,newY);
	}
	public void setWidgetCoordinates(Widget widget, int newX, int newY) {
		if (widget != null){
			saveWidgetState(widget);
			widget.setOriginalX(newX);
			widget.setOriginalY(newY);
			widget.revalidateScroll();
		}
	}
	public void setWidgetParameters(Widget widget,
									 int newX,
									 int newY,
									 int newOriginalWidth,
									 int newOriginalHeight,
									 int newXPositionMode,
									 int newYPositionMode,
									 int newWidthMode,
									 int newHeightMode) {
		if (widget != null) {
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
	public void repositionMinimapWidgets(){
		Widget minimapWidget = client.getWidget(161,95);
		Widget minimapSprite = client.getWidget(161, 32);
		Widget minimapWidgetOrbsParent = client.getWidget(161,33);
		Widget minimapWidgetOrbsInterface = client.getWidget(160,0);
		if (client.isResized() &&
				minimapWidget != null &&
				minimapSprite != null &&
				minimapWidgetOrbsParent != null &&
				minimapWidgetOrbsInterface != null)
		{
			saveWidgetState(minimapWidget,true);
			saveWidgetState(minimapSprite);
			saveWidgetState(minimapWidgetOrbsInterface);
			saveWidgetState(minimapWidgetOrbsParent);

			minimapSprite.setHidden(true);

			minimapWidget.setOriginalWidth(249);
			minimapWidget.setOriginalHeight(207);
			minimapWidget.revalidate();

			minimapWidgetOrbsParent.setOriginalWidth(249);
			minimapWidgetOrbsParent.setOriginalHeight(197);
			minimapWidgetOrbsParent.revalidate();

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
			for (int[] adjustment : minimapViewportAdjustment) {
				int childId = adjustment[0];
				int newX = adjustment[1];
				int newY = adjustment[2];

				Widget wdgToAdj = client.getWidget(161, childId);
				if (wdgToAdj != null && wdgToAdj.getXPositionMode() == 2) {
					saveWidgetState(wdgToAdj,true);
					// Set the position mode to absolute
					wdgToAdj.setXPositionMode(0);

					// Set the absolute coordinates using setWidgetCoordinates
					setWidgetCoordinates(wdgToAdj, newX, newY);
					wdgToAdj.revalidate();
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
			//compass orb clickbox
			setWidgetCoordinates(client.getWidget(161, 31), 26, 1);
			//compass orb viewbox
			setWidgetCoordinates(client.getWidget(161, 29), 28, 3);
			//world map orb, wiki banner, store orb, and activity adviser orb all handled under this function
			fixWorldMapWikiStoreActAdvOrbs();
		}
	}
	public void showFixedSprites() {
		// Get the parent widget the sprites should be under
		Widget minimapParentWidget = client.getWidget(161, 22);
		Widget inventoryParentWidget = client.getWidget(161,97);
		// Define the configurations for all the sprites
		// Each row represents a sprite with the following columns:
		// [groupId, childId, type, spriteId, originalX, originalY, originalWidth, originalHeight, xPositionMode, yPositionMode, widthMode, heightMode]
		int[][] newSpriteConfigs = {
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
			for (int[] newSpriteConfig : newSpriteConfigs) {
				Widget parentWidget = client.getWidget(newSpriteConfig[0],newSpriteConfig[1]);
				Widget minimapSprite = parentWidget.createChild(newSpriteConfig[2]);
				minimapSprite.setSpriteId(newSpriteConfig[3]);
				minimapSprite.setOriginalX(newSpriteConfig[4]);
				minimapSprite.setOriginalY(newSpriteConfig[5]);
				minimapSprite.setOriginalWidth(newSpriteConfig[6]);
				minimapSprite.setOriginalHeight(newSpriteConfig[7]);
				minimapSprite.setXPositionMode(newSpriteConfig[8]);
				minimapSprite.setYPositionMode(newSpriteConfig[9]);
				minimapSprite.setWidthMode(newSpriteConfig[10]);
				minimapSprite.setHeightMode(newSpriteConfig[11]);
				if (newSpriteConfig[11] == 1){
					minimapSprite.setNoClickThrough(true);
				}
				minimapSprite.getParent().revalidate();
				minimapSprite.revalidate();
			}
		}
	}
	public void inventoryWidgetBoundsFix() {
		Widget invParent = client.getWidget(161,97);
		if (invParent != null) {
			saveWidgetState(invParent,true);
			invParent.setOriginalWidth(249);
			invParent.setOriginalHeight(336);
			invParent.revalidate();
		}

		Widget invBackground = client.getWidget(161, 38);
		if (invBackground != null) {
			saveWidgetState(invBackground);
			invBackground.setOriginalX(28);
			invBackground.setOriginalY(37);
			invBackground.setOriginalWidth(190);
			invBackground.setOriginalHeight(261);
			invBackground.setSpriteId(1031);
			invBackground.revalidate();
		}

		Widget invLeftColumn = client.getWidget(161, 39);
		if (invLeftColumn != null) {
			saveWidgetState(invLeftColumn);
			invLeftColumn.setHidden(true);
			invLeftColumn.revalidate();
		}
		Widget invRightColumn = client.getWidget(161, 40);
		if (invRightColumn != null) {
			saveWidgetState(invRightColumn);
			invRightColumn.setHidden(true);
			invRightColumn.revalidate();
		}

		Widget invBottomBarSprite = client.getWidget(161, 41);
		if (invBottomBarSprite != null) {
			saveWidgetState(invBottomBarSprite);
			invBottomBarSprite.setOriginalWidth(246);
			invBottomBarSprite.setOriginalHeight(37);
			invBottomBarSprite.setSpriteId(1032);
			invBottomBarSprite.revalidate();
		}

		Widget invBottomTabsParent = client.getWidget(161, 42);
		if (invBottomTabsParent != null) {
			saveWidgetState(invBottomTabsParent,true);
			invBottomTabsParent.setOriginalX(2);
			invBottomTabsParent.revalidate();
		}

		Widget invTopBarSprite = client.getWidget(161, 57);
		if (invTopBarSprite != null) {
			saveWidgetState(invTopBarSprite);
			invTopBarSprite.setOriginalY(298);
			invTopBarSprite.setOriginalWidth(249);
			invTopBarSprite.setOriginalHeight(38);
			invTopBarSprite.setSpriteId(1036);
			invTopBarSprite.revalidate();
		}

		Widget invTopTabsParent = client.getWidget(161, 58);
		if (invTopTabsParent != null) {
			saveWidgetState(invTopTabsParent,true);
			invTopTabsParent.setOriginalX(2);
			invTopTabsParent.revalidate();
		}

		Widget invViewportInterfaceController = client.getWidget(161, 73);
		if (invViewportInterfaceController != null) {
			saveWidgetState(invViewportInterfaceController);
			invViewportInterfaceController.setOriginalX(26 + 2);
			invViewportInterfaceController.revalidate();
		}
	}
//	public void inventoryWidgetBoundsFix(){
//		Widget invParent = client.getWidget(161, 97);
//		Widget invBackground = client.getWidget(161,38);
//		Widget invLeftColumn = client.getWidget(161,39);
//		Widget invRightColumn = client.getWidget(161,40);
//		Widget invBottomBarSprite = client.getWidget(161,41);
//		Widget invBottomTabsParent = client.getWidget(161,42);
//		Widget invTopBarSprite = client.getWidget(161,57);
//		Widget invTopTabsParent = client.getWidget(161,58);
//		Widget invViewportInterfaceController = client.getWidget(161,73);
//
//		saveInventoryWidgetStates();
//
//		invParent.setOriginalWidth(249);
//		invParent.setOriginalHeight(336);
//		invParent.revalidate();
//
//		invBackground.setOriginalX(28);
//		invBackground.setOriginalY(37);
//		invBackground.setOriginalWidth(190);
//		invBackground.setOriginalHeight(261);
//		invBackground.setSpriteId(1031);
//		invBackground.revalidate();
//
//		invLeftColumn.setHidden(true);
//		invRightColumn.setHidden(true);
//		invLeftColumn.revalidate();
//		invRightColumn.revalidate();
//
//		invBottomBarSprite.setOriginalWidth(246);
//		invBottomBarSprite.setOriginalHeight(37);
//		invBottomBarSprite.setSpriteId(1032);
//		invBottomBarSprite.revalidate();
//
//		invBottomTabsParent.setOriginalX(2);
//		invBottomTabsParent.revalidate();
//
//		invTopBarSprite.setOriginalY(298);
//		invTopBarSprite.setOriginalWidth(249);
//		invTopBarSprite.setOriginalHeight(38);
//		invTopBarSprite.setSpriteId(1036);
//		invTopBarSprite.revalidate();
//
//		invTopTabsParent.setOriginalX(2);
//		invTopTabsParent.revalidate();
//
//		invViewportInterfaceController.setOriginalX(26+2);
//		invViewportInterfaceController.revalidate();
//
//	}
	public void resizeRenderViewport(){
		Widget mainViewport = client.getWidget(161,91);
		if (mainViewport != null){
			saveWidgetState(mainViewport);
			mainViewport.setOriginalWidth(249);
			mainViewport.revalidate();
		}
	}
	public void resetRenderViewport(){
		Widget mainViewport = client.getWidget(161,91);
		if (mainViewport != null){
			clientThread.invoke(()-> {
				mainViewport.setOriginalWidth(0);
				mainViewport.revalidate();
			});
		}
	}
}


//@Subscribe
//public void onScriptPostFired(ScriptPostFired event) {
//	if (widgetsModified) {
//		int scriptId = event.getScriptId();
//		if (scriptId == 909) {
//			Widget interfacesParent = client.getWidget(161, 94);
//			Widget oldSchoolBox = client.getWidget(161, 15);
//			Widget clickWindow = client.getWidget(161, 92);
//			if (clickWindow != null) {
//				clickWindow.setXPositionMode(0);
//				Widget[] clickWindowSChildren = clickWindow.getStaticChildren();
//				for (Widget clickWindowSChild : clickWindowSChildren) {
//					//log.info("checking widget {}, getWidth = {}",clickWindowSChild.getId(), clickWindowSChild.getOriginalWidth());
//					if (clickWindowSChild != null &&
//							clickWindowSChild.getOriginalWidth() == 250)
//					{
//						clickWindowSChild.setOriginalWidth(0);
//						clickWindowSChild.revalidate();
//					}
//				}
//			}
//			if (interfacesParent != null && oldSchoolBox != null) {
//				if (interfacesParent.getXPositionMode() == 1 || oldSchoolBox.getOriginalWidth() == 250) {
//					interfacesParent.setXPositionMode(0);
//					oldSchoolBox.setOriginalWidth(0);
//					interfacesParent.revalidate();
//					oldSchoolBox.revalidate();
//				}
//				Widget[] oldSchoolBoxChildren = oldSchoolBox.getStaticChildren();
//				for (Widget oldSchoolBoxChild : oldSchoolBoxChildren){
//					if (oldSchoolBoxChild != null) {
//						oldSchoolBoxChild.setOriginalX(0);
//						oldSchoolBoxChild.revalidate();
//					}
//				}
//			}
//		}
//		// Detects the scripts which will occasionally reset the positions of the world map orb and wiki banner, and resets their modified positions to matched fixed mode.
//		// Script 1699 = [clientscript,orbs_worldmap_setup]
//		// Script 3305 = 3305 [clientscript,wiki_icon_update]
//		if (scriptId == 1699 || scriptId == 3305) { //1699
//			Widget worldMapOrb = client.getWidget(160, 48);
//			Widget wikiBanner = client.getWidget(ComponentID.MINIMAP_WIKI_BANNER_PARENT);
//			if (worldMapOrb != null &&
//					wikiBanner != null &&
//					(worldMapOrb.getOriginalX() == 0 || wikiBanner.getOriginalX() == 0)
//			) {
//				setWidgetCoordinates(worldMapOrb, 23, 109);
//				setWidgetCoordinates(wikiBanner, 21, 129);
//			}
//		}
//	}
//}