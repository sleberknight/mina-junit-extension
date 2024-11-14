package com.acme.junit.jupiter;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;

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
 * sending data, for example you have a MINA-based server and want to test how it behaves when provided various
 * input data.
 * <p>
 * Tests using this extension should:
 * <ol>
 *     <li>Obtain a port from this extension in a {@link BeforeEach} method</li>
 *     <li>Start a server on the provided port, again in a {@link BeforeEach} method</li>
 *     <li>Stop the server in an {@link AfterEach} method</li>
 * </ol>
 */
@Slf4j
public class MinaSessionExtension implements BeforeEachCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final LocalPortChecker LOCAL_PORT_CHECKER = new LocalPortChecker();

    private NioSocketConnector senderConnector;

    /**
     * Tests can obtain a session which can be used to send data to a test server.
     */
    @Getter
    private IoSession session;

    /**
     * Tests using this extension shoould use this port to start their servers on.
     */
    @Getter
    private int port;

    /**
     * Finds an open port. The test using this extension should use this port to start its server on.
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        port = LOCAL_PORT_CHECKER.findFirstOpenPortAbove(16_384).orElseThrow();
    }

    /**
     * The test using this extension should, in a @{@link BeforeEach} method, start its server.
     * Then, immediately before test execution, this method opens an {@link IoSession} to the
     * test server which can be used to send data, e.g., via {@link IoSession#write(Object)}.
     */
    @Override
    public void beforeTestExecution(ExtensionContext context) {
        senderConnector = new NioSocketConnector();
        senderConnector.getFilterChain().addLast("loggingFilter", new LoggingFilter());

        var protocolCodecFactory = new TextLineCodecFactory(StandardCharsets.UTF_8);
        senderConnector.getFilterChain().addLast("protocolCodecFilter", new ProtocolCodecFilter(protocolCodecFactory));

        var ioHandler = new IoHandlerAdapter();
        senderConnector.setHandler(ioHandler);

        var socketAddress = new InetSocketAddress("localhost", port);
        var connectFuture = senderConnector.connect(socketAddress);
        var connectedBeforeTimeout = connectFuture.awaitUninterruptibly(2, TimeUnit.SECONDS);
        checkState(connectedBeforeTimeout, "Did not connect before timeout");
        checkState(connectFuture.isConnected(), "Is not connected to localhost:%s", port);

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
        var closeFuture = closeNow();
        var closedBeforeTimeout = closeFuture.awaitUninterruptibly(2, TimeUnit.SECONDS);
        if (!closedBeforeTimeout) {
            LOG.warn("session {} did not close before timeout", getSessionId());
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
