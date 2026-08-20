# demo1 — 管理系统

异构硬件设备管控系统，基于 Spring Boot + MyBatis + H2 + JWT 构建。

## 运行环境

| 环境 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 编译与运行 |
| Maven | 3.9+ | 项目自带 Maven Wrapper（`mvnw`），无需手动安装 |
| 操作系统 | Windows / Linux / macOS | 跨平台 |
| 浏览器 | Chrome / Edge / Firefox 最新版 | 访问前端页面 |

## 模块概览

| 模块 | 端口 | 说明 |
|------|------|------|
| `demo1-server` | **8085** | 管控系统后端 |
| `demo1-simulator-input` | 8086 / 8087 | REST 输入设备模拟器 |
| `demo1-simulator-output` | 8088 / 8089 | REST 输出设备模拟器 |
| `demo1-simulator-tlv-input` | 8090 / 8091 | TLV 输入设备模拟器 |
| `demo1-simulator-tlv-output` | 8092 / 8093 | TLV 输出设备模拟器 |

## 启动

### 1. 启动管控系统（demo1-server）

```bash
# 进入 code 目录
cd demo1/code

# 先编译公共模块（首次必须）
# Linux / macOS / Git Bash
./mvnw clean install -pl demo1-common -am

# Windows cmd / PowerShell
mvnw.cmd clean install -pl demo1-common -am

# 再启动管控系统
./mvnw spring-boot:run -pl demo1-server          # Linux / macOS
mvnw.cmd spring-boot:run -pl demo1-server        # Windows
```

### 2. 启动 REST 输入设备模拟器

```bash
# 实例 1（端口 8086，默认）
./mvnw spring-boot:run -pl demo1-simulator-input

# 实例 2（端口 8087）
./mvnw spring-boot:run -pl demo1-simulator-input -Dspring-boot.run.profiles=8087
```

### 3. 启动 REST 输出设备模拟器

```bash
# 实例 1（端口 8088，默认）
./mvnw spring-boot:run -pl demo1-simulator-output

# 实例 2（端口 8089）
./mvnw spring-boot:run -pl demo1-simulator-output -Dspring-boot.run.profiles=8089
```

### 4. 启动 TLV 输入设备模拟器

```bash
# 实例 1（端口 8090，含 Web 管理页面）
./mvnw spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8090

# 实例 2（端口 8091）
./mvnw spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8091
```

### 5. 启动 TLV 输出设备模拟器

```bash
# 实例 1（端口 8092，含 Web 管理页面）
./mvnw spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8092

# 实例 2（端口 8093）
./mvnw spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8093
```

## 关闭

每个服务监听不同端口，按端口号关闭即可。将下面命令中的端口号替换为实际端口（8085 ~ 8093）。

**Windows：**

```bat
for /f "tokens=5" %a in ('netstat -ano ^| findstr :<端口>') do taskkill /f /pid %a
```

**Linux / macOS：**

```bash
kill $(lsof -t -i:<端口>)
```

## 一键启动脚本

### Windows（保存为 `start-all.bat`，放在 `demo1/code` 目录下）

```bat
@echo off
title demo1 一键启动
echo ============================================
echo   demo1 管理系统 - 启动所有模块
echo ============================================

REM 1. 先编译公共模块
echo [1/10] 编译公共模块 demo1-common...
call mvnw.cmd clean install -pl demo1-common -am -q
if %errorlevel% neq 0 (
    echo 编译失败，退出！
    pause
    exit /b 1
)

REM 2. 启动管控系统（8085）
echo [2/10] 启动管控系统（端口 8085）...
start "demo1-server" cmd /c "mvnw.cmd spring-boot:run -pl demo1-server"

REM 等待管控系统就绪
echo 等待管控系统启动中,约 10 秒...
timeout /t 10 /nobreak >nul

REM 3. 启动 REST 输入设备模拟器
echo [3/10] 启动 REST 输入模拟器（8086）...
start "sim-input-8086" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-input"

echo [4/10] 启动 REST 输入模拟器（8087）...
start "sim-input-8087" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-input -Dspring-boot.run.profiles=8087"

REM 4. 启动 REST 输出设备模拟器
echo [5/10] 启动 REST 输出模拟器（8088）...
start "sim-output-8088" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-output"

echo [6/10] 启动 REST 输出模拟器（8089）...
start "sim-output-8089" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-output -Dspring-boot.run.profiles=8089"

REM 5. 启动 TLV 输入设备模拟器
echo [7/10] 启动 TLV 输入模拟器（8090）...
start "sim-tlv-input-8090" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8090"

echo [8/10] 启动 TLV 输入模拟器（8091）...
start "sim-tlv-input-8091" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8091"

REM 6. 启动 TLV 输出设备模拟器
echo [9/10] 启动 TLV 输出模拟器（8092）...
start "sim-tlv-output-8092" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8092"

echo [10/10] 启动 TLV 输出模拟器（8093）...
start "sim-tlv-output-8093" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8093"

echo ============================================
echo   全部模块启动完成！
echo ============================================
pause
```

