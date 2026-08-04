package com.voidnullvalue.parcelpal.source;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UspsUrlPolicyTest {
    @Test public void permitsOnlyHttpsUspsHostsAndInternalUrls() {
        assertTrue(UspsUrlPolicy.isAllowed("https://tools.usps.com/tracking/example"));
        assertTrue(UspsUrlPolicy.isAllowed("https://www.usps.com/assets/script.js"));
        assertTrue(UspsUrlPolicy.isAllowed("https://usps.com/"));
        assertTrue(UspsUrlPolicy.isAllowed("about:blank"));
        assertTrue(UspsUrlPolicy.isAllowed("data:text/plain,empty"));
        assertTrue(UspsUrlPolicy.isAllowed("blob:https://tools.usps.com/id"));

        assertFalse(UspsUrlPolicy.isAllowed("http://tools.usps.com/tracking/example"));
        assertFalse(UspsUrlPolicy.isAllowed("https://usps.com.example.org/script.js"));
        assertFalse(UspsUrlPolicy.isAllowed("https://fast.fonts.net/font.woff"));
        assertFalse(UspsUrlPolicy.isAllowed("javascript:alert(1)"));
        assertFalse(UspsUrlPolicy.isAllowed("not a url"));
    }
}
