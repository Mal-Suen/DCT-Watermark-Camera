@echo off
REM 设置 Java 环境
set JAVA_HOME=D:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%

REM 运行 Gradle
cd /d "E:\PROMETHEUS PROJECTS\DCT-Watermark-Camera"
echo ====================================
echo Building DCT-Watermark-Camera Project
echo ====================================
echo.

REM 先运行单元测试
echo Running unit tests...
gradlew.bat test --no-daemon
if %errorlevel% neq 0 (
    echo.
    echo ✗ Tests failed!
    exit /b %errorlevel%
)

echo.
echo ====================================
echo ✓ All tests passed!
echo ====================================
