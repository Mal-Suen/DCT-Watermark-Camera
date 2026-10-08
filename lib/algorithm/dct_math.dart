// dct_math.dart — 逐行移植自 Android 版 watermark 包：
//   DCT.java, DCT2.java, Qt.java, ZigZag.java
//
// 位级一致性要点：原 Java 用 Math.round(temp1)，其中 Math.round(x)=floor(x+0.5)，
// 对 .5 边界与 Dart 的 double.round()（round half away from zero）行为不同。
// 因此这里统一用 jRound() 精确复刻 Java 语义。
// 变量/常量名（Ct、Qtable）保留 Java 原名，便于与原版逐行对照。
// ignore_for_file: non_constant_identifier_names, constant_identifier_names

import 'dart:math' as m;

/// 复刻 java.lang.Math.round(double) === floor(x + 0.5)
int jRound(num x) => (x + 0.5).floor().toInt();

/// 8x8 图像 DCT（n=8）。
class DCT {
  static const int N = 8;

  late List<List<double>> C; // Cosine basis
  late List<List<double>> Ct; // Transpose of C

  DCT() {
    final pi = m.atan(1.0) * 4.0; // Math.atan(1)*4
    C = List.generate(N, (_) => List<double>.filled(N, 0));
    Ct = List.generate(N, (_) => List<double>.filled(N, 0));
    for (int j = 0; j < N; j++) {
      C[0][j] = 1.0 / m.sqrt(N);
      Ct[j][0] = C[0][j];
    }
    for (int i = 1; i < N; i++) {
      for (int j = 0; j < N; j++) {
        C[i][j] = m.sqrt(2.0 / N) * m.cos(pi * (2 * j + 1) * i / (2.0 * N));
        Ct[j][i] = C[i][j];
      }
    }
  }

  void forwardDCT(List<List<int>> input, List<List<int>> output) {
    final temp = List.generate(N, (_) => List<double>.filled(N, 0));
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        temp[i][j] = 0.0;
        for (int k = 0; k < N; k++) {
          temp[i][j] += (input[i][k] - 128) * Ct[k][j];
        }
      }
    }
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        double temp1 = 0.0;
        for (int k = 0; k < N; k++) {
          temp1 += C[i][k] * temp[k][j];
        }
        output[i][j] = jRound(temp1);
      }
    }
  }

  void inverseDCT(List<List<int>> input, List<List<int>> output) {
    final temp = List.generate(N, (_) => List<double>.filled(N, 0));
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        temp[i][j] = 0.0;
        for (int k = 0; k < N; k++) {
          temp[i][j] += input[i][k] * C[k][j];
        }
      }
    }
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        double temp1 = 0.0;
        for (int k = 0; k < N; k++) {
          temp1 += Ct[i][k] * temp[k][j];
        }
        temp1 += 128.0;
        if (temp1 < 0) {
          output[i][j] = 0;
        } else if (temp1 > 255) {
          output[i][j] = 255;
        } else {
          output[i][j] = jRound(temp1);
        }
      }
    }
  }
}

/// 水印专用 4x4 DCT（n=4）。
class DCT2 {
  static const int N = 4;

  late List<List<double>> C;
  late List<List<double>> Ct;

  DCT2() {
    final pi = m.atan(1.0) * 4.0;
    C = List.generate(N, (_) => List<double>.filled(N, 0));
    Ct = List.generate(N, (_) => List<double>.filled(N, 0));
    for (int j = 0; j < N; j++) {
      C[0][j] = 1.0 / m.sqrt(N);
      Ct[j][0] = C[0][j];
    }
    for (int i = 1; i < N; i++) {
      for (int j = 0; j < N; j++) {
        C[i][j] = m.sqrt(2.0 / N) * m.cos(pi * (2 * j + 1) * i / (2.0 * N));
        Ct[j][i] = C[i][j];
      }
    }
  }

  void forwardDCT(List<List<int>> input, List<List<int>> output) {
    final temp = List.generate(N, (_) => List<double>.filled(N, 0));
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        temp[i][j] = 0.0;
        for (int k = 0; k < N; k++) {
          temp[i][j] += (input[i][k] - 128) * Ct[k][j];
        }
      }
    }
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        double temp1 = 0.0;
        for (int k = 0; k < N; k++) {
          temp1 += C[i][k] * temp[k][j];
        }
        output[i][j] = jRound(temp1);
      }
    }
  }

  void inverseDCT(List<List<int>> input, List<List<int>> output) {
    final temp = List.generate(N, (_) => List<double>.filled(N, 0));
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        temp[i][j] = 0.0;
        for (int k = 0; k < N; k++) {
          temp[i][j] += input[i][k] * C[k][j];
        }
      }
    }
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        double temp1 = 0.0;
        for (int k = 0; k < N; k++) {
          temp1 += Ct[i][k] * temp[k][j];
        }
        temp1 += 128.0;
        if (temp1 < 0) {
          output[i][j] = 0;
        } else if (temp1 > 255) {
          output[i][j] = 255;
        } else {
          output[i][j] = jRound(temp1);
        }
      }
    }
  }
}

