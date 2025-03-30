package zec.ghibli.zechat.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.BufferedReader;
import java.io.PrintWriter;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (! (handler instanceof HandlerMethod)) {
            return true;
        }
        System.out.println("登陆拦截器!");
        System.out.println("登陆拦截器!");

        BufferedReader reader = request.getReader();

        // 读取请求正文中的数据
        String line;
        StringBuilder requestBody = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            requestBody.append(line);
        }

        // 输出读取到的数据
        System.out.println("Request Body: " + requestBody.toString());

        // 关闭 BufferedReader（可选）
        reader.close();

        return true;
    }
}
