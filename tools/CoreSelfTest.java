import com.voidnullvalue.parcelpal.backup.BackupCodec;
import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.StatusNormalizer;
import com.voidnullvalue.parcelpal.util.TimelineMerger;

import java.util.Arrays;
import java.util.List;

public final class CoreSelfTest {
    public static void main(String[] args) throws Exception {
        require("UPS".equals(CarrierDetector.detect("1Z999AA10123456784")), "UPS detection");
        require("USPS".equals(CarrierDetector.detect("92612999998771000123456789")), "USPS handoff detection");
        require("DELIVERED".equals(StatusNormalizer.normalize("Delivered, In/At Mailbox")), "status normalization");

        List<String> oneSt = CarrierDetector.detectAll("1ST06013631493");
        require("1ST".equals(oneSt.get(0)), "1ST primary detection");
        require(oneSt.contains("Cainiao"), "1ST also resolves to Cainiao");

        List<TimelineMerger.MergedEvent> merged = TimelineMerger.merge(Arrays.asList(
                event("cainiao", "Cainiao", 1_000_000L, "Mayong Town", "Departed from sorting center"),
                event("parcelsapp", "ParcelsApp", 1_000_000L, "", "Departed from sorting center")));
        require(merged.size() == 1, "cross-source duplicate scans merge");
        require(merged.get(0).sourceNames.size() == 2, "merged entry keeps both source names");
        require("Mayong Town".equals(merged.get(0).event.location), "merged entry keeps the richer location");

        String encrypted = BackupCodec.encode("{\"ok\":true}", "secret".toCharArray());
        require(BackupCodec.isEncrypted(encrypted), "backup encryption marker");
        require("{\"ok\":true}".equals(BackupCodec.decode(encrypted, "secret".toCharArray())), "backup round trip");
        System.out.println("Core self-test passed");
    }

    private static TrackingEvent event(String sourceId, String sourceName, long time,
                                       String location, String description) {
        TrackingEvent event = new TrackingEvent();
        event.trackingNumber = "1ST06013631493";
        event.carrierName = sourceName;
        event.sourceId = sourceId;
        event.sourceName = sourceName;
        event.eventTime = time;
        event.location = location;
        event.description = description;
        return event;
    }

    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name + " failed");
    }
}
