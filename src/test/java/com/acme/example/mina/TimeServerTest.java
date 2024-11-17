package com.acme.example.mina;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.awaitility.Awaitility.await;

import com.acme.junit.jupiter.MinaSessionExtension;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Durations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

/**
 * Example test showing how to use {@link MinaSessionExtension}.
 */
@DisplayName("TimeServer")
@Slf4j
class TimeServerTest {

    @RegisterExtension
    MinaSessionExtension sessionExtension = new MinaSessionExtension();

    int port;
    TimeServer timeServer;

    @BeforeEach
    void setUp() {
        // Get the port from the extension
        port = sessionExtension.getPort();
        LOG.info("Using port: {}", port);

        // Start the server we want to test
        timeServer = new TimeServer();
        timeServer.createAndStart(port);
    }

    @AfterEach
    void tearDown() {
        // Make sure to stop the server
        timeServer.stopNow();
    }

    @Test
    void shouldWriteMessage() {
        var message = "Hello, MINA!";
        sessionExtension.write(message);
        await().atMost(Durations.ONE_SECOND).until(() -> timeServer.recentMessages().contains(message));
    }

    @Test
    void shouldWriteSeveralMessages() {
        var message1 = "Hi, MINA!";
        sessionExtension.write(message1);

        var message2 = "How are you today?";
        sessionExtension.write(message2);

        var message3 = "Bye, MINA!";
        sessionExtension.write(message3);

        await().atMost(Durations.TWO_SECONDS).until(() -> timeServer.recentMessages().containsAll(
                List.of(message1, message2, message3)
        ));
    }

    @Test
    void shouldNotAllowStartingAgain() {
        assertThatIllegalStateException()
                .isThrownBy(() -> timeServer.createAndStart(42_000))
                .withMessage("Already started");
    }
}
