package com.lapask;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("fixedhybrid")
public interface FixedHybridConfig extends Config
{
	@ConfigItem(
			keyName = "enableResize",
			name = "Enable Resize",
			description = "Enable for proper window size",
			position = 1
	)
	default boolean enableResize()
	{
		return false;
	}
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
			keyName = "clientWidth",
			name = "Client Width",
			description = "Set the desired width of the client",
			position = 3
	)
	default int clientWidth()
	{
		return 894;
	}

	@ConfigItem(
			keyName = "clientHeight",
			name = "Client Height",
			description = "Set the desired height of the client",
			position = 4
	)
	default int clientHeight()
	{
		return 503;
	}

	@ConfigItem(
			keyName = "keepSixteenByNine",
			name = "Keep 16:9 Aspect Ratio",
			description = "Set's the aspect ratio of the window size based on Client Height to be 16:9 (good for 1920x1080 monitors)",
			position = 5
	)
	default boolean keepSixteenByNine()
	{
		return true;
	}
}