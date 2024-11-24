package com.lapask;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("fixedresizablehybrid")
public interface FixedResizableHybridConfig extends Config
{
	@ConfigItem(
			keyName = "resizeTrigger",
			name = "Resize Trigger",
			description = "Change this value to trigger the client resize",
			position = 2
	)
	default Keybind resizeTrigger()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "useSixteenByNine",
			name = "16:9 Aspect Ratio Resize",
			description = "Recommended to use with Stretched Mode Plugin at Scaling 100%. Reset this setting if your window size changes.",
			position = 3
	)
	default boolean useSixteenByNine()
	{
		return false;
	}

}