package com.lapask;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("fixedresizablehybrid")
public interface FixedResizableHybridConfig extends Config
{
	@ConfigItem(
			keyName = "useSixteenByNine",
			name = "16:9 Aspect Ratio Resize",
			description = "Recommended to use with Stretched Mode Plugin at Scaling 100%. Reset this setting if your window size changes.",
			position = 1
	)
	default boolean useSixteenByNine()
	{
		return false;
	}
	@ConfigItem(
			keyName = "fillGapBorders",
			name = "Gap Borders",
			description = "For atypical aspect ratios or users who don't use stretched mode, this adds borders to the gap between the inventory and minimap.",
			position = 2
	)
	default boolean fillGapBorders()
	{
		return true;
	}
	@ConfigItem(
			keyName = "isWideChatbox",
			name = "Wide Chatbox + Viewport Centering",
			description = "Widens the chatbox to fit the entire width of the viewport. Also recenters the viewport depending on whether the chat is open or closed.",
			position = 2
	)
	default boolean isWideChatbox()
	{
		return false;
	}

}