package com.lapask;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FixedResizableHybridTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FixedResizableHybridPlugin.class);

		RuneLite.main(args);
	}
}