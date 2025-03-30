package zec.ghibli.zechat;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import zec.ghibli.zechat.service.RegService;

import static org.junit.jupiter.api.Assertions.*;

public class RegexTest {

    @BeforeAll
    public static void setup() {
        //todo: to set up

    }

    @Test
    @DisplayName("非纯数字字符串测试")
    public void IsNotNumericTest() {
        boolean res = RegService.isNumeric("012-1234");
        assertFalse(res);

        res = RegService.isNumeric("-");
        assertFalse(res);

        res = RegService.isNumeric("০১৮৯০-১২৩৪৫৬");
        assertFalse(res);

        res = RegService.isNumeric("");
        assertFalse(res);

        res = RegService.isNumeric("+86 13606766500");
        assertFalse(res);

        res = RegService.isNumeric(" 13606766500");
        assertFalse(res);

        res = RegService.isNumeric("13606766500\n123");
        assertFalse(res);
    }

    @Test
    @DisplayName("纯数字字符串测试")
    public void IsNumericTest() {
        boolean res = RegService.isNumeric("0121234");
        assertTrue(res);

        res = RegService.isNumeric("০১৮৯০১২৩৪৫৬");
        assertTrue(res);
    }
}