### Linux / macOS（保存为 `start-all.sh`，放在 `demo1/code` 目录下）

```bash
#!/bin/bash
set -e

echo "============================================"
echo "  demo1 管理系统 - 启动所有模块"
echo "============================================"

# 1. 编译公共模块
echo "[1/10] 编译公共模块 demo1-common..."
./mvnw clean install -pl demo1-common -am -q

# 2. 启动管控系统
echo "[2/10] 启动管控系统（端口 8085）..."
./mvnw spring-boot:run -pl demo1-server &
sleep 10

# 3. 启动 REST 模拟器
echo "[3/10] 启动 REST 输入模拟器（8086）..."
./mvnw spring-boot:run -pl demo1-simulator-input &

echo "[4/10] 启动 REST 输入模拟器（8087）..."
./mvnw spring-boot:run -pl demo1-simulator-input -Dspring-boot.run.profiles=8087 &

echo "[5/10] 启动 REST 输出模拟器（8088）..."
./mvnw spring-boot:run -pl demo1-simulator-output &

echo "[6/10] 启动 REST 输出模拟器（8089）..."
./mvnw spring-boot:run -pl demo1-simulator-output -Dspring-boot.run.profiles=8089 &

# 4. 启动 TLV 模拟器
echo "[7/10] 启动 TLV 输入模拟器（8090）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8090 &

echo "[8/10] 启动 TLV 输入模拟器（8091）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8091 &

echo "[9/10] 启动 TLV 输出模拟器（8092）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8092 &

echo "[10/10] 启动 TLV 输出模拟器（8093）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8093 &

echo ""
echo "============================================"
echo "  全部模块启动完成！"
echo "============================================"
```

### 一键停止全部服务,`stop-all.bat` / `stop-all.sh`

**Windows：**

```bat
@echo off
echo ============================================
echo   停止全部 demo1 服务...
echo ============================================
taskkill /f /im java.exe
echo.
echo 全部服务已停止。
pause
```

**Linux / macOS：**

```bash
#!/bin/bash
echo "============================================"
echo "  停止全部 demo1 服务..."
echo "============================================"
pkill -f spring-boot
echo ""
echo "全部服务已停止。"
```
### 仅启动管控系统,`start-server.bat` / `start-server.sh`

如果只想启动管控系统，不启动模拟设备，使用以下脚本。

**Windows：**

```bat
@echo off
title demo1 - 管控系统
echo ============================================
echo   demo1 - 启动管控系统
echo ============================================
echo [1/2] 编译公共模块 demo1-common...
call mvnw.cmd install -pl demo1-common -DskipTests
echo [2/2] 启动管控系统（8085）...
start "demo1-server" cmd /c "mvnw.cmd spring-boot:run -pl demo1-server"
echo.
echo ============================================
echo   管控系统启动中，请稍候...
echo ============================================
pause
```

**Linux / macOS：**

```bash
#!/bin/bash
echo "============================================"
echo "  demo1 - 启动管控系统"
echo "============================================"
echo "[1/2] 编译公共模块 demo1-common..."
./mvnw install -pl demo1-common -DskipTests
echo "[2/2] 启动管控系统（8085）..."
./mvnw spring-boot:run -pl demo1-server &
echo ""
echo "============================================"
echo "  管控系统启动中，请稍候..."
echo "============================================"
```
### 仅启动 REST 模拟设备,`start-rest.bat` / `start-rest.sh`

如果管控系统已经在运行，只需启动 REST 模拟器，使用以下脚本。

**Windows：**

```bat
@echo off
title demo1 - 启动 REST 模拟器
echo ============================================
echo   demo1 - 启动 REST 模拟设备
echo ============================================
echo [1/4] 启动 REST 输入模拟器（8086）...
start "sim-input-8086" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-input"
echo [2/4] 启动 REST 输入模拟器（8087）...
start "sim-input-8087" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-input -Dspring-boot.run.profiles=8087"
echo [3/4] 启动 REST 输出模拟器（8088）...
start "sim-output-8088" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-output"
echo [4/4] 启动 REST 输出模拟器（8089）...
start "sim-output-8089" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-output -Dspring-boot.run.profiles=8089"
echo.
echo ============================================
echo   REST 模拟器启动完成！
echo ============================================
pause
```

**Linux / macOS：**

