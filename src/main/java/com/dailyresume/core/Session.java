package com.dailyresume.core;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The whole snapshot we write to data/session_*.json.
 *
 * <p>Holds <strong>when</strong> we captured (capturedAt), the
 * <strong>history lookback window</strong> we used, and the two payloads:
 * the apps the user had open and the URLs from their browser history.
 */
public class Session {

    @JsonProperty("captured_at")
    public String capturedAt;

    @JsonProperty("lookback_since")
    public String lookbackSince;

    @JsonProperty("apps")
    public List<AppEntry> apps;

    @JsonProperty("visits")
    public List<Visit> visits;

    public Session() {}
}
