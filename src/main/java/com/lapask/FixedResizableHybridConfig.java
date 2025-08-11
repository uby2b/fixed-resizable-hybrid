package com.lapask;

import com.lapask.config.OrbsPosition;
import com.lapask.config.ResizeBy;
import java.awt.Color;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("fixedresizablehybrid")
public interface FixedResizableHybridConfig extends Config
{
	@ConfigSection(
		name = "Window Resizing",
		description = "Automatic resizing settings",
		position = 0,
		closedByDefault = true
	)
	String resizingSettings = "resizingSettings";
	@ConfigSection(
		name = "Minimap Settings",
		description = "Settings for minimap appearance",
		position = 1,
		closedByDefault = true
	)
	String minimapSettings = "minimapSettings";
	@ConfigSection(
		name = "Inventory Minimap Gap",
		description = "Gap settings",
		position = 2,
		closedByDefault = true
	)
	String gapSettings = "gapSettings";

	@ConfigSection(
		name = "Wide Chatbox",
		description = "Wide chatbox settings",
		position = 3,
		closedByDefault = true
	)
	String wideChatboxSettings = "wideChatboxSettings";

	@ConfigItem(
		keyName = "aspectRatioResize",
		name = "Aspect Ratio Resize",
		description = "Recommended to use with Stretched Mode Plugin at Scaling 100%.<br>"
			+ "Reset this setting if your window size changes.",
		position = 1,
		section = resizingSettings
	)
	default boolean aspectRatioResize()
	{
		return false;
	}

	@ConfigItem(
		keyName = "resizeBy",
		name = "Resize By",
		description = "Defines whether the aspect ratio resize will calculate the new dimensions based on the original width or height.",
		position = 2,
		section = resizingSettings
	)
	default ResizeBy resizeBy()
	{
		return ResizeBy.WIDTH;
	}

	@ConfigItem(
		keyName = "aspectRatioWidthResize",
		name = "Aspect Ratio Width",
		description = "",
		position = 3,
		section = resizingSettings
	)
	default int aspectRatioWidthResize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "aspectRatioHeightResize",
		name = "Aspect Ratio Height",
		description = "",
		position = 4,
		section = resizingSettings
	)
	default int aspectRatioHeightResize()
	{
		return 9;
	}

	@ConfigItem(
		keyName = "orbsPosition",
		name = "Orb Positioning",
		description = "Allows for alternate minimap orb positioning.<br>"
			+"Fixed Mode = 1:1 replica of fixed mode<br>"
			+"More Clearance = Orbs moved outwards to prevent orb click-through on corners (e.g. run orb corner)",
		position = 1,
		section = minimapSettings
	)
	default OrbsPosition orbsPosition()
	{
		return OrbsPosition.FIXED_MODE;
	}

	@ConfigItem(
		keyName = "useGapBorders",
		name = "Gap Borders",
		description = "For atypical aspect ratios or users who don't use stretched mode, this adds borders to the gap between the inventory and minimap.",
		position = 1,
		section = gapSettings
	)
	default boolean useGapBorders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gapColor",
		name = "Gap Color",
		description = "Color used for the gap between the inventory and minimap.",
		position = 2,
		section = gapSettings
	)
	default Color gapColor() 
	{
		return new Color(47, 42, 32);
	}

@ConfigItem(
	keyName = "gapBorderColor",
	name = "Gap Border Color",
	description = "Color overlay applied on top of the gap border image (supports transparency).",
	position = 3,
	section = gapSettings
)	
	@Alpha
	default Color gapBorderColor() 
	{
		// Default: transparent
		return new Color(255, 255, 255, 0);
	}

	@ConfigItem(
		keyName = "isWideChatbox",
		name = "Wide Chatbox",
		description = "Widens the chatbox to fit the entire width of the viewport.<br>"
			+ "Centers chat buttons within the viewport & allows for viewport centering (see below)",
		position = 1,
		section = wideChatboxSettings
	)
	default boolean isWideChatbox()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatboxViewportCentering",
		name = "Viewport Centering",
		description = "Requires \"Wide Chatbox\" to be enabled.<br>"
			+ "Does not work if chatbox is transparent (ingame settings).<br>"
			+ "Recenters the viewport depending on whether the chat is open or closed.<br>"
			+ "If this is not enabled, viewport will remain the height of the window when chat is open.",
		position = 2,
		section = wideChatboxSettings
	)
	default boolean chatboxViewportCentering()
	{
		return true;
	}

	@ConfigItem(
		keyName = "centerChatboxButtons",
		name = "Center Chatbox Buttons",
		description = "Requires \"Wide Chatbox\" to be enabled.<br>"
			+ "Allows you to select between centering the chatbox buttons or stretching them out.<br>",
		position = 3,
		section = wideChatboxSettings
	)
	default boolean centerChatboxButtons()
	{
		return true;
	}
}