```bash
#!/bin/bash
echo "============================================"
echo "  demo1 - 启动 REST 模拟设备"
echo "============================================"
echo "[1/4] 启动 REST 输入模拟器（8086）..."
./mvnw spring-boot:run -pl demo1-simulator-input &
echo "[2/4] 启动 REST 输入模拟器（8087）..."
./mvnw spring-boot:run -pl demo1-simulator-input -Dspring-boot.run.profiles=8087 &
echo "[3/4] 启动 REST 输出模拟器（8088）..."
./mvnw spring-boot:run -pl demo1-simulator-output &
echo "[4/4] 启动 REST 输出模拟器（8089）..."
./mvnw spring-boot:run -pl demo1-simulator-output -Dspring-boot.run.profiles=8089 &
echo ""
echo "============================================"
echo "  REST 模拟器启动完成！"
echo "============================================"
```

### 仅启动 TLV 模拟设备,`start-tlv.bat` / `start-tlv.sh`

TLV 模拟器通过 UDP + TLV 二进制协议通信，自带 Web 管理页面。

**Windows：**

```bat
@echo off
title demo1 - 启动 TLV 模拟器
echo ============================================
echo   demo1 - 启动 TLV 模拟设备
echo ============================================
echo [1/4] 启动 TLV 输入模拟器（8090）...
start "sim-tlv-input-8090" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8090"
echo [2/4] 启动 TLV 输入模拟器（8091）...
start "sim-tlv-input-8091" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8091"
echo [3/4] 启动 TLV 输出模拟器（8092）...
start "sim-tlv-output-8092" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8092"
echo [4/4] 启动 TLV 输出模拟器（8093）...
start "sim-tlv-output-8093" cmd /c "mvnw.cmd spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8093"
echo.
echo ============================================
echo   TLV 模拟器启动完成！
echo ============================================
pause
```

**Linux / macOS：**

```bash
#!/bin/bash
echo "============================================"
echo "  demo1 - 启动 TLV 模拟设备"
echo "============================================"
echo "[1/4] 启动 TLV 输入模拟器（8090）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8090 &
echo "[2/4] 启动 TLV 输入模拟器（8091）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-input -Dspring-boot.run.profiles=8091 &
echo "[3/4] 启动 TLV 输出模拟器（8092）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8092 &
echo "[4/4] 启动 TLV 输出模拟器（8093）..."
./mvnw spring-boot:run -pl demo1-simulator-tlv-output -Dspring-boot.run.profiles=8093 &
echo ""
echo "============================================"
echo "  TLV 模拟器启动完成！"
echo "============================================"
```
## 浏览器访问地址

| 入口 | 地址 | 说明 |
|------|------|------|
| 管控系统前端 | http://localhost:8085/index.html | 管控系统登录与主界面 |
| H2 数据库控制台 | http://localhost:8085/h2-console | 数据库管理界面 |
| REST 输入模拟器 8086 | http://localhost:8086/index.html | REST 输入设备管理页面 |
| REST 输入模拟器 8087 | http://localhost:8087/index.html | REST 输入设备管理页面,实例2 |
| REST 输出模拟器 8088 | http://localhost:8088/index.html | REST 输出设备管理页面 |
| REST 输出模拟器 8089 | http://localhost:8089/index.html | REST 输出设备管理页面,实例2 |
| TLV 输入模拟器 8090 | http://localhost:8090/index.html | TLV 输入设备管理页面 |
| TLV 输入模拟器 8091 | http://localhost:8091/index.html | TLV 输入设备管理页面,实例2 |
| TLV 输出模拟器 8092 | http://localhost:8092/index.html | TLV 输出设备管理页面 |
| TLV 输出模拟器 8093 | http://localhost:8093/index.html | TLV 输出设备管理页面,实例2 |

## 默认账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| `admin` | `admin123` | 管理员 | 系统管理员，拥有全部权限 |

## 数据库

- 使用 H2 文件数据库，数据文件保存在各模块的 `data/` 目录下
- 首次启动时自动执行 `schema.sql` 建表、`data.sql` 插入演示数据
- 各模块数据库独立，互不影响

### H2数据库连接信息

用户名均为 `sa`，密码留空。各模块 JDBC URL 如下：

| 模块 | 实例 | JDBC URL |
|------|------|----------|
| `demo1-server` | 8085 | `jdbc:h2:file:./data/demodb` |
| `demo1-simulator-input` | 8086 | `jdbc:h2:file:./data/simulator_input_8086` |
| `demo1-simulator-input` | 8087 | `jdbc:h2:file:./data/simulator_input_8087` |
| `demo1-simulator-output` | 8088 | `jdbc:h2:file:./data/simulator_output_8088` |
| `demo1-simulator-output` | 8089 | `jdbc:h2:file:./data/simulator_output_8089` |
| `demo1-simulator-tlv-input` | 8090/8091 | 无 H2,使用 JSON 文件存储设备信息 |
| `demo1-simulator-tlv-output` | 8092/8093 | 无 H2,使用 JSON 文件存储设备信息 |

## 常用命令

```bash
# 编译全部模块
./mvnw clean install

# 仅编译跳过测试
./mvnw clean install -DskipTests

# 运行全部测试
./mvnw test

# 打包为可执行 JAR
./mvnw clean package -DskipTests
```
