package zec.ghibli.zechat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Regex Example
 */
@Service
@Slf4j
public class RegService {

    public static String readFile(String fileName) {
        StringBuilder content = new StringBuilder();
        try {
            Resource resource = new ClassPathResource(fileName);
            InputStream inputStream = resource.getInputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading file: " + fileName, e);
        }
        return content.toString();
    }

    /**
     * 监测：开头结尾直接全部由数字组成
     * @param str
     * @return
     */
    public static Boolean isNumeric(String str) {
        Pattern pattern = Pattern.compile("^\\p{Nd}+$");
        return pattern.matcher(str).find();
    }

    public static void Test() {
        String res = readFile("data/example.txt");
        res = res.replaceAll("-", "");
        Pattern pattern = Pattern.compile("^([+-]?)(88)?(\\p{Nd}+)", Pattern.MULTILINE);
        List<String> test = pattern.matcher(res)
                .results()
                .map(m -> m.group(3))
                .toList();

        for(String s : test) {
            System.out.println(s);
        }
    }

    public static void main(String[] args) {
        Test();
    }
}
