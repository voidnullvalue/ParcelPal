import com.voidnullvalue.parcelpal.backup.BackupCodec;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.StatusNormalizer;

public final class CoreSelfTest {
    public static void main(String[] args) throws Exception {
        require("UPS".equals(CarrierDetector.detect("1Z999AA10123456784")), "UPS detection");
        require("USPS".equals(CarrierDetector.detect("92612999998771000123456789")), "USPS handoff detection");
        require("DELIVERED".equals(StatusNormalizer.normalize("Delivered, In/At Mailbox")), "status normalization");
        String encrypted = BackupCodec.encode("{\"ok\":true}", "secret".toCharArray());
        require(BackupCodec.isEncrypted(encrypted), "backup encryption marker");
        require("{\"ok\":true}".equals(BackupCodec.decode(encrypted, "secret".toCharArray())), "backup round trip");
        System.out.println("Core self-test passed");
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name + " failed");
    }
}
