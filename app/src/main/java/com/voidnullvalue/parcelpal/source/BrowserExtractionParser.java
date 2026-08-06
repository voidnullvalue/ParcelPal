package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import java.io.IOException;

/**
 * Converts the JSON contract returned by a browser extraction script into the common model.
 *
 * <p>Every extraction script returns a single JSON object with at least {@code ready} and
 * {@code challenge} flags; the rest of the shape is specific to the carrier's page. Keeping this
 * contract separate from {@link BrowserSource} lets each parser be unit tested against captured
 * fixtures without an Android runtime.
 */
public interface BrowserExtractionParser {
    TrackingResult parse(String json, TrackingTarget target, String sourceId, String sourceName) throws IOException;
}