/// 量化 / 反量化表。
class Qt {
  static const int N = 4;

  static const List<List<double>> Qtable = [
    [20, 30, 30, 35],
    [30, 30, 35, 45],
    [30, 35, 45, 50],
    [35, 45, 50, 60],
  ];

  static const List<List<double>> filter = [
    [0.2, 0.6, 0.6, 1],
    [0.6, 0.6, 1, 1.1],
    [0.6, 1, 1.1, 1.2],
    [1, 1.1, 1.2, 1.3],
  ];

  // WaterDeQt：反量化（input * Qtable*filter）
  void waterDeQuantize(List<List<int>> input, List<List<int>> output) {
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        output[i][j] = (input[i][j] * (Qtable[i][j] * filter[i][j])).toInt();
      }
    }
  }

  // WaterQt：量化（round(input / Qtable*filter)）
  void waterQuantize(List<List<int>> input, List<List<int>> output) {
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        output[i][j] = jRound(input[i][j] / (Qtable[i][j] * filter[i][j]));
      }
    }
  }
}

/// ZigZag 扫描：一维 <-> 二维 常量横斜遍历。
class ZigZag {
  static const int N = 128;

  // Java one2two(int[], int[][])
  void one2two(List<int> input, List<List<int>> output) {
    int n = 0, x = 0, y = 0;
    output[x][y] = input[n];
    n++;
    while (true) {
      if (x == 0 && y <= N - 2) {
        y++;
        output[x][y] = input[n];
        n++;
        while (true) {
          x++;
          y--;
          output[x][y] = input[n];
          n++;
          if (y == 0) {
            break;
          }
        }
      }
      if (y == 0 && x <= N - 2) {
        x++;
        output[x][y] = input[n];
        n++;
        while (true) {
          x--;
          y++;
          output[x][y] = input[n];
          n++;
          if (x == 0) {
            break;
          }
        }
      }
      if (x == N - 1 && y < N - 2) {
        y++;
        output[x][y] = input[n];
        n++;
        while (true) {
          x--;
          y++;
          output[x][y] = input[n];
          n++;
          if (y == N - 1) {
            break;
          }
        }
      }
      if (y == N - 1 && x < N - 2) {
        x++;
        output[x][y] = input[n];
        n++;
        while (true) {
          x++;
          y--;
          output[x][y] = input[n];
          n++;
          if (x == N - 1) {
            break;
          }
        }
      }
      if (x == N - 1 && y == N - 2) {
        y++;
        output[x][y] = input[n];
        break;
      }
    }
  }

  // Java two2one(int[][], int[])
  void two2one(List<List<int>> input, List<int> output) {
    int n = 0, x = 0, y = 0;
    output[n] = input[x][y];
    n++;
    while (true) {
      if (x == 0 && y <= N - 2) {
        y++;
        output[n] = input[x][y];
        n++;
        while (true) {
          x++;
          y--;
          output[n] = input[x][y];
          n++;
          if (y == 0) {
            break;
          }
        }
      }
      if (y == 0 && x <= N - 2) {
        x++;
        output[n] = input[x][y];
        n++;
        while (true) {
          x--;
          y++;
          output[n] = input[x][y];
          n++;
          if (x == 0) {
            break;
          }
        }
      }
      if (x == N - 1 && y < N - 2) {
        y++;
        output[n] = input[x][y];
        n++;
        while (true) {
          x--;
          y++;
          output[n] = input[x][y];
          n++;
          if (y == N - 1) {
            break;
          }
        }
      }
      if (y == N - 1 && x < N - 2) {
        x++;
        output[n] = input[x][y];
        n++;
        while (true) {
          x++;
          y--;
          output[n] = input[x][y];
          n++;
          if (x == N - 1) {
            break;
          }
        }
      }
      if (x == N - 1 && y == N - 2) {
        y++;
        output[n] = input[x][y];
        break;
      }
    }
  }
}

