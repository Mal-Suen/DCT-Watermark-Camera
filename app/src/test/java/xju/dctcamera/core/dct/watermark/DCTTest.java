package xju.dctcamera.core.dct.watermark;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * DCT 算法单元测试
 */
public class DCTTest {
    
    private DCT dct;
    
    @Before
    public void setUp() {
        dct = new DCT();
    }
    
    @Test
    public void testForwardAndInverseDCT() {
        // 创建测试数据
        int[][] input = new int[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                input[i][j] = 128; // 测试值
            }
        }
        
        // Forward DCT
        float[][] dctOutput = new float[8][8];
        dct.ForwardDCT(input, dctOutput);
        
        // Inverse DCT
        int[][] reconstructed = new int[8][8];
        dct.InverseDCT(dctOutput, reconstructed);
        
        // 验证重建误差在可接受范围内
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int diff = Math.abs(input[i][j] - reconstructed[i][j]);
                assertTrue("DCT reconstruction error too large: " + diff, diff < 2);
            }
        }
    }
    
    @Test
    public void testDCTCoefficients() {
        // 测试 DC 系数 (0,0) 应该接近平均值
        int[][] input = new int[8][8];
        int expectedAvg = 150;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                input[i][j] = expectedAvg;
            }
        }
        
        float[][] dctOutput = new float[8][8];
        dct.ForwardDCT(input, dctOutput);
        
        // DC 系数应该是 (平均值 - 128) * 8 / sqrt(8*8)
        float dcCoeff = dctOutput[0][0];
        float expectedDC = (expectedAvg - 128) * 8.0f;
        float diff = Math.abs(dcCoeff - expectedDC);
        assertTrue("DC coefficient error too large: " + diff, diff < 1.0f);
    }
}
