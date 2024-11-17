package com.acme.example.mina;

import lombok.extern.slf4j.Slf4j;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.kiwiproject.collect.KiwiEvictingQueues;

import java.time.Instant;
import java.util.List;
import java.util.Queue;

@Slf4j
public class TimeServerHandler extends IoHandlerAdapter {

    private final Queue<String> recentMessages = KiwiEvictingQueues.synchronizedEvictingQueue();

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        LOG.error("TimeServerHandler caught exception for session {}", session.getId(), cause);
    }

    @Override
    public void messageReceived(IoSession session, Object messageObj) {
        var sessionId = session.getId();
        LOG.info("Session {} received a message", sessionId);

        var message = messageObj.toString().strip();
        if (message.equalsIgnoreCase("quit")) {
            LOG.info("quit received for session {}", sessionId);
            session.closeNow();
            return;
        }

        LOG.info("Session {}: You said: {}", sessionId, message);
        recentMessages.add(message);

        var now = Instant.now();
        session.write("The time is now " + now.toString());
        LOG.info("Session {}: Message written...", sessionId);
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        LOG.info("session {} is idle", session.getIdleCount(status));
    }

    public List<String> recentMessages() {
        return recentMessages.stream().toList();
    }
}
