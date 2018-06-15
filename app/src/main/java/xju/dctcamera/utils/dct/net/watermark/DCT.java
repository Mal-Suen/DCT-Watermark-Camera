package xju.dctcamera.utils.dct.net.watermark;

/**
 * DCT 類別,含ForwardDCT與InverseDCT兩種方法class DCT
 * n=8
 */

class DCT {
    static int N = 8;

    public double C[][] = new double[N][N];

    public double Ct[][] = new double[N][N];

    DCT() {
        int i;
        int j;
        final double pi = Math.atan(1.0) * 4.0;
        for (j = 0; j < N; j++) {
            this.C[0][j] = 1.0 / Math.sqrt(N);
            this.Ct[j][0] = this.C[0][j];
        }
        for (i = 1; i < N; i++) {
            for (j = 0; j < N; j++) {
                this.C[i][j] = Math.sqrt(2.0 / N) * Math.cos(pi * (2 * j + 1) * i / (2.0 * N));
                this.Ct[j][i] = this.C[i][j];
            }
        }
    }

    void ForwardDCT(final int input[][], final int output[][]) {
        final double temp[][] = new double[N][N];
        double temp1;
        int i, j, k;
        for (i = 0; i < N; i++) {
            for (j = 0; j < N; j++) {
                temp[i][j] = 0.0;
                for (k = 0; k < N; k++) {
                    temp[i][j] += (input[i][k] - 128) * this.Ct[k][j];
                }
            }
        }

        for (i = 0; i < N; i++) {
            for (j = 0; j < N; j++) {
                temp1 = 0.0;
                for (k = 0; k < N; k++) {
                    temp1 += this.C[i][k] * temp[k][j];
                }
                output[i][j] = (int) Math.round(temp1);
            }
        }
    }

    void InverseDCT(final int input[][], final int output[][]) {
        final double temp[][] = new double[N][N];
        double temp1;
        int i, j, k;

        for (i = 0; i < N; i++) {
            for (j = 0; j < N; j++) {
                temp[i][j] = 0.0;
                for (k = 0; k < N; k++) {
                    temp[i][j] += input[i][k] * this.C[k][j];
                }
            }
        }

        for (i = 0; i < N; i++) {
            for (j = 0; j < N; j++) {
                temp1 = 0.0;
                for (k = 0; k < N; k++) {
                    temp1 += this.Ct[i][k] * temp[k][j];
                }
                temp1 += 128.0;
                if (temp1 < 0) {
                    output[i][j] = 0;
                } else if (temp1 > 255) {
                    output[i][j] = 255;
                } else {
                    output[i][j] = (int) Math.round(temp1);
                }
            }
        }
    }
}
