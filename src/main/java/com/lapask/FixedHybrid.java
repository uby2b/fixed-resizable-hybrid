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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.HotkeyListener;
import java.awt.*;
import java.util.*;
import java.util.List;

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

	private static final String CONFIG_GROUP_NAME = "fixedhybrid";

	private boolean resizeOnGameTick = false;

	boolean widgetsModified = false;

	private static int stretchedResizableScaling;

	private final HashMap<Integer, WidgetState> originalStates = new HashMap<>();

	@Provides
	FixedHybridConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FixedHybridConfig.class);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		//onGameTick only fires while logged in!
		if (resizeOnGameTick){
			Dimension configDimensions = calcSixteenByNineDimensions();
			resizeClient(configDimensions);
			resizeOnGameTick = false;
		}
	}
	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.resizeTrigger())
	{
		@Override
		public void hotkeyPressed()
		{
			log.info("hotkeyPressed");
			log.info("s{} r{}",client.getStretchedDimensions(),client.getRealDimensions());
		}
	};
	@Override
	protected void startUp() throws Exception
	{
		log.info("Fixed Hybrid Plugin started!");
		keyManager.registerKeyListener(hotkeyListener);
		if (client.getGameState() == GameState.LOGGED_IN) {
			queuePluginInitialization();
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Fixed Hybrid Plugin stopped!");
		keyManager.unregisterKeyListener(hotkeyListener);
		resetWidgets();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(CONFIG_GROUP_NAME))
		{
			return;
		}
		if (event.getKey().equals("useSixteenByNine") && config.useSixteenByNine()){
			clientThread.invoke(this::resizeSixteenByNine);

		}
	}


	@Subscribe
	public void onScriptPostFired(ScriptPostFired event) {
		int scriptId = event.getScriptId();
		// Fires when interface boxes are recalculated (e.g. bank inventory, settings panel, etc)
		if (scriptId == 909) {
			fixNestedInterfaceDimensions();
		}
		// Fires when right aligned minimap orbs are repositioned
		if (scriptId == 1699 || scriptId == 3305) {
			fixWorldMapWikiStoreActAdvOrbs();
		}
		// Fires when the inventory background is changed, reverting it back to its old sprite
		if (scriptId == 902) {
			fixInvBackground();
		}
		// Fires when Game Interface Mode changes
		if (scriptId == 901){
			gameClientLayoutChanged();
		}
	}
	// Calculates 16:9 dimensions based on the player's client height
	// Used in when config.useSixteenByNine() is enabled
	private Dimension calcSixteenByNineDimensions(){
		Dimension stretchedDimensions = client.getStretchedDimensions();
		//int resizableScalingPercent = configManager.getConfiguration("stretchedmode", "scalingFactor", Integer.class);
		Widget mainViewport = client.getWidget(161,34);
		if (mainViewport!=null && !mainViewport.isHidden()) {
			mainViewport.revalidateScroll();
			int currentHeight = stretchedDimensions.height;
			int calcWidth = 16 * currentHeight / 9;
			return new Dimension(calcWidth, currentHeight);
		}
		return null;
	}
	private void setResizableScaling(int resizableScalingPercent) {
		configManager.setConfiguration("stretchedmode", "scalingFactor", resizableScalingPercent);
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
			if (gameClientLayout!=-1) {
                if (gameClientLayout == 2) {
                    initializePlugin();
                }
                return true;
			}
			return false;
		});
	}
	private void resizeSixteenByNine(){
		if (config.useSixteenByNine()){
			Dimension newDimension = calcSixteenByNineDimensions();
			if (newDimension != null) {
				log.info("resizeSixteenByNine(). newDimension: {} x {}", newDimension.width,newDimension.height);
				resizeClient(newDimension);
			}
		}
	}

	// Initializes the plugin by modifying necessary widgets and creating custom sprites.
	// Ensures the minimap, inventory, and viewport are properly adjusted for fixed mode.
	// This function is invoked when the game client layout is determined to be valid.
	private void initializePlugin()
	{
		widgetsModified = true;
		repositionMinimapWidgets();
		createFixedSprites();
		resizeRenderViewport();
		resizeSixteenByNine();
	}

	// Overloaded function to allow for resetLast to be omitted.
	private void saveWidgetState(Widget widget) {
		saveWidgetState(widget,false);
	}

	// Saves the widget state under these conditions:
	// 1. The widget exists
	// 2. The widget has not already been saved
	//    - prevents overwriting of the vanilla state when functions are called more than once
	// The resetLast parameter is specified for the function resetWidgets() to allow for some saved widgets
	// to be reset after the others, preventing issue where parent widgets needs to be revalidated again.
	private void saveWidgetState(Widget widget, boolean resetLast) {
		if (widget == null) {
			return;
		}
		int widgetId = widget.getId();
		if (originalStates.containsKey(widgetId)) {
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
	private int getGameClientLayout(){
		if (client.getGameState() == GameState.LOGGED_IN){
			Widget classicResizableWidget = client.getWidget(InterfaceID.RESIZABLE_VIEWPORT,0);
			if (classicResizableWidget != null && !classicResizableWidget.isHidden()){
				return 2;
			}
			Widget modernResizableWidget = client.getWidget(InterfaceID.RESIZABLE_VIEWPORT_BOTTOM_LINE,0);
			if (modernResizableWidget!=null && !modernResizableWidget.isHidden()){
				return 3;
			}
			Widget classicFixedWidget = client.getWidget(InterfaceID.FIXED_VIEWPORT,0);
			if (classicFixedWidget != null && !classicFixedWidget.isHidden()){
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
	public void gameClientLayoutChanged(){
		int newGameClientLayout = getGameClientLayout();
		if (newGameClientLayout == 2){
			queuePluginInitialization();
		} else { //} else if (newGameClientLayout == 1 || newGameClientLayout == 3){
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
			if (storeOrb != null && storeOrb.getOriginalX() == 85) {
				saveWidgetState(storeOrb);
				setWidgetParameters(client.getWidget(160, 42), 0+13, 83-6, 34, 34, 2, 0, 0, 0);
			}
			if (activityAdviserOrb != null && activityAdviserOrb.getOriginalX() == 55) {
				saveWidgetState(activityAdviserOrb);
				setWidgetParameters(client.getWidget(160, 47), 0+13, 50-6, 34, 34, 2, 0, 0, 0);
			}
		}
	}

	// Runs from onScriptPostFired() for the scriptId == 909 which resets the bounding boxes of game interfaces (e.g.
	// banks, deposit boxes, settings, etc). This function sets those back to their modified states.
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
	// Runs from onScriptPostFired() for the script which fires and resets the inventory background sprite
	private void fixInvBackground(){
		if (widgetsModified && getGameClientLayout() == 2) {
			Widget invBackground = client.getWidget(161, 38);
			if (invBackground != null){
				if (invBackground.getSpriteId() == 897) {
					saveWidgetState(invBackground);
					invBackground.setSpriteId(1031);
				}
			}
		}
	}
	// Resets all modified widgets. See removeAddedWidgets() for how all plugin-created widgets are reset.
	// Runs whenever the user logs out, hops worlds, or changes the game client layout to something other than classic-resizable
	// There are a few widgets which need to be revalidated last to make sure everything resets properly, which is done last.
	public void resetWidgets() {
		removeAddedWidgets();
		resetRenderViewport();
		resetOriginalStates(); // sets widgetModified to false too
	}
	public void resetOriginalStates(){
		List<Map.Entry<Integer, WidgetState>> resetLastEntries = new ArrayList<>();

		// Iterate through the originalStates map
		for (Map.Entry<Integer, WidgetState> entry : originalStates.entrySet()) {
			int widgetId = entry.getKey();
			WidgetState state = entry.getValue();

			// Skip resetLast widgets for now, and adds to the list to reset last.
			if (state.isResetLast()) {
				resetLastEntries.add(entry);
				continue;
			}

			// Retrieves the widget and resets it
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

		// Revalidates reset widgets
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

		// Revalidate widgets with isResetLast == true
		clientThread.invoke(() -> {
			for (Map.Entry<Integer, WidgetState> entry : resetLastEntries) {
				Widget widget = client.getWidget(entry.getKey());
				if (widget != null) {
					widget.revalidateScroll();
				}
			}
		});

		// Clear the originalStates map after resetting, and sets the boolean to reflect that
		originalStates.clear();
		widgetsModified = false;
	}

	// Removes all widgets that plugin created (sprites surrounding the minimap/inventory)
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
	// Resizes the client to the specified dimension. For some reason, the runelite client doesn't update the config
	//   when the client is resized via dragging edges. To work around this, it's first set 1 pixel wider than the
	//   dimensions passed, and then resized again after the client's next game tick.
	//
	//   Repurposed from the Client Resizer Plugin
	private void resizeClient(Dimension dimension) {
		// Validate and adjust the dimensions
		int processedWidth = Math.max(Math.min(dimension.width, 7680), Constants.GAME_FIXED_WIDTH);
		int processedHeight = Math.max(Math.min(dimension.height, 2160), Constants.GAME_FIXED_HEIGHT);
		Dimension processedGameSize = new Dimension(processedWidth, processedHeight);
		Dimension currentSize = configManager.getConfiguration("runelite", "gameSize", Dimension.class);
		if (processedGameSize.equals(currentSize)){
			Dimension processedGameSizePlus1 = new Dimension(processedWidth + 1, processedHeight);
			log.info("Resized to {} x {}", processedWidth+1, processedHeight);
			configManager.setConfiguration("runelite", "gameSize", processedGameSizePlus1);
			resizeOnGameTick = true;
		}
		if (!processedGameSize.equals(currentSize)){
			log.info("Resized to {} x {}", processedWidth,processedHeight);
			configManager.setConfiguration("runelite", "gameSize", processedGameSize);
			resizeOnGameTick = false;
		}

	}
	// Sets a widget's coordinates, overloaded to be able to accept both ComponentIDs or the widget directly
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
	// Expanded version of the setWidgetCoordinates() function to accept more parameters
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
	// Positions the all the minimap elements to align with fixed mode, and saves the original widget parameters for
	//     resetWidgets() later. Could definitely be cleaned up.
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

	public void createFixedSprites() {
		// Get the parent widget the sprites should be under
		Widget minimapParentWidget = client.getWidget(161, 22);
		Widget inventoryParentWidget = client.getWidget(161,97);
		// Define the configurations for all the sprites to be created.
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
			//Ensure the bounds on the parent container(s) are properly prepared.
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
	// Sets up the widget coordinates and bounds on the inventory panel prior to creating the fixed mode sprites and
	//     modifying the existing inventory sprites.
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
	// Resizes the main viewport of the game so that no rendering occurs underneath the minimap/inventory.
	// This also subsequently centers the camera properly, one of my main annoyances with the original resizable mode
	public void resizeRenderViewport(){
		Widget mainViewport = client.getWidget(161,91);
		if (mainViewport != null){
			saveWidgetState(mainViewport);
			// Width is set to the width of the inventory and minimap widgets because widthMode = 1 (subtracts
			//     that value from the parent widget's dimensions).
			mainViewport.setOriginalWidth(249);
			mainViewport.revalidate();
		}
	}
	// Reset's the plugins changes on the render viewport back to the original full screen resizable mode.
	// Called during the resetWidgets() function.
	public void resetRenderViewport(){
		Widget mainViewport = client.getWidget(161,91);
		if (mainViewport != null){
			clientThread.invoke(()-> {
				mainViewport.setOriginalWidth(0);
				mainViewport.revalidateScroll();
			});
		}
	}
}