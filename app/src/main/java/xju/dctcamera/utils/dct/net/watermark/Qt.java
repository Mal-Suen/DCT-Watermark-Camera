package xju.dctcamera.utils.dct.net.watermark;



/**
 * 含WaterQt(量化)與WaterDeQt(反量化)兩種方法
 */

class Qt {
    static int N = 4;

    public double Qtable[][] = {
            {
                    20, 30, 30, 35 }, {
                    30, 30, 35, 45 }, {
                    30, 35, 45, 50 }, {
                    35, 45, 50, 60 } };

    public double filter[][] = {
            {
                    0.2, 0.6, 0.6, 1 }, {
                    0.6, 0.6, 1, 1.1 }, {
                    0.6, 1, 1.1, 1.2 }, {
                    1, 1.1, 1.2, 1.3 } };

    Qt() {}

    /**
     * 量化
     * @param input
     * @param output
     */
    void WaterDeQt(final int input[][], final int output[][]) {
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                output[i][j] = (int) (input[i][j] * (this.Qtable[i][j] * this.filter[i][j]));
            }
        }
    }

    /**
     * 反量化
     * @param input
     * @param output
     */
    void WaterQt(final int input[][], final int output[][]) {
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                output[i][j] = (int) Math.round(input[i][j] / (this.Qtable[i][j] * this.filter[i][j]));
            }
        }
    }

}
