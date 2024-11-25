package com.acme.junit.jupiter;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.base.KiwiStrings.f;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.kiwiproject.net.LocalPortChecker;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * JUnit Jupiter extension that allows integration testing using Apache MINA. It provides an {@link IoSession} for
 * sending data. For example, if you have a MINA-based server and want to test how it behaves when provided various
 * input data.
 * <p>
 * Tests using this extension should:
 * <ol>
 *     <li>Get a port from this extension in a {@link BeforeEach} method</li>
 *     <li>Start a server on the provided port, again in a {@link BeforeEach} method</li>
 *     <li>Stop the server in an {@link AfterEach} method</li>
 * </ol>
 */
@Slf4j
public class MinaSessionExtension implements
        BeforeEachCallback,
        BeforeTestExecutionCallback,
        AfterTestExecutionCallback {

    private static final LocalPortChecker LOCAL_PORT_CHECKER = new LocalPortChecker();
    private static final int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 2;
    private static final int DEFAULT_START_PORT = 16_384;

    private NioSocketConnector senderConnector;

    /**
     * Tests can get a session which can be used to send data to a test server.
     */
    @Getter
    private IoSession session;

    /**
     * Tests using this extension should use this port to start their servers on.
     */
    @Getter
    private int port;

    private final MinaSessionExtensionConfig config;

    /**
     * Create an instance using the default server connection timeout (two seconds).
     */
    public MinaSessionExtension() {
        this(DEFAULT_CONNECTION_TIMEOUT_SECONDS);
    }

    /**
     * Create an instance using the given server connection timeout.
     *
     * @param secondsToWaitForConnection the number of seconds to wait for the server-under-test before test
     *                                   execution to start
     */
    public MinaSessionExtension(int secondsToWaitForConnection) {
        this(MinaSessionExtensionConfig.builder()
                .secondsToWaitForConnection(DEFAULT_CONNECTION_TIMEOUT_SECONDS)
                .portSearch(MinaServerPortSearch.RANDOM_ABOVE)
                .startPort(DEFAULT_START_PORT)
                .build());
    }

    /**
     * Create an instance using the given configuration.
     *
     * @param config the configuration to use
     */
    public MinaSessionExtension(MinaSessionExtensionConfig config) {
        this.config = requireNotNull(config, "config must not be null");
    }

    /**
     * Finds an open port. The test using this extension should use this port to start its server on.
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        var portSearch = config.portSearch();
        var startPort = config.startPort();

        var portOptional = switch (portSearch) {
            case ABOVE -> LOCAL_PORT_CHECKER.findFirstOpenPortAbove(startPort);
            case FROM -> LOCAL_PORT_CHECKER.findFirstOpenPortFrom(startPort);
            case RANDOM_ABOVE -> LOCAL_PORT_CHECKER.findRandomOpenPortAbove(startPort);
            case RANDOM_FROM -> LOCAL_PORT_CHECKER.findRandomOpenPortFrom(startPort);
        };

        this.port = portOptional.orElseThrow(() ->
                new IllegalStateException(f("Unable to find port (PortSearch strategy: {}, start port: {})",
                        portSearch, startPort)));

        LOG.trace("Using PortSearch strategy {} with start port {}, found open port {}",
                portSearch, startPort, port);
    }

    /**
     * The test using this extension should, in a @{@link BeforeEach} method, start its server.
     * Then, immediately before test execution, this method opens an {@link IoSession} to the
     * test server which can be used to send data, e.g., via {@link IoSession#write(Object)}.
     * <p>
     * Waits for the server-under-test to be connected, and throws {@link IllegalStateException}
     * if the server is not connected within the timeout period.
     */
    @Override
    public void beforeTestExecution(ExtensionContext context) {
        senderConnector = new NioSocketConnector();
        senderConnector.getFilterChain().addLast("loggingFilter", new LoggingFilter());

        var protocolCodecFactory = new TextLineCodecFactory(StandardCharsets.UTF_8);
        senderConnector.getFilterChain().addLast("protocolCodecFilter", new ProtocolCodecFilter(protocolCodecFactory));

        var ioHandler = new IoHandlerAdapter();
        senderConnector.setHandler(ioHandler);

        LOG.trace("Connecting to port {} on localhost", port);
        var socketAddress = new InetSocketAddress("localhost", port);
        var connectFuture = senderConnector.connect(socketAddress);
        var secondsToWaitForConnection = config.secondsToWaitForConnection();
        var connectedBeforeTimeout = connectFuture.awaitUninterruptibly(secondsToWaitForConnection, TimeUnit.SECONDS);
        checkState(connectedBeforeTimeout,
                "Did not connect before timeout (does your test start the server in a @BeforeEach method?)");
        checkState(connectFuture.isConnected(),
                "Is not connected to localhost:%s (does your test start the server in a @BeforeEach method?)", port);

        session = connectFuture.getSession();
    }

    /**
     * Immediately after each test, close the {@link IoSession} that was started prior to test execution.
     */
    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (nonNull(session) && session.isActive()) {
            closeSessionWaitingUntilClosedOrTimeout();
        }

        if (nonNull(senderConnector)) {
            LOG.trace("Disposing {}", senderConnector);
            senderConnector.dispose();
        }
    }

    /**
     * Convenience method that forwards to {@link IoSession#getId()}.
     */
    public long getSessionId() {
        return session.getId();
    }

    /**
     * Convenience method that forwards to {@link IoSession#write(Object)}.
     */
    @CanIgnoreReturnValue
    public WriteFuture write(Object message) {
        return session.write(message);
    }

    /**
     * Convenience method that forwards to {@link IoSession#closeNow()} and also then
     * waits (up to two seconds) for the session to close. If the session did not close
     * within that time, it logs a warning.
     *
     * @return true if the session closed within the timeout, otherwise false
     */
    @CanIgnoreReturnValue
    public boolean closeSessionWaitingUntilClosedOrTimeout() {
        LOG.trace("Closing session {}", session.getId());
        var closeFuture = closeNow();
        var closedBeforeTimeout = closeFuture.awaitUninterruptibly(DEFAULT_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!closedBeforeTimeout) {
            LOG.warn("Session {} did not close before timeout", getSessionId());
        }
        return closedBeforeTimeout;
    }

    /**
     * Convenience method that forwards to {@link IoSession#closeNow())}.
     *
     * @return the {@link CloseFuture} that can be used to wait until closed, check if closed, etc.
     */
    public CloseFuture closeNow() {
        return session.closeNow();
    }
}
