@echo off
chcp 65001 >nul
title Lab OA 开发环境启动器
color 0A

echo ==========================================
echo   Lab OA 开发环境启动器
echo   Windows 一键启动
echo ==========================================
echo.

:: 检查 Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [✗] Java 未安装！请先安装 JDK 17
    echo     下载: https://adoptium.net/temurin/releases/?version=17
    echo.
    pause
    exit /b 1
)
for /f "tokens=2 delims=." %%i in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVAVER=%%i
echo [✓] Java 版本: %JAVAVER%

:: 检查 Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [✗] Maven 未安装！请先安装 Maven 3.8+
    echo     下载: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)
echo [✓] Maven 已安装

:: 检查 Docker
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [✗] Docker 未安装！请先安装 Docker Desktop
    echo     下载: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)
echo [✓] Docker 已安装

echo.
echo ==========================================
echo   Step 1/4: 启动基础设施容器
echo   (MySQL + Redis + ES + MinIO)
echo ==========================================
echo.
docker-compose up -d mysql redis elasticsearch minio
if %errorlevel% neq 0 (
    echo [✗] 容器启动失败！
    pause
    exit /b 1
)
echo [✓] 容器启动完成

echo.
echo ==========================================
echo   Step 2/4: 等待服务就绪 (60秒)
echo ==========================================
echo.
set WAIT_SECONDS=60
for /l %%i in (1,1,%WAIT_SECONDS%) do (
    timeout /t 1 /nobreak >nul
    cls
    echo ==========================================
    echo   等待服务就绪... %%i / %WAIT_SECONDS% 秒
    echo ==========================================
    echo.
    echo   MySQL: 等待连接...
    echo   ES:    等待 green 状态...
    echo   MinIO: 等待初始化...
    echo.
)

echo.
echo ==========================================
echo   Step 3/4: 编译项目
echo ==========================================
echo.
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [✗] 编译失败！请检查错误信息
    pause
    exit /b 1
)
echo [✓] 编译成功

echo.
echo ==========================================
echo   Step 4/4: 启动应用
echo ==========================================
echo.
echo   端口: 8080
echo   API:  http://localhost:8080
echo   文档: http://localhost:8080/swagger-ui.html
echo.
echo   按 Ctrl+C 可停止应用
echo ==========================================
echo.
java -jar lab-oa-web/target/lab-oa-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

pause