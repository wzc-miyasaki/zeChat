package zec.ghibli.zechat.controller;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zec.ghibli.zechat.model.ApiResponse;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@Slf4j
@RequestMapping
public class TestController {

    @PostMapping("/test")
    public ApiResponse<JSONObject> test(@RequestBody JSONObject jsonObject) {
        return ApiResponse.success(new JSONObject(), "This is a test");
    }
}
