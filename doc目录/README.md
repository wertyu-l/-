# demo1 — 管理系统

Spring Boot + MyBatis + H2 + JWT 的用户管理后端项目。

## 环境要求

- JDK 17+
- Maven 3.9+（项目自带 Maven Wrapper，无需手动安装）

## 快速启动

```bash
# 1. 进入项目目录
cd demo1

# 2. 编译并启动（首次启动会自动建表和插入演示数据）
./mvnw clean spring-boot:run        # Linux / macOS / Git Bash
mvnw.cmd clean spring-boot:run       # Windows cmd / PowerShell
```

启动后访问：

| 入口 | 地址 |
|------|------|
| 前端页面 | http://localhost:8085/index.html |
| H2 控制台 | http://localhost:8085/h2-console |

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |

## 数据库

- 使用 H2 文件数据库，数据文件保存在 `data/` 目录下
- 首次启动时自动执行 `schema.sql` 建表、`data.sql` 插入演示数据
- H2 控制台连接信息：
  - JDBC URL: `jdbc:h2:file:./data/demodb`
  - 用户名: `sa`
  - 密码: （留空）

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.1.0 | 核心框架 |
| MyBatis | 4.0.0 | ORM |
| PageHelper | 2.1.0 | 分页插件 |
| H2 Database | — | 文件数据库 |
| JWT (jjwt) | 0.12.6 | 身份认证 |
| Spring Security Crypto | — | BCrypt 密码加密 |
| Jackson | — | JSON 序列化 / UDP 发现 |
| Spring JDBC | — | JdbcTemplate（模拟设备） |
| JUnit 5 + Mockito | — | 单元测试 |

## 项目结构

```
demo1/
├── demo1-common/              # 公共模块（共享模型、通用类）
│   └── src/main/java/com/example/demo/
│       ├── common/            #   Result, PageDTO, PageResult
│       └── model/             #   SimDeviceInfo, SimDeviceStatus, SimDeviceCapability, SimWindow
│
├── demo1-server/              # 管控系统后端（端口 8085）
│   └── src/main/java/com/example/demo/
│       ├── ST/                #   实体类（User）
│       ├── common/            #   请求/响应类（AddDeviceRequest, DevicePageVO, DiscoveredNode 等）
│       ├── config/            #   Web 配置、安全配置
│       ├── controller/        #   控制器（DeviceController, UserController）
│       ├── driver/            #   设备驱动（DeviceDriver 接口, RestDeviceDriver, DeviceEndpoint）
│       ├── interceptor/       #   JWT 拦截器
│       ├── mapper/            #   MyBatis Mapper（DeviceMapper, UserMapper）
│       ├── service/           #   服务层（DeviceService, UserService, DeviceDiscoveryService）
│       └── utils/             #   工具类（JwtUtils）
│
├── demo1-simulator/           # REST 模拟设备 1（端口 8086）
│   └── src/main/java/com/example/demo/simulator/
│       ├── controller/        #   SimDeviceController（REST API）
│       ├── core/              #   SimDeviceManager, DeviceRepository
│       └── server/            #   DiscoveryListener（UDP 设备发现）
│
├── demo1-simulator2/          # REST 模拟设备 2（端口 8087）
│   └── src/main/java/com/example/demo/simulator2/
│       ├── controller/        #   SimDeviceController（REST API）
│       ├── core/              #   SimDeviceManager, DeviceRepository
│       └── server/            #   DiscoveryListener（UDP 设备发现）
│
└── pom.xml                    # 父 POM（多模块管理）
```