package com.acme.example.mina;

import lombok.extern.slf4j.Slf4j;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

import java.time.Instant;

@Slf4j
public class TimeServerHandler extends IoHandlerAdapter {

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        LOG.error("TimeServerHandler caught exception for session {}", session.getId(), cause);
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        var sessionId = session.getId();
        LOG.info("Session {} received a message", sessionId);

        var str = message.toString();
        if (str.strip().equalsIgnoreCase("quit")) {
            LOG.info("quit received for session {}", sessionId);
            session.closeNow();
            return;
        }

        LOG.info("Session {}: You said: {}", sessionId, str);

        var now = Instant.now();
        session.write("The time is now " + now.toString());
        LOG.info("Session {}: Message written...", sessionId);
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        LOG.info("session {} is idle", session.getIdleCount(status));
    }
}
