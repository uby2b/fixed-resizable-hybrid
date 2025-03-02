package com.lapask.config;

import lombok.Getter;

@Getter
public enum OrbsPosition
{
	FIXED_MODE("Fixed Mode"),
	MORE_CLEARANCE("More Clearance");

	private final String displayName;

	OrbsPosition(String displayName)
		{
			this.displayName = displayName;
		}

	@Override
	public String toString()
	{
		return displayName;
	}

}
