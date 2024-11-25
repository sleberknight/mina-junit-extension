package com.acme.junit.jupiter;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.checkPositive;
import static org.kiwiproject.base.KiwiPreconditions.checkValidNonZeroPort;

import lombok.Builder;

@Builder
public record MinaSessionExtensionConfig(int secondsToWaitForConnection,
                                         MinaServerPortSearch portSearch,
                                         int startPort) {

    public MinaSessionExtensionConfig {
        checkPositive(secondsToWaitForConnection, "secondsToWaitForConnection must be positive");
        checkArgumentNotNull(portSearch, "portSearch must not be null");
        checkValidNonZeroPort(startPort, "startPort must be a valid non-zero port between 1 and 65535");
    }
}
