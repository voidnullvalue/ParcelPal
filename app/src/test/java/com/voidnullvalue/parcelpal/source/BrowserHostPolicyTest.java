package com.voidnullvalue.parcelpal.source;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BrowserHostPolicyTest {
    private final BrowserHostPolicy usps = new BrowserHostPolicy(Collections.singletonList("usps.com"));

    @Test public void allowsTheCarrierHostAndItsSubdomains() {
        assertTrue(usps.isAllowed("https://tools.usps.com/go/TrackConfirmAction?tLabels=123"));
        assertTrue(usps.isAllowed("https://usps.com/"));
        assertTrue(usps.isAllowed("https://www.usps.com/assets/app.js"));
    }

    @Test public void blocksEveryOtherHost() {
        assertFalse(usps.isAllowed("https://usps.com.evil.example/track"));
        assertFalse(usps.isAllowed("https://notusps.com/track"));
        assertFalse(usps.isAllowed("https://google-analytics.com/collect"));
        assertFalse(usps.isAllowed("https://parcelsapp.com/en"));
    }

    @Test public void blocksCleartextAndMalformedUrls() {
        assertFalse(usps.isAllowed("http://tools.usps.com/go/TrackConfirmAction"));
        assertFalse(usps.isAllowed("ftp://tools.usps.com/x"));
        assertFalse(usps.isAllowed("javascript:alert(1)"));
        assertFalse(usps.isAllowed(""));
        assertFalse(usps.isAllowed(null));
    }

    @Test public void allowsTheBrowsersOwnInternalSchemes() {
        assertTrue(usps.isAllowed("about:blank"));
        assertTrue(usps.isAllowed("data:text/plain;base64,aGk="));
        assertTrue(usps.isAllowed("blob:https://tools.usps.com/abc"));
    }

    /** A URL the JDK cannot parse is refused rather than guessed at. */
    @Test public void blocksUrlsThatDoNotParse() {
        assertFalse(usps.isAllowed("data:text/html,<script>fetch('//evil')</script>"));
        assertFalse(usps.isAllowed("https://tools.usps.com/ path with spaces"));
    }

    @Test public void blocksEverythingWhenNoHostIsDeclared() {
        BrowserHostPolicy empty = new BrowserHostPolicy(Collections.emptyList());

        assertFalse(empty.isAllowed("https://tools.usps.com/"));
        assertFalse(empty.isAllowed("about:blank"));
    }

    @Test public void supportsSeveralCarrierHosts() {
        BrowserHostPolicy shared = new BrowserHostPolicy(Arrays.asList("usps.com", "ups.com"));

        assertTrue(shared.isAllowed("https://www.ups.com/track"));
        assertTrue(shared.isAllowed("https://tools.usps.com/go"));
        assertFalse(shared.isAllowed("https://fedex.com/track"));
    }
}
