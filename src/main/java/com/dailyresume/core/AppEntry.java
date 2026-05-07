package com.dailyresume.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One running application that owns at least one visible window.
 *
 * <p>Mutable class (rather than a record) because we add window titles
 * incrementally as we walk through visible HWNDs.
 *
 * <p>{@code exePath} is the full path to the .exe (used by the launcher
 * to relaunch the app). May be null if Windows refused access — the
 * launcher falls back to letting Windows resolve the bare name.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppEntry {

    @JsonProperty("name")
    public String name;

    @JsonProperty("exe_path")
    public String exePath;

    @JsonProperty("window_titles")
    public List<String> windowTitles;

    @JsonProperty("pid")
    public Integer pid;

    /** No-arg constructor — required by Jackson when deserializing JSON. */
    public AppEntry() {}

    public AppEntry(String name, String exePath, List<String> windowTitles, Integer pid) {
        this.name = name;
        this.exePath = exePath;
        this.windowTitles = windowTitles;
        this.pid = pid;
    }
}
