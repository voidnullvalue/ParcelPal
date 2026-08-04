package com.voidnullvalue.parcelpal.backup;

import org.junit.Test;

import java.security.GeneralSecurityException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class BackupCodecTest {
    @Test public void encryptedRoundTrip() throws Exception {
        String json = "{\"format\":\"parcelpal-backup\",\"packages\":[]}";
        String encoded = BackupCodec.encode(json, "correct horse".toCharArray());
        assertTrue(BackupCodec.isEncrypted(encoded));
        assertEquals(json, BackupCodec.decode(encoded, "correct horse".toCharArray()));
    }

    @Test public void wrongPasswordFailsAuthentication() throws Exception {
        String encoded = BackupCodec.encode("{}", "right".toCharArray());
        try {
            BackupCodec.decode(encoded, "wrong".toCharArray());
            fail("Expected authentication failure");
        } catch (GeneralSecurityException expected) {
            // Expected.
        }
    }

    @Test public void blankPasswordProducesPlainJson() throws Exception {
        assertEquals("{}", BackupCodec.encode("{}", new char[0]));
    }
}
