package xju.dctcamera.core.dct.watermark;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * ZigZag 扫描算法单元测试
 */
public class ZigZagTest {
    
    private ZigZag zigzag;
    
    @Before
    public void setUp() {
        zigzag = new ZigZag();
    }
    
    @Test
    public void testOneToTwoRoundTrip() {
        // 创建一维数组
        int[] input = new int[128 * 128];
        for (int i = 0; i < input.length; i++) {
            input[i] = i;
        }
        
        // 一维转二维
        int[][] twoD = new int[128][128];
        zigzag.one2two(input, twoD);
        
        // 二维转一维
        int[] output = new int[128 * 128];
        zigzag.two2one(twoD, output);
        
        // 验证往返一致性
        for (int i = 0; i < input.length; i++) {
            assertEquals("ZigZag round trip failed at index " + i, input[i], output[i]);
        }
    }
    
    @Test
    public void testBoundaryConditions() {
        // 测试边界值
        int[] input = new int[128 * 128];
        for (int i = 0; i < input.length; i++) {
            input[i] = 255; // 最大值
        }
        
        int[][] output = new int[128][128];
        // 不应抛出异常
        zigzag.one2two(input, output);
        
        // 验证所有值正确
        for (int i = 0; i < 128; i++) {
            for (int j = 0; j < 128; j++) {
                assertEquals("Boundary test failed at [" + i + "][" + j + "]", 255, output[i][j]);
            }
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testSmallInputThrowsException() {
        int[] smallInput = new int[100]; // 太小
        int[][] output = new int[128][128];
        zigzag.one2two(smallInput, output);
    }
    
    @Test
    public void testNoInfiniteLoop() {
        // 测试不会进入死循环
        int[] input = new int[128 * 128];
        for (int i = 0; i < input.length; i++) {
            input[i] = i % 256;
        }
        
        int[][] output = new int[128][128];
        long startTime = System.currentTimeMillis();
        
        zigzag.one2two(input, output);
        
        long duration = System.currentTimeMillis() - startTime;
        assertTrue("ZigZag took too long (possible infinite loop): " + duration + "ms", duration < 1000);
    }
}
