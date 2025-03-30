package zec.ghibli.zechat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 提供页面路由
 */
@Controller
public class MessageController {
    @GetMapping("/send")
    public String senderPage() {
        return "sender";
    }

    @GetMapping("/receiver")
    public String receiverPage() {
        return "receiver";
    }
}
