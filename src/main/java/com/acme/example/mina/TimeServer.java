package com.acme.example.mina;

import lombok.extern.slf4j.Slf4j;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TimeServer {

    private final AtomicBoolean started;
    private IoAcceptor acceptor;
    private TimeServerHandler handler;

    public TimeServer() {
        started = new AtomicBoolean();
    }

    public void createAndStart(int port) {
        if (started.get()) {
            throw new IllegalStateException("Already started");
        }

        acceptor = new NioSocketAcceptor();
        handler = new TimeServerHandler();

        acceptor.getFilterChain().addLast("logger", new LoggingFilter());
        acceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(new TextLineCodecFactory(StandardCharsets.UTF_8)));

        acceptor.setHandler(handler);

        acceptor.getSessionConfig().setReadBufferSize(2048);
        acceptor.getSessionConfig().setIdleTime(IdleStatus.BOTH_IDLE, 10);  // seconds
        try {
            LOG.info("Binding to port {}", port);
            acceptor.bind(new InetSocketAddress(port));
            started.set(true);
        } catch (IOException e) {
            throw new UncheckedIOException("I/O error binding to port " + port, e);
        }
    }

    public void stopNow() {
        if (started.compareAndSet(true, false)) {
            LOG.info("Stopping...");
            acceptor.unbind();
        }
    }

    public List<String> recentMessages() {
        return handler.recentMessages();
    }
}
