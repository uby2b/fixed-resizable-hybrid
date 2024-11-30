package com.lapask.config;

import lombok.Getter;

@Getter
public enum ResizeBy
{
    HEIGHT("Height"),
    WIDTH("Width");

    private final String displayName;

    ResizeBy(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
