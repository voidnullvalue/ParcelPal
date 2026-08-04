package com.voidnullvalue.parcelpal.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AddShipmentFlowTest {
    @Test public void successfulSubmissionNavigatesExactlyOnce() {
        AddShipmentFlow flow = new AddShipmentFlow();
        assertTrue(flow.beginSubmit());
        assertFalse(flow.beginSubmit());
        assertTrue(flow.completeSubmit());
        assertFalse(flow.completeSubmit());
    }

    @Test public void canceledSubmissionDoesNotNavigateWhenSaveCompletes() {
        AddShipmentFlow flow = new AddShipmentFlow();
        assertTrue(flow.beginSubmit());
        flow.cancelNavigation();
        assertFalse(flow.completeSubmit());
    }

    @Test public void failedSubmissionCanBeRetried() {
        AddShipmentFlow flow = new AddShipmentFlow();
        assertTrue(flow.beginSubmit());
        flow.failSubmit();
        assertTrue(flow.beginSubmit());
    }
}
