package zec.ghibli.zechat.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.List;


public class ZecWebSocketHandler extends TextWebSocketHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ZecWebSocketHandler.class);
    private final List<WebSocketSession> sessions = new ArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("New Session established: {}", session.getId());
        log.info(session.toString());
        log.info(session.getAttributes().toString());
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String receivedText = message.getPayload();

        // 创建两个虚拟线程分别处理发送消息和写入数据库
        Thread senderThread = Thread.startVirtualThread(() -> {
            try {
                for (WebSocketSession s : sessions) {
                    if (s.isOpen()) {
                        s.sendMessage(message);
                    }
                }
            } catch (Exception e) {
                log.error("Error in sender thread: " + e.getMessage());
            }
        });

        Thread databaseThread = Thread.startVirtualThread(() -> {
            try {
                saveMessageToDatabase(receivedText);
            } catch (Exception e) {
                log.error("Error in database thread: " + e.getMessage());
            }
        });

        // 可选：等待线程完成（同步）
        senderThread.join();
        databaseThread.join();
    }

    private void saveMessageToDatabase(String receivedText) throws Exception {
        log.info("持久化: {}" , receivedText);
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        log.info("remove session: {}", session.getId());
        sessions.remove(session);
    }
}
