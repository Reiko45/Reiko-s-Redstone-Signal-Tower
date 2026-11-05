package com.reiko.signaltower.client;

import com.reiko.signaltower.network.RemoteScanResultPayload;

/** A screen that shows locator results. */
public interface RemoteScanListener {
    void onRemoteScan(RemoteScanResultPayload result);
}
