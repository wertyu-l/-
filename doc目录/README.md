# demo1 — 用户管理系统

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

- Spring Boot 4.1.0
- MyBatis + PageHelper
- H2 Database
- JWT (jjwt 0.12.6)

## 项目结构

```
demo1/
├── src/main/java/com/example/demo/
│   ├── common/          # 通用类（Result, PageDTO 等）
│   ├── config/          # Web 配置
│   ├── controller/      # 控制器
│   ├── interceptor/     # JWT 拦截器
│   ├── mapper/          # MyBatis Mapper
│   ├── service/         # 服务层
│   ├── ST/              # 实体类
│   └── utils/           # 工具类（JWT）
├── src/main/resources/
│   ├── application.yaml # 应用配置
│   ├── schema.sql       # 建表脚本
│   ├── data.sql         # 初始数据
│   ├── mapper/          # MyBatis XML
│   └── static/          # 前端页面
└── pom.xml
```
