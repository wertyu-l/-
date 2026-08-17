# TLV 模拟设备设计文档

## 1. 概述

### 1.1 模块定位

TLV 模拟设备是 V4 阶段新增的**第二类异构设备**，通过 UDP + TLV 二进制协议与管控系统通信。

**V4 核心目标：验证 `DeviceDriver` 统一抽象接口的正确性**——管控系统业务代码不改一行，通过 `DeviceEndpoint.deviceType` 区分设备类型，同一套开窗接口同时控制多类设备。

> **设计原则：** TLV 模拟设备使用 `demo1-common` 公共数据模型，通信层采用 UDP + TLV 二进制协议。
>
> **设备类别说明：** 模拟设备分为两类，由 `deviceCategory` 字段标识（`INPUT`/`OUTPUT`）：
> - **输入设备（`deviceCategory = "INPUT"`）：** 拥有输入通道，负责提供信号源。输入设备不支持窗口操作，仅提供设备信息、状态、能力查询接口，以及通道播放地址设置。支持接收管控系统推送的窗口信息反馈（`CMD_NOTIFY_WINDOW`）。
> - **输出设备（`deviceCategory = "OUTPUT"`）：** 拥有输出通道，用于大屏绑定显示。支持窗口创建/关闭/查询/更新。通过声明设备能力，管控系统据此做布局校验。

### 1.2 技术选型

| 选型 | 方案 | 说明 |
|------|------|------|
| 框架 | Spring Boot 空壳模式 | 只用 IoC 容器管理 Bean，不启动 Tomcat/SpringMVC |
| 启动 | SpringApplication.run() + WebApplicationType.NONE | main 方法启动，与 REST 模拟器形式统一 |
| 通信 | UDP + TLV 二进制协议 | 与管控系统通过 TLV 帧通信 |
| 前端 | JDK 内置 HttpServer | 零依赖，提供静态页面 + JSON API |
| 配置 | JSON 文件 | 启动时从 resources 加载，不依赖数据库 |

> Spring Boot 空壳模式：SpringApplication 设置 setWebApplicationType(WebApplicationType.NONE)，仅启动 IoC 容器，不启动内嵌 Web 服务器。所有 Bean（如 TlvServer、DiscoveryListener、FrontendServer）通过 @Component + @PostConstruct 自动装配和启动。

---

## 2. 模块结构

### 2.1 文件结构

TLV 模拟设备是独立 Java 程序，共有 2 个模拟器模块，通过 `main` 方法启动，命令行参数传入配置文件和端口号，每个进程代表一台设备。数据模型与管控系统共享 `demo1-common`，编解码器也放在 `demo1-common` 中供模拟器和管控系统共用。

**demo1-common（V4 新增编解码器）**

```
demo1-common/src/main/java/com/example/demo/codec/   ←新增
    ├── TlvFrame.java            ← TLV 外层帧定义（Type + Length + Value）
    ├── TlvTag.java              ← 字段 Tag 常量（内层字段标识，新增）
    ├── TlvCommand.java          ← 命令类型常量
    ├── TlvEncoder.java          ← 外层帧编码器：TlvFrame → byte[]
    ├── TlvDecoder.java          ← 外层帧解码器：byte[] → TlvFrame
    └── TlvFieldCodec.java       ← 字段级编解码器（内层 TLV 编解码，新增）
```

> 管控系统的 `TlvDeviceDriver` 编请求包、模拟器的 `TlvServer` 解请求包，两边都需要编解码，放在公共模块避免重复。

**demo1-simulator-tlv-input**（输入设备模块）

```
demo1-simulator-tlv-input/
└── src/main/resources/
    ├── device-8090.json                ← TLV 输入设备-1 配置（1个输入通道 HDMI-1）
    └── device-8091.json                ← TLV 输入设备-2 配置（2个输入通道 HDMI-1, HDMI-2）

└── src/main/java/com/example/demo/simulator/tlv/input/
    ├── SimulatorApplication.java       ← 程序入口（main 方法，读配置，启动 TlvServer）
    ├── server/
    │   ├── TlvServer.java              ← UDP 服务端（监听端口，请求分发）
    │   └── DiscoveryListener.java      ← UDP 设备发现监听
    ├── frontend/
    │   ├── FrontendServer.java         ← JDK HttpServer 前端管理
    │   └── static/
    │       └── index.html              ← 前端管理页面
    ├── handler/
    │   ├── GetInfoHandler.java         ← 查询设备信息
    │   ├── GetStatusHandler.java       ← 查询设备状态
    │   └── NotifyWindowHandler.java    ← 窗口信息反馈
    └── core/
        ├── SimDeviceManager.java       ← 设备管理核心（含通道-窗口占用映射）
        └── DeviceConfig.java           ← 设备配置加载（从 JSON 文件读取）
```
├── pom.xml

**demo1-simulator-tlv-output**（输出设备模块）

```
demo1-simulator-tlv-output/
└── src/main/resources/
    ├── device-8092.json                ← TLV 输出设备-1 配置（2个输出通道，maxWindows=4）
    └── device-8093.json                ← TLV 输出设备-2 配置（3个输出通道，maxWindows=6）

└── src/main/java/com/example/demo/simulator/tlv/output/
    ├── SimulatorApplication.java       ← 程序入口（main 方法，读配置，启动 TlvServer）
    ├── server/
    │   ├── TlvServer.java              ← UDP 服务端（监听端口，请求分发）
    │   └── DiscoveryListener.java      ← UDP 设备发现监听
    ├── frontend/
    │   ├── FrontendServer.java         ← JDK HttpServer 前端管理
    │   └── static/
    │       └── index.html              ← 前端管理页面
    ├── handler/
    │   ├── GetInfoHandler.java         ← 查询设备信息
    │   ├── GetStatusHandler.java       ← 查询设备状态
    │   ├── CreateWindowHandler.java    ← 创建窗口
    │   ├── UpdateWindowHandler.java    ← 更新窗口
    │   └── CloseWindowHandler.java     ← 关闭窗口
    └── core/
        ├── SimDeviceManager.java       ← 设备管理核心（窗口存内存）
        └── DeviceConfig.java           ← 设备配置加载（从 JSON 文件读取）
```
├── pom.xml

> 添加设备只需新增对应的 JSON 配置文件，启动新进程即可。

**demo1-server（管控侧，V4 新增）**

```
demo1-server/src/main/java/com/example/demo/driver/
    ├── DeviceDriver.java          (已有)
    ├── RestDeviceDriver.java      (已有)
    └── TlvDeviceDriver.java       (新增 - UDP + TLV 驱动)
```
---
### 2.2 依赖清单

**demo1-simulator-tlv-input / demo1-simulator-tlv-output**（pom.xml）：

```xml
<dependencies>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>demo1-common</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

> spring-boot-starter 仅引入 Spring IoC 核心依赖，不包含 spring-boot-starter-web（无 Tomcat/SpringMVC）。零额外依赖，JDK HttpServer 为 JDK 内置。


## 3. 设备配置

TLV 模拟设备不使用数据库，设备信息和能力通过 JSON 配置文件加载，启动时一次性读取到内存。

### 3.1 配置文件格式

**输入设备配置**（`device-8090.json`）：

```json
{
  "deviceInfo": {
    "deviceName": "TLV 输入设备-1",
    "deviceType": "TLV",
    "deviceCategory": "INPUT",
    "model": "DS-D2055NH-A",
    "serialNumber": "SN-TLV-INPUT-001",
    "channelCount": 1,
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "inputChannel3": "",
    "inputChannel4": "",
    "inputChannel5": "",
    "maxResolution": "1920x1080",
    "maxWindows": 0
  },
  "deviceCapability": {
    "maxResolution": "1920x1080",
    "channelCount": 1,
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "inputChannel3": "",
    "inputChannel4": "",
    "inputChannel5": ""
  },
  "server": {
    "port": 8090,
    "discoveryPort": 9995
  }
}
```

**输出设备配置**（`device-8092.json`）：

```json
{
  "deviceInfo": {
    "deviceName": "TLV 输出设备-1",
    "deviceType": "TLV",
    "deviceCategory": "OUTPUT",
    "model": "DS-D2055NH-A",
    "serialNumber": "SN-TLV-OUTPUT-001",
    "channelCount": 2,
    "outputChannel1": "OUT-1",
    "outputChannel2": "OUT-2",
    "outputChannel3": "",
    "outputChannel4": "",
    "outputChannel5": "",
    "maxResolution": "1920x1080",
    "maxWindows": 4
  },
  "deviceCapability": {
    "maxWindows": 4,
    "supportMove": true,
    "supportResize": true,
    "supportOverlay": true,
    "maxResolution": "1920x1080",
    "channelCount": 2,
    "outputChannel1": "OUT-1",
    "outputChannel2": "OUT-2",
    "outputChannel3": "",
    "outputChannel4": "",
    "outputChannel5": ""
  },
  "server": {
    "port": 8092,
    "discoveryPort": 9993
  }
}
```

### 3.2 配置字段说明

**deviceInfo — 设备基本信息**

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceName | String | 设备名称，如 TLV 输入设备-1 |
| deviceType | String | 设备类型，固定为 TLV |
| deviceCategory | String | 设备类别：INPUT / OUTPUT |
| model | String | 设备型号 |
| serialNumber | String | 序列号 |
| channelCount | int | 实际通道数 |
| inputChannel1~5 | String | 输入通道名称（仅输入设备填写） |
| outputChannel1~5 | String | 输出通道名称（仅输出设备填写） |
| maxWindows | int | 最大窗口数（仅输出设备） |
| maxResolution | String | 最大分辨率 |

**deviceCapability — 设备能力**

| 字段 | 类型 | 说明 |
|------|------|------|
| maxWindows | int | 最大窗口数（仅输出设备） |
| supportMove | boolean | 是否支持窗口移动（仅输出设备） |
| supportResize | boolean | 是否支持窗口缩放（仅输出设备） |
| supportOverlay | boolean | 是否支持窗口叠加（仅输出设备） |
| maxResolution | String | 最大分辨率 |
| channelCount | int | 通道数 |
| inputChannel1~5 | String | 输入通道名称（仅输入设备） |
| outputChannel1~5 | String | 输出通道名称（仅输出设备） |

**server — 服务配置**

| 字段 | 类型 | 说明 |
|------|------|------|
| port | int | 设备端口号（TCP 前端 + UDP 通信，共用） |
| discoveryPort | int | 设备发现 UDP 端口 |

---

## 4. 数据模型

数据模型定义在 `demo1-common` 模块中，供模拟器和管控系统共享。

### 4.1 SimDeviceInfo — 设备基本信息

位于 `demo1-common` 的 `model/` 包下，一个进程 = 一台设备，`baseUrl`（含端口）即设备唯一标识。

```java
@Data
public class SimDeviceInfo {

    private String deviceName;      // 设备名称，如 TLV输入设备-1
    private String deviceType;      // 设备类型，TLV 设备固定为 TLV
    private String deviceCategory;  // 设备类别：INPUT=输入设备，OUTPUT=输出设备
    private String model;           // 设备型号，如 DS-TLV2048-A
    private String serialNumber;    // 序列号
    private int channelCount;       // 实际通道数，控制前端渲染和校验
    private String inputChannel1;   // 输入通道1名称，为空表示无该通道
    private String inputChannel2;
    private String inputChannel3;
    private String inputChannel4;
    private String inputChannel5;
    private String outputChannel1;  // 输出通道1名称，为空表示无该通道
    private String outputChannel2;
    private String outputChannel3;
    private String outputChannel4;
    private String outputChannel5;
    private String maxResolution;   // 最大分辨率，如 1920x1080
    private int maxWindows;         // 最大窗口数（仅输出设备有意义）

}
```

### 4.2 SimDeviceStatus — 设备运行状态

位于 `demo1-common` 的 `model/` 包下。

```java
@Data
public class SimDeviceStatus {

    private boolean online;        // 是否在线，当前始终为 true
    private int windowCount;       // 当前窗口数量
    private String uptime;         // 设备启动时间，格式 yyyy-MM-dd HH:mm:ss

}
```

### 4.3 SimWindow — 窗口信息

位于 `demo1-common` 的 `model/` 包下。窗口是管控系统下发到模拟设备的内容展示单元，每个窗口绑定到设备的某个通道。窗口由管控系统创建并推送到设备，**不持久化存储**，进程重启后窗口数据丢失，由管控系统重新推送。

```java
@Data
public class SimWindow {

    private String windowId;       // 窗口唯一标识，由管控系统生成，全局唯一
    private String channelName;    // 绑定的通道名称，必须是该设备已定义的通道名之一
    private Integer x;             // 窗口左上角 X 坐标，null 表示未设置，默认 0
    private Integer y;             // 窗口左上角 Y 坐标，null 表示未设置，默认 0
    private Integer width;         // 窗口宽度（像素），null 表示未设置，默认 1920
    private Integer height;        // 窗口高度（像素），null 表示未设置，默认 1080
    private String sourceType;     // 信号源类型，由设备根据通道配置返回
    private String sourceUrl;      // 信号源地址，由设备根据通道配置返回
    private String createTime;     // 窗口创建时间，格式 yyyy-MM-dd HH:mm:ss，自动生成

}
```

### 4.4 SimDeviceCapability — 设备能力

位于 `demo1-common` 的 `model/` 包下。描述设备的功能限制，控制窗口创建时的校验规则。能力可以在运行时动态变更，用于模拟设备能力变化场景。

```java
@Data
public class SimDeviceCapability {

    private int maxWindows;          // 最大窗口数量（仅输出设备有意义）
    private boolean supportMove;     // 是否支持窗口移动
    private boolean supportResize;   // 是否支持窗口缩放
    private boolean supportOverlay;  // 是否支持窗口叠加
    private String maxResolution;    // 最大分辨率，如 1920x1080
    private int channelCount;        // 实际通道数
    private String inputChannel1;    // 输入通道1名称
    private String inputChannel2;
    private String inputChannel3;
    private String inputChannel4;
    private String inputChannel5;
    private String outputChannel1;   // 输出通道1名称
    private String outputChannel2;
    private String outputChannel3;
    private String outputChannel4;
    private String outputChannel5;

}
```

### 4.5 TlvFrame — TLV 外层帧结构

位于 `demo1-common` 的 `codec/` 包下，V4 新增。外层帧负责命令路由，Value 是嵌套的 TLV 字段序列。

```java
public class TlvFrame {
    private int type;                          // 命令类型（2 字节，大端序）
    private int length;                        // Value 字节长度（2 字节，大端序）
    private byte[] value;                      // 嵌套 TLV 字段的原始字节序列
    private Map<Byte, byte[]> fields;          // 解析后的字段映射 Tag → Value bytes（decode 时填充）
    private List<Map<Byte, byte[]>> listItems; // 列表场景：多个 TLV 字段组（decode 时填充）
}
```

### 4.6 TlvTag — 字段 Tag 常量

位于 `demo1-common` 的 `codec/` 包下。定义内层 TLV 每个业务字段的 Tag 编号。

**Tag 编码规则：**

| Tag 范围 | 值类型 | 编码方式 | 示例 |
|----------|--------|----------|------|
| 0x01-0x1F | 字符串 | UTF-8 字节 | `"OUT-1"` → `4F 55 54 2D 31` |
| 0x20-0x3F | 整数(int32) | 4字节大端序 | `1920` → `00 00 07 80` |
| 0x40-0x5F | 布尔 | 1字节 (0x00/0x01) | `true` → `01` |
| 0x60-0x7F | 整数(int16) | 2字节大端序 | 短整数 |
| 0x80-0xFF | 保留 | — | 未来扩展 |

```java
public class TlvTag {
    // ===== 字符串类型 (0x01-0x1F) =====
    public static final byte TAG_WINDOW_ID      = 0x01;
    public static final byte TAG_CHANNEL_NAME   = 0x02;
    public static final byte TAG_SOURCE_TYPE    = 0x03;
    public static final byte TAG_SOURCE_URL     = 0x04;
    public static final byte TAG_DEVICE_ID      = 0x05;
    public static final byte TAG_DEVICE_NAME    = 0x06;
    public static final byte TAG_DEVICE_MODEL   = 0x07;
    public static final byte TAG_FIRMWARE_VER   = 0x08;
    public static final byte TAG_UPTIME         = 0x09;
    public static final byte TAG_MAX_RESOLUTION = 0x0A;
    public static final byte TAG_CREATE_TIME    = 0x0B;
    public static final byte TAG_ERROR_MSG      = 0x0C;
    public static final byte TAG_CHANNEL_URL    = 0x0E;  // 通道播放地址

    // ===== 整数类型 int32 (0x20-0x3F) =====
    public static final byte TAG_X              = 0x20;
    public static final byte TAG_Y              = 0x21;
    public static final byte TAG_WIDTH          = 0x22;
    public static final byte TAG_HEIGHT         = 0x23;
    public static final byte TAG_MAX_WINDOWS    = 0x24;
    public static final byte TAG_CHANNEL_COUNT  = 0x25;
    public static final byte TAG_WINDOW_COUNT   = 0x26;
    public static final byte TAG_RESULT_CODE    = 0x27;

    // ===== 布尔类型 (0x40-0x5F) =====
    public static final byte TAG_ONLINE         = 0x40;
    public static final byte TAG_SUPPORT_MOVE   = 0x41;
    public static final byte TAG_SUPPORT_RESIZE = 0x42;
    public static final byte TAG_SUPPORT_OVERLAY= 0x43;
}
```

### 4.7 TlvCommand — 命令类型常量

位于 `demo1-common` 的 `codec/` 包下。

```java
public class TlvCommand {
    public static final int CMD_GET_INFO       = 0x0001;
    public static final int CMD_GET_STATUS     = 0x0002;
    public static final int CMD_GET_CAPABILITY = 0x0003;
    public static final int CMD_CREATE_WINDOW  = 0x0010;
    public static final int CMD_CLOSE_WINDOW   = 0x0012;
    public static final int CMD_GET_WINDOWS    = 0x0011;
    public static final int CMD_UPDATE_WINDOW  = 0x0013;
    public static final int CMD_SET_CHANNEL_URL  = 0x0020;
    public static final int CMD_GET_CHANNEL_URLS = 0x0021;
    public static final int CMD_NOTIFY_WINDOW  = 0x0030;
    public static final int RESP_INFO          = 0x8001;
    public static final int RESP_STATUS        = 0x8002;
    public static final int RESP_CAPABILITY    = 0x8003;
    public static final int RESP_WINDOW        = 0x8010;
    public static final int RESP_WINDOWS       = 0x8011;
    public static final int RESP_CHANNEL_URLS   = 0x8020;
    public static final int RESP_ERROR         = 0xFFFF;
}
```

---

## 5. 接口

TLV 模拟设备通过 UDP 通信，默认端口 **8090**（输入）/ **8092**（输出）。管控系统通过 `DeviceEndpoint` 指定 `deviceType = "TLV"` 和 `baseUrl = "udp://ip:port"`。

> **注意：** 管控系统仅接受 IP+端口 格式的 `baseUrl`，`localhost` 和域名不允许。
>
> **设备限制：** 输入设备不支持窗口操作（创建/关闭/更新窗口），仅支持查询类接口和窗口信息反馈。输出设备支持全部窗口操作。

### 5.1 接口汇总

| 命令 | Type | 方向 | 用途 | 适用设备 | Value 中的 TLV 字段 |
|------|------|------|------|:----:|------|
| CMD_GET_INFO | 0x0001 | 管控→设备 | 查询设备信息 |  全部  | 空（Length=0） |
| CMD_GET_STATUS | 0x0002 | 管控→设备 | 查询设备状态 |  全部  | 空（Length=0） |
| CMD_GET_CAPABILITY | 0x0003 | 管控→设备 | 查询设备能力 |  全部  | 空（Length=0） |
| CMD_CREATE_WINDOW | 0x0010 | 管控→设备 | 创建窗口 |  输出  | TAG_WINDOW_ID + TAG_CHANNEL_NAME + TAG_X + TAG_Y + TAG_WIDTH + TAG_HEIGHT |
| CMD_CLOSE_WINDOW | 0x0012 | 管控→设备 | 关闭窗口 |  输出  | TAG_WINDOW_ID |
| CMD_UPDATE_WINDOW | 0x0013 | 管控→设备 | 更新窗口 |  输出  | 见 5.2.6 |
| CMD_GET_WINDOWS | 0x0011 | 管控→设备 | 查询所有窗口 |  输出  | 空（Length=0） |
| CMD_SET_CHANNEL_URL | 0x0020 | 管控→设备 | 设置通道播放地址 |  输入  | TAG_CHANNEL_NAME + TAG_CHANNEL_URL |
| CMD_GET_CHANNEL_URLS | 0x0021 | 管控→设备 | 获取所有通道播放地址 |  输入  | 空（Length=0） |
| CMD_NOTIFY_WINDOW | 0x0030 | 管控→设备 | 窗口信息反馈 |  输入  | 见 5.2.7 |
| RESP_INFO | 0x8001 | 设备→管控 | 设备信息响应 |  全部  | 见 5.2.1 |
| RESP_STATUS | 0x8002 | 设备→管控 | 设备状态响应 |  全部  | 见 5.2.2 |
| RESP_CAPABILITY | 0x8003 | 设备→管控 | 设备能力响应 |  全部  | 见 5.2.3 |
| RESP_WINDOW | 0x8010 | 设备→管控 | 通用成功响应 |  输出  | 见 5.2.4 / 5.2.5 / 5.2.6 / 5.2.7 / 5.2.9 |
| RESP_WINDOWS | 0x8011 | 设备→管控 | 窗口列表响应 |  输出  | 见 5.2.8 |
| RESP_CHANNEL_URLS | 0x8020 | 设备→管控 | 通道URL响应 |  输入  | 见 5.2.10 |
| RESP_ERROR | 0xFFFF | 设备→管控 | 错误响应 |  全部  | TAG_RESULT_CODE(0) + TAG_ERROR_MSG |

### 5.2 接口详情

> **Value 格式约定：** 以下用 TLV 字段列表表示 Value 内容。实际编码时按 4.6 节 Tag 编号规则，每个字段编码为 `Tag(1B) + Length(1B) + Value(NB)` 的 TLV 条目。

#### 5.2.1 查询设备信息

**请求：** Type = `0x0001`，Value = 空（Length = 0）

**响应：** Type = `0x8001`

| Tag | 字段名 | 类型 | 说明 |
|-----|--------|------|------|
| 0x27 | code | int32 | 1=成功 |
| 0x06 | deviceName | string | 设备名称 |
| 0x07 | model | string | 设备型号 |
| 0x05 | deviceId | string | 序列号 |
| 0x25 | channelCount | int32 | 通道数 |
| 0x0A | maxResolution | string | 最大分辨率 |
| 0x02 | 通道名 | string | 实际存在的通道名（可重复出现多次） |

**示例（TLV 输入设备-1）：**

```
Value 字节序列（十六进制表示）：
27 04 00 00 00 01                          ← code=1(int32)
06 10 54 4C 56 E8 BE 93 E5 85 A5 E8 AE BE E5 A4 87 2D 31  ← deviceName="TLV输入设备-1"
07 0D 44 53 2D 44 32 30 35 35 4E 48 2D 41  ← model="DS-D2055NH-A"
05 11 53 4E 2D 54 4C 56 2D 49 4E 50 55 54 2D 30 30 31  ← deviceId="SN-TLV-INPUT-001"
25 04 00 00 00 01                          ← channelCount=1(int32)
0A 09 31 39 32 30 78 31 30 38 30           ← maxResolution="1920x1080"
02 06 48 44 4D 49 2D 31                    ← 通道名="HDMI-1"
```

#### 5.2.2 查询设备状态

**请求：** Type = `0x0002`，Value = 空（Length = 0）

**响应：** Type = `0x8002`

| Tag | 字段名 | 类型 | 说明 |
|-----|--------|------|------|
| 0x27 | code | int32 | 1=成功 |
| 0x40 | online | bool | 是否在线 |
| 0x26 | windowCount | int32 | 当前窗口数量 |
| 0x09 | uptime | string | 启动时间 |

**示例：**

```
27 04 00 00 00 01                          ← code=1
40 01 01                                   ← online=true
26 04 00 00 00 00                          ← windowCount=0
09 13 32 30 32 36 2D 30 38 2D 31 32 20 31 30 3A 30 30 3A 30 30  ← uptime="2026-08-12 10:00:00"
```

#### 5.2.3 查询设备能力

**请求：** Type = `0x0003`，Value = 空（Length = 0）

**响应：** Type = `0x8003`

| Tag | 字段名 | 类型 | 说明 |
|-----|--------|------|------|
| 0x27 | code | int32 | 1=成功 |
| 0x24 | maxWindows | int32 | 最大窗口数 |
| 0x41 | supportMove | bool | 是否支持移动 |
| 0x42 | supportResize | bool | 是否支持缩放 |
| 0x43 | supportOverlay | bool | 是否支持叠加 |
| 0x0A | maxResolution | string | 最大分辨率 |
| 0x25 | channelCount | int32 | 通道数 |
| 0x02 | 通道名 | string | 实际存在的通道名（可重复出现多次） |

**示例（TLV 输出设备-1）：**

```
27 04 00 00 00 01                          ← code=1
24 04 00 00 00 04                          ← maxWindows=4
41 01 01                                   ← supportMove=true
42 01 01                                   ← supportResize=true
43 01 01                                   ← supportOverlay=true
0A 09 31 39 32 30 78 31 30 38 30           ← maxResolution="1920x1080"
25 04 00 00 00 02                          ← channelCount=2
02 05 4F 55 54 2D 31                       ← 通道名="OUT-1"
02 05 4F 55 54 2D 32                       ← 通道名="OUT-2"
```

#### 5.2.4 创建窗口

**请求：** Type = `0x0010`

| Tag | 字段名 | 类型 | 必填 | 说明 |
|-----|--------|------|:--:|------|
| 0x01 | windowId | string | 是 | 窗口唯一标识 |
| 0x02 | channelName | string | 是 | 绑定的通道名 |
| 0x20 | x | int32 | 否 | 左上角X坐标，不传默认0 |
| 0x21 | y | int32 | 否 | 左上角Y坐标，不传默认0 |
| 0x22 | width | int32 | 否 | 窗口宽度，不传默认1920 |
| 0x23 | height | int32 | 否 | 窗口高度，不传默认1080 |

**请求示例：**

```
01 07 77 69 6E 2D 30 30 31                 ← windowId="win-001"
02 06 48 44 4D 49 2D 31                    ← channelName="HDMI-1"
20 04 00 00 00 00                          ← x=0
21 04 00 00 00 00                          ← y=0
22 04 00 00 03 C0                          ← width=960
23 04 00 00 02 1C                          ← height=540
```

**成功响应：** Type = `0x8010`

| Tag | 字段名 | 类型 | 说明 |
|-----|--------|------|------|
| 0x27 | code | int32 | 1=成功 |
| 0x01 | windowId | string | 窗口ID |
| 0x02 | channelName | string | 通道名 |
| 0x20 | x | int32 | X坐标 |
| 0x21 | y | int32 | Y坐标 |
| 0x22 | width | int32 | 宽度 |
| 0x23 | height | int32 | 高度 |
| 0x03 | sourceType | string | 信号源类型 |
| 0x04 | sourceUrl | string | 信号源地址 |
| 0x0B | createTime | string | 创建时间 |

**失败响应（窗口 ID 重复）：** Type = `0xFFFF`

```
27 04 00 00 00 00                          ← code=0
0C 19 E7 AA 97 E5 8F A3 E5 B7 B2 E5 AD 98 E5 9C A8 3A 20 77 69 6E 2D 30 30 31  ← msg="窗口已存在: win-001"
```

**失败响应（通道名无效）：** Type = `0xFFFF`

```
27 04 00 00 00 00                          ← code=0
0C 16 E9 80 9A E9 81 93 E5 90 8D E6 97 A0 E6 95 88 3A 20 4F 55 54 2D 39 39  ← msg="通道名无效: OUT-99"
```

#### 5.2.5 关闭窗口

**请求：** Type = `0x0012`

| Tag | 字段名 | 类型 | 必填 | 说明 |
|-----|--------|------|:--:|------|
| 0x01 | windowId | string | 是 | 窗口唯一标识 |

**请求示例：**

```
01 07 77 69 6E 2D 30 30 31                 ← windowId="win-001"
```

**成功响应：** Type = `0x8010`

```
27 04 00 00 00 01                          ← code=1
```

**失败响应（窗口不存在）：** Type = `0xFFFF`

```
27 04 00 00 00 00                          ← code=0
0C 17 E7 AA 97 E5 8F A3 E4 B8 8D E5 AD 98 E5 9C A8 3A 20 77 69 6E 2D 39 39 39  ← msg="窗口不存在: win-999"
```

#### 5.2.6 更新窗口

仅输出设备支持。直接更新窗口属性（位置/大小/通道），**无需关旧建新**。只传变更的字段，未传字段保持原值不变。

**请求：** Type = `0x0013`

| Tag | 字段名 | 类型 | 必填 | 说明 |
|-----|--------|------|:--:|------|
| 0x01 | windowId | string | 是 | 要更新的窗口ID |
| 0x02 | channelName | string | 否 | 切换绑定的通道 |
| 0x20 | x | int32 | 否 | 新X坐标 |
| 0x21 | y | int32 | 否 | 新Y坐标 |
| 0x22 | width | int32 | 否 | 新宽度 |
| 0x23 | height | int32 | 否 | 新高度 |

**请求示例（移动窗口到 (100, 200)）：**

```
01 07 77 69 6E 2D 30 30 31                 ← windowId="win-001"
20 04 00 00 00 64                          ← x=100
21 04 00 00 00 C8                          ← y=200
```

**成功响应：** Type = `0x8010`，返回更新后的完整窗口数据（字段同 5.2.4 成功响应）

**失败响应（窗口不存在）：** Type = `0xFFFF`

```
27 04 00 00 00 00                          ← code=0
0C 17 E7 AA 97 E5 8F A3 E4 B8 8D E5 AD 98 E5 9C A8 3A 20 77 69 6E 2D 39 39 39  ← msg="窗口不存在: win-999"
```

#### 5.2.7 窗口信息反馈

仅输入设备支持。管控系统在输出设备上创建/更新/关闭窗口后，找到该窗口信号源对应的输入设备，推送窗口信息，让输入设备感知其通道的占用情况。

**请求：** Type = `0x0030`

| Tag | 字段名 | 类型 | 必填 | 说明 |
|-----|--------|------|:--:|------|
| 0x01 | windowId | string | 是 | 窗口ID |
| 0x02 | channelName | string | 是 | 占用的输入通道名（必须是该设备已定义的通道） |
| 0x20 | x | int32 | 否 | 窗口X坐标 |
| 0x21 | y | int32 | 否 | 窗口Y坐标 |
| 0x22 | width | int32 | 否 | 窗口宽度 |
| 0x23 | height | int32 | 否 | 窗口高度 |

**推送时机：**

| 输出设备操作 | 输入设备收到的 CMD_NOTIFY_WINDOW |
|-------------|------|
| 创建窗口 | 含完整字段 |
| 更新窗口 | 含变更字段 |
| 关闭窗口 | 仅 windowId + channelName（坐标字段不传） |

**请求示例（创建窗口时通知）：**

```
01 07 77 69 6E 2D 30 30 31                 ← windowId="win-001"
02 06 48 44 4D 49 2D 31                    ← channelName="HDMI-1"
20 04 00 00 00 00                          ← x=0
21 04 00 00 00 00                          ← y=0
22 04 00 00 03 C0                          ← width=960
23 04 00 00 02 1C                          ← height=540
```

**请求示例（关闭窗口时通知）：**

```
01 07 77 69 6E 2D 30 30 31                 ← windowId="win-001"
02 06 48 44 4D 49 2D 31                    ← channelName="HDMI-1"
```

**成功响应：** Type = `0x8010`

```
27 04 00 00 00 01                          ← code=1
```

**输入设备侧存储：** 内存中维护 `Map<String, Set<String>>`（channelName → 占用该通道的 windowId 集合），用于查询设备状态时返回通道占用情况。

| 收到通知 | 操作 |
|---------|------|
| 创建（windowId 不存在） | `channelWindows[channelName].add(windowId)` |
| 更新（windowId 已存在） | 更新坐标信息 |
| 关闭（仅 windowId + channelName） | `channelWindows[channelName].remove(windowId)` |

#### 5.2.8 查询窗口列表

**请求：** Type = `0x0011`，Value = 空（Length = 0）

**响应：** Type = `0x8011`

| Tag | 字段名 | 类型 | 说明 |
|-----|--------|------|------|
| 0x27 | code | int32 | 1=成功 |
| 0x26 | windowCount | int32 | 窗口数量（N） |
| (以下 N 组) | | | |
| 0x01 | windowId | string | 窗口ID |
| 0x02 | channelName | string | 通道名 |
| 0x20 | x | int32 | X坐标 |
| 0x21 | y | int32 | Y坐标 |
| 0x22 | width | int32 | 宽度 |
| 0x23 | height | int32 | 高度 |
| 0x03 | sourceType | string | 信号源类型 |
| 0x04 | sourceUrl | string | 信号源地址 |
| 0x0B | createTime | string | 创建时间 |

> **解析规则：** 先读 `windowCount`，然后循环解析 N 组窗口字段。每组窗口字段的 Tag 会重复出现，按顺序解析即可。

**示例（有 1 个窗口）：**

```
27 04 00 00 00 01                          ← code=1
26 04 00 00 00 01                          ← windowCount=1
01 07 77 69 6E 2D 30 30 31                 ← windowId="win-001"
02 06 48 44 4D 49 2D 31                    ← channelName="HDMI-1"
20 04 00 00 00 00                          ← x=0
21 04 00 00 00 00                          ← y=0
22 04 00 00 03 C0                          ← width=960
23 04 00 00 02 1C                          ← height=540
03 04 48 44 4D 49                          ← sourceType="HDMI"
04 00                                      ← sourceUrl=""（空字符串，Length=0）
0B 13 32 30 32 36 2D 30 38 2D 31 32 20 31 34 3A 33 30 3A 30 30  ← createTime="2026-08-12 14:30:00"
```

> 若无窗口，`windowCount=0`，后面无窗口字段。

#### 5.2.9 设置通道播放地址

为指定输入通道配置信号源 URL，供前端预览使用。**仅输入设备支持。**

**请求：** Type = `0x0020`

| Tag | 字段名 | 类型 | 必填 | 说明 |
|-----|--------|------|:--:|------|
| 0x02 | channelName | string | 是 | 输入通道名称 |
| 0x0E | sourceUrl | string | 否 | 信号源播放地址，空字节表示清空 |

**请求示例：**

```
02 06 48 44 4D 49 2D 31                    ← channelName="HDMI-1"
0E 1E 72 74 73 70 3A 2F 2F 31 39 32 2E 31 36 38 2E 31 2E 35 30 3A 35 35 34 2F 73 74 72 65 61 6D 31  ← sourceUrl="rtsp://192.168.1.50:554/stream1"
```

**成功响应：** Type = `0x8010`

```
27 04 00 00 00 01                          ← code=1
```

**失败响应（通道名无效）：** Type = `0xFFFF`

```
27 04 00 00 00 00                          ← code=0
0C 16 E9 80 9A E9 81 93 E5 90 8D E6 97 A0 E6 95 88 3A 20 4F 55 54 2D 39 39  ← msg="通道名无效: OUT-99"
```

#### 5.2.10 获取所有通道播放地址

返回当前设备所有通道名到播放地址的映射。**仅输入设备支持。**

**请求：** Type = `0x0021`，Value = 空（Length = 0）

**响应：** Type = `0x8020`

| Tag | 字段名 | 类型 | 说明 |
|-----|--------|------|------|
| 0x27 | code | int32 | 1=成功 |
| (以下 K-V 对，重复出现) | | | |
| 0x02 | channelName | string | 通道名 |
| 0x0E | sourceUrl | string | 对应播放地址 |

**示例：**

```
27 04 00 00 00 01                          ← code=1
02 06 48 44 4D 49 2D 31                    ← channelName="HDMI-1"
0E 1E 72 74 73 70 3A 2F 2F 31 39 32 2E 31 36 38 2E 31 2E 35 30 3A 35 35 34 2F 73 74 72 65 61 6D 31  ← sourceUrl="rtsp://192.168.1.50:554/stream1"
02 06 48 44 4D 49 2D 32                    ← channelName="HDMI-2"
0E 00                                      ← sourceUrl=""（空字符串）
```

---

## 6. 要完成的功能（流程图）

### 6.1 设备信息获取流程

```text
管控系统业务代码
    │
    ▼
DeviceDriver.getInfo(endpoint)
    │
    └── TlvDeviceDriver
            │
            ├── 1. 构造 TlvFrame(type=0x0001, value=空)
            ├── 2. TlvEncoder.encode() → byte[]
            ├── 3. UDP 发送到 baseUrl（udp://ip:port）
            ├── 4. UDP 接收响应字节
            ├── 5. TlvDecoder.decode() → TlvFrame(type=0x8001)
            └── 6. TlvFieldCodec.decodeFields(value) → Map<Byte,byte[]> → 组装 SimDeviceInfo
```

### 6.2 窗口创建、更新与关闭流程

```text
管控系统 → CMD_CREATE_WINDOW (0x0010)

  ├── TlvDeviceDriver 用 TlvFieldCodec 编码字段: windowId, channelName, x, y, width, height
  ├── TlvEncoder.encode() → UDP 发送
  │
  └── TLV 模拟设备 TlvServer 收到
        ├── TlvDecoder.decode() → TlvFieldCodec.decodeFields(value) → 解析各字段
        ├── 校验 channelName 是否为有效通道
        │     └── 无效 → RESP_ERROR "通道名无效"
        ├── 校验 windowId 是否重复
        │     └── 重复 → RESP_ERROR "窗口已存在"
        ├── 校验窗口总数是否超过 maxWindows
        │     └── 超限 → RESP_ERROR "窗口数量已达上限"
        └── 校验通过 → 存入 ConcurrentHashMap → RESP_WINDOW

管控系统 → CMD_UPDATE_WINDOW (0x0013)

  ├── TlvDeviceDriver 用 TlvFieldCodec 编码字段: windowId + 需变更的字段
  ├── TlvEncoder.encode() → UDP 发送
  │
  └── TLV 模拟设备（输出）TlvServer 收到
        ├── TlvDecoder.decode() → TlvFieldCodec.decodeFields(value) → 解析各字段
        ├── 校验 windowId 是否存在
        │     └── 不存在 → RESP_ERROR "窗口不存在"
        ├── 仅更新传入的字段，未传字段保持原值不变
        └── 更新 ConcurrentHashMap → RESP_WINDOW（返回完整窗口数据）

管控系统 → CMD_CLOSE_WINDOW (0x0012)

  └── TLV 模拟设备（输出）TlvServer 收到
        ├── 校验 windowId 是否存在
        │     └── 不存在 → RESP_ERROR "窗口不存在"
        └── 存在 → 从 ConcurrentHashMap 移除 → RESP_WINDOW

管控系统 → CMD_NOTIFY_WINDOW (0x0030)

  └── 管控系统在输出设备上操作窗口后，找到该窗口的信号源对应的输入设备
        └── 向输入设备发送 CMD_NOTIFY_WINDOW，告知窗口与通道的占用关系
              └── 输入设备 NotifyWindowHandler 更新 channelWindows 映射
```

### 6.3 设备发现流程

```text
管控系统启动
  │
  ├── UDP 广播:255.255.255.255:9995 → TLV 输入设备
  └── UDP 广播:255.255.255.255:9993 → TLV 输出设备

各 TLV 模拟设备 DiscoveryListener 收到 {"action":"discovery"}
  └── 单播回复 {"deviceType":"TLV","baseUrl":"udp://192.168.1.100:8090"}

管控系统 3 秒超时收集所有回复
  └── 汇总返回给前端，用户选择添加设备
        └── POST /device → 管控系统通过 TlvDeviceDriver 拉取设备详细信息
```

### 6.4 窗口查询流程

```text
管控系统
    │
    └── DeviceDriver.getWindows(endpoint)
            │
            └── TlvDeviceDriver
                    │
                    ├── 1. 构造 TlvFrame(type=0x0011, value=空)
                    ├── 2. TlvEncoder.encode() → byte[]
                    ├── 3. UDP 发送
                    ├── 4. UDP 接收响应字节
                    ├── 5. TlvDecoder.decode() → TlvFrame(type=0x8011)
                    └── 6. TlvFieldCodec.decodeList(value) → List<Map<Byte,byte[]>> → 组装 List<SimWindow>
```

### 6.5 设备能力查询流程

```text
管控系统
    │
    └── DeviceDriver.getCapability(endpoint)
            │
            └── TlvDeviceDriver
                    │
                    ├── 1. 构造 TlvFrame(type=0x0003, value=空)
                    ├── 2. TlvEncoder.encode() → byte[]
                    ├── 3. UDP 发送
                    ├── 4. UDP 接收响应字节
                    ├── 5. TlvDecoder.decode() → TlvFrame(type=0x8003)
                    └── 6. TlvFieldCodec.decodeFields(value) → Map<Byte,byte[]> → 组装 SimDeviceCapability
```

---

## 7. 补充

### 7.1 默认设备

系统提供 4 台 TLV 模拟设备，通过 `main` 方法启动不同进程。每台设备进程启动后自动初始化，管控系统启动后即可直接查询。

| 模块 | 端口 | 发现端口 | 类别 | 通道 | maxWindows |
|------|:--:|:--:|:--:|------|:--:|
| demo1-simulator-tlv-input | 8090 | 9995 | INPUT | 1个输入：HDMI-1 | — |
| demo1-simulator-tlv-input | 8091 | 9994 | INPUT | 2个输入：HDMI-1, HDMI-2 | — |
| demo1-simulator-tlv-output | 8092 | 9993 | OUTPUT | 2个输出：OUT-1, OUT-2 | 4 |
| demo1-simulator-tlv-output | 8093 | 9992 | OUTPUT | 3个输出：OUT-1, OUT-2, OUT-3 | 6 |

### 7.2 存储方式

| 数据类型 | 存储方式 | 说明 |
|------|------|------|
| 设备基本信息 | JSON 配置文件 | 启动时一次性加载到内存 |
| 设备能力 | JSON 配置文件 | 启动时一次性加载到内存 |
| 窗口数据（输出设备） | 内存 `ConcurrentHashMap` | 不持久化，进程重启后丢失，由管控系统重新推送 |
| 窗口占用映射（输入设备） | 内存 `Map<String, Set<String>>` | 不持久化，channelName → 占用该通道的 windowId 集合 |

**配置文件：** 每台模拟设备使用独立的 JSON 配置文件，以设备端口命名。

| 模拟设备 | 配置文件 |
|---------|-----------|
| TLV 输入设备-1（端口 8090） | `device-8090.json` |
| TLV 输入设备-2（端口 8091） | `device-8091.json` |
| TLV 输出设备-1（端口 8092） | `device-8092.json` |
| TLV 输出设备-2（端口 8093） | `device-8093.json` |

### 7.3 启动方式

**一进程一设备**，每个设备对应一个 JSON 配置文件，文件名按端口号命名，如 `device-8090.json`。`main` 方法接收端口号作为参数，启动 Spring Boot 空壳容器。

**SimulatorApplication.java：**

```java
@SpringBootApplication
public class SimulatorApplication {
    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);  // 端口号，如 8090

        SpringApplication app = new SpringApplication(SimulatorApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);  // 不启动 Tomcat/SpringMVC
        app.setDefaultProperties(Collections.singletonMap("server.port", port));
        app.run();

        // TlvServer、DiscoveryListener、FrontendServer 通过 @Component + @PostConstruct 自动启动
    }
}
```

> `WebApplicationType.NONE` 仅启动 IoC 容器，不启动内嵌 Web 服务器。`TlvServer`（UDP）、`DiscoveryListener`（UDP）、`FrontendServer`（JDK HttpServer）均通过 `@Component` + `@PostConstruct` 自动装配和启动。

**IDEA 运行配置：** 创建 4 个 Run Configuration，每个只填端口号，启动即为一台设备，可单独启停：

```
IDEA Run Configurations:
  ├── TLV-Input-8090    → Program arguments: 8090
  ├── TLV-Input-8091    → Program arguments: 8091
  ├── TLV-Output-8092   → Program arguments: 8092
  └── TLV-Output-8093   → Program arguments: 8093
```

> **部署运行：** 也支持命令行方式 `java -jar demo1-simulator-tlv-input.jar 8090`，本质相同。

### 7.3.1 TlvServer 设计
### 7.3.1 TlvServer 设计

`TlvServer` 是模拟设备的核心，负责 UDP 监听和命令分发。编解码器（`TlvEncoder`、`TlvDecoder`、`TlvFieldCodec`、`Crc16`）已在 `demo1-common` 中实现，`TlvServer` 只需调用即可。

#### 核心职责

- 监听指定 UDP 端口，接收管控系统下发的 TLV 命令帧
- 校验 Magic 和 CRC16，过滤非法/损坏数据包
- 根据命令类型（Type 字段）将请求分发到对应的命令处理器（CmdHandler）
- 将处理器返回的响应字节序列通过 UDP 原路发回（Seq 原样回填）

#### 核心组件

| 组件 | 说明 |
|------|------|
| **TlvServer** | UDP 服务器，持有 `DeviceConfig` 和 `handlerMap`（命令号 → 处理器映射），提供 `registerHandler()` 注册处理器、`start()` 启动死循环监听 |
| **CmdHandler** | 命令处理器接口，定义 `handle(byte[] value)` 方法，接收内层 Value 字节序列，返回响应字节序列 |
| **具体 Handler** | 每个命令对应一个实现类（如 `GetInfoHandler`、`CreateWindowHandler`），内部完成：解码请求 → 业务处理 → 编码响应 |

#### 请求处理流程

```
UDP 收包 → TlvDecoder 解码外层帧
    → 校验 Magic（不匹配则丢弃）
    → 校验 CRC16（不匹配则丢弃）
    → 提取 Seq、Type、Value
    → handlerMap 根据 Type 查找对应 CmdHandler
    → CmdHandler.handle(Value) 处理请求
        → 内部：TlvFieldCodec 解码 Value 中各字段
        → 业务逻辑处理
        → TlvFieldCodec 编码响应字段 → TlvEncoder.encode(seq, frame) 编码外层帧（含 Magic、CRC16，Seq 原样回填）
    → UDP 发送响应帧
```

#### 设计要点

- `main` 方法负责读取配置、创建 Handler、注册到 `TlvServer`；`TlvServer.start()` 启动死循环监听 UDP 端口
- 收到数据后先校验 Magic 和 CRC16，校验失败静默丢弃
- 通过后根据命令号从 `handlerMap` 取出对应 Handler 执行
- Handler 内部完成解码 → 业务处理 → 编码 → 返回响应
- 响应帧 Seq 原样回填请求帧的 Seq，实现请求-响应匹配
- 整个链路无 Spring Boot 依赖，纯 Java 标准库实现
- 新增命令只需新增一个 CmdHandler 实现类并注册即可，无需修改 TlvServer 核心代码
### 7.4 TLV 协议帧格式

#### 7.4.1 外层帧（命令级）

```
+---------+---------+---------+---------+-------------------+---------+
|  Magic  |   Seq   |  Type   | Length  |  Value (TLV列表)  |  CRC16  |
| 2 bytes | 2 bytes | 2 bytes | 2 bytes |     N bytes       | 2 bytes |
+---------+---------+---------+---------+-------------------+---------+
|                    Header (8 bytes)     |     Payload       |  Tail   |
+-----------------------------------------+-------------------+---------+
```

| 字段 | 字节数 | 说明 |
|------|--------|------|
| Magic | 2 | 固定值 `0xEB 0x90`，用于快速识别 TLV 协议帧，过滤非协议包 |
| Seq | 2 | 序列号（大端序），用于请求-响应匹配。请求方自增生成，响应方原样回填 |
| Type | 2 | 命令类型，标识请求/响应语义（大端序） |
| Length | 2 | Value 部分的总字节长度（大端序） |
| Value | N | 内层 TLV 字段序列 |
| CRC16 | 2 | CRC-16/XMODEM 校验（多项式 `0x1021`），覆盖范围：Magic + Seq + Type + Length + Value，大端序。校验失败静默丢弃 |
#### 7.4.2 内层字段（字段级）

Value 由多个 TLV 条目组成，每个条目对应一个业务字段：

```
+---------+---------+----------+
|  Tag    | Length  |  Value   |
| 1 byte  | 1 byte  | N bytes  |
+---------+---------+----------+
```

| 字段 | 字节数 | 说明 |
|------|--------|------|
| Tag | 1 | 字段标识，见 `TlvTag` 常量定义 |
| Length | 1 | 该字段 Value 的字节长度（最大 255） |
| Value | N | 字段值，编码方式取决于 Tag 所属类型 |

**字段类型编码规则：**

| Tag 范围 | 值类型 | 编码方式 |
|----------|--------|----------|
| 0x01-0x1F | 字符串 | UTF-8 字节 |
| 0x20-0x3F | 整数(int32) | 4字节大端序 |
| 0x40-0x5F | 布尔 | 1字节 (0x00=false, 0x01=true) |
| 0x60-0x7F | 整数(int16) | 2字节大端序 |

**列表编码规则：** 列表类型（如窗口列表）先编码一个 `TAG_WINDOW_COUNT`（int32）表示元素数量 N，然后依次编码 N 组字段。每组字段的 Tag 可重复出现。

**空值/可选字段处理：** 可选字段（如 `x`、`y`、`width`、`height`）不传时直接不编码，接收方按默认值处理。空字符串编码为 Length=0、Value 为空。

> 外层用 2+2+2+2 字节头（Magic+Seq+Type+Length）+ 2 字节尾（CRC16），内层用 1+1 字节头（Tag+Length）——Magic 用于帧识别，Seq 用于请求-响应匹配，CRC16 保证数据完整性；外层命令空间大、Value 可能超过 255 字节；内层单字段通常不超过 255 字节，1 字节头节省开销。

### 7.5 编解码器设计

位于 `demo1-common/src/main/java/com/example/demo/codec/`。

**编解码分层：**

| 层级 | 类 | 职责 |
|------|-----|------|
| 帧校验 | `Crc16` | CRC-16/XMODEM 校验（多项式 `0x1021`），提供 `compute(byte[])` 和 `verify(byte[])` |
| 外层帧 | `TlvEncoder` / `TlvDecoder` | 编解码 Magic + Seq + Type + Length + Value + CRC16 外层帧，不关心 Value 内部格式 |
| 内层字段 | `TlvFieldCodec` | 编解码 Value 内部的 Tag-Length-Value 字段序列 |

**Crc16.java**

```java
public class Crc16 {
    // CRC-16/XMODEM 多项式 0x1021
    public static short compute(byte[] data);
    public static short compute(byte[] data, int offset, int length);

    // 校验：data 末尾 2 字节为 CRC16，重算后比对
    public static boolean verify(byte[] data);
}
```

**TlvEncoder.java**

```java
public class TlvEncoder {
    public static byte[] encode(int seq, TlvFrame frame) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // Magic
        bos.write(0xEB);
        bos.write(0x90);
        // Seq
        bos.write((seq >> 8) & 0xFF);
        bos.write(seq & 0xFF);
        // Type
        bos.write((frame.getType() >> 8) & 0xFF);
        bos.write(frame.getType() & 0xFF);
        // Length
        int len = frame.getLength();
        bos.write((len >> 8) & 0xFF);
        bos.write(len & 0xFF);
        // Value
        bos.write(frame.getValue());
        // CRC16（覆盖前面所有字节）
        byte[] data = bos.toByteArray();
        short crc = Crc16.compute(data);
        bos.write((crc >> 8) & 0xFF);
        bos.write(crc & 0xFF);
        return bos.toByteArray();
    }
}
```

**TlvDecoder.java**

```java
public class TlvDecoder {
    public static TlvFrame decode(byte[] data) {
        // 1. 校验 Magic
        if ((data[0] & 0xFF) != 0xEB || (data[1] & 0xFF) != 0x90) {
            throw new IllegalArgumentException("Invalid magic number");
        }
        // 2. 校验 CRC16
        if (!Crc16.verify(data)) {
            throw new IllegalArgumentException("CRC16 mismatch");
        }
        // 3. 提取字段
        int seq = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int type = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int length = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        byte[] value = new byte[length];
        System.arraycopy(data, 8, value, 0, length);
        TlvFrame frame = new TlvFrame(type, length, value);
        frame.setSeq(seq);
        frame.setFields(TlvFieldCodec.decodeFields(value));
        return frame;
    }
}
```

**TlvFieldCodec.java**

```java
public class TlvFieldCodec {

    // === 编码：单字段 →  TLV 条目字节 ===
    public static byte[] encodeField(byte tag, byte[] value);
    public static byte[] encodeString(byte tag, String value);
    public static byte[] encodeInt32(byte tag, int value);
    public static byte[] encodeBool(byte tag, boolean value);

    // === 编码：多字段 Map →  Value 字节序列 ===
    public static byte[] encodeFields(Map<Byte, byte[]> fields);

    // === 解码：Value 字节序列 → 字段 Map ===
    public static Map<Byte, byte[]> decodeFields(byte[] value);
    public static Map<Byte, byte[]> decodeFields(byte[] value, int offset, int length);

    // === 便捷读取 ===
    public static String getString(Map<Byte, byte[]> fields, byte tag);
    public static Integer getInt32(Map<Byte, byte[]> fields, byte tag);
    public static Boolean getBool(Map<Byte, byte[]> fields, byte tag);

    // === 列表解码 ===
    public static List<Map<Byte, byte[]>> decodeList(byte[] value);
}
```

> **使用方：** 管控侧 `TlvDeviceDriver` 生成 Seq → 调 `TlvFieldCodec.encodeFields()` 编请求字段 → `TlvEncoder.encode(seq, frame)` 编外层帧（含 Magic、CRC16）；模拟器侧 `TlvServer` 调 `TlvDecoder.decode()` 解外层帧（校验 Magic、CRC16） → `TlvFieldCodec.decodeFields()` 解字段。响应方向同理，Seq 原样回填。
### 7.6 超时与重试

| 参数 | 值 | 说明 |
|------|-----|------|
| 发送超时 | 3 秒 | 单次 UDP 请求-响应超时 |
| 重试次数 | 2 次 | 超时后重试，共 3 次机会 |
| 重试间隔 | 无 | 立即重试 |

> UDP 丢包是正常现象，2 次重试可覆盖大部分网络抖动场景。管控系统已有的 `retryPendingWindows` 机制会兜底处理最终失败的情况。

### 7.7 统一返回格式

所有响应 Value 统一包含 `code` 字段，`code=1` 表示成功，`code=0` 表示失败。失败时附带 `TAG_ERROR_MSG` 错误信息。

**成功响应 Value 结构：**

```
TAG_RESULT_CODE(0x27) = 1    ← int32
+ 业务字段...
```

**失败响应 Value 结构：**

```
TAG_RESULT_CODE(0x27) = 0    ← int32
TAG_ERROR_MSG(0x0C) = 错误描述  ← string
```

> 不再使用 JSON 的 `Result<T>` 封装结构，`code` 和 `msg` 作为普通 TLV 字段扁平化编码在 Value 中。

### 7.8 与管控系统的关系

管控系统通过 `DeviceDriver` 统一接口调用两类设备，业务代码不感知设备类型差异。TLV 模拟设备与管控系统是**两个独立进程**，各自启动：

| 模块 | 端口 | 发现端口 | 说明 |
|------|:--:|:--:|------|
| demo1-server（管控系统） | - | - | 通过 IDEA / mvn 启动 |
| TLV 输入设备-1 | 8090 | 9995 | 1个输入通道，仅查询接口 + 窗口信息反馈 |
| TLV 输入设备-2 | 8091 | 9994 | 2个输入通道，仅查询接口 + 窗口信息反馈 |
| TLV 输出设备-1 | 8092 | 9993 | 2个输出通道，创建/关闭/更新/查询窗口 |
| TLV 输出设备-2 | 8093 | 9992 | 3个输出通道，创建/关闭/更新/查询窗口 |

```text
DeviceDriver (接口)
    ├── RestDeviceDriver   ← HTTP + JSON
    └── TlvDeviceDriver    ← UDP + TLV（V4 新增）
            │
            ├── 依赖 demo1-common/codec/TlvFrame      ← TLV 外层帧结构
            ├── 依赖 demo1-common/codec/TlvTag        ← 字段 Tag 常量
            ├── 依赖 demo1-common/codec/TlvCommand    ← 命令类型常量
            ├── 依赖 demo1-common/codec/TlvEncoder    ← 外层帧编码
            ├── 依赖 demo1-common/codec/TlvDecoder    ← 外层帧解码
            └── 依赖 demo1-common/codec/TlvFieldCodec ← 内层字段编解码
```

---

## 8. 模拟设备前端

### 8.1 概述

每台 TLV 模拟设备通过 JDK 内置 `HttpServer` 提供前端管理页面，用于浏览器查看设备状态、内存窗口列表等调试信息。

| 特性 | 说明 |
|------|------|
| 技术 | JDK 内置 `com.sun.net.httpserver.HttpServer` |
| 端口 | 与 UDP 通信共用同一端口（TCP 连接） |
| 功能 | 静态页面 + JSON API |
| 依赖 | 零额外依赖 |

### 8.2 架构

```text
浏览器                     HttpServer (port)              Spring IoC 容器
  │                            │                              │
  ├── GET / ──────────────────→│  返回 static/index.html       │
  ├── GET /api/device ────────→│  调用 DeviceConfig.getBean()  │
  ├── GET /api/windows ───────→│  调用 SimDeviceManager.getBean()  │
  ├── GET /api/capability ────→│  调用 DeviceConfig.getBean()  │
  └── POST /api/refresh ──────→│  刷新内存数据                │
```

### 8.3 FrontendServer 设计

```java
@Component
public class FrontendServer {

    @Value("${server.port}")
    private int port;

    @Autowired
    private DeviceConfig deviceConfig;

        @Autowired
    private SimDeviceManager deviceManager;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer httpServer;

    @PostConstruct
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(2));

        httpServer.createContext("/", exchange -> {
            // 返回 static/index.html 静态页面
            byte[] bytes = Files.readAllBytes(Paths.get("static/index.html"));
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        httpServer.createContext("/api/device", exchange -> {
            // JSON API: 返回设备信息
            String json = objectMapper.writeValueAsString(deviceConfig.getDeviceInfo());
            sendJson(exchange, json);
        });

        httpServer.createContext("/api/windows", exchange -> {
            // JSON API: 返回窗口列表
            String json = objectMapper.writeValueAsString(deviceManager.getWindows());
            sendJson(exchange, json);
        });

        httpServer.createContext("/api/capability", exchange -> {
            // JSON API: 返回设备能力
            String json = objectMapper.writeValueAsString(deviceConfig.getDeviceCapability());
            sendJson(exchange, json);
        });

        httpServer.start();
    }

    @PreDestroy
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }
}
```

### 8.4 前端管理页面

`index.html` 为纯静态页面，通过 fetch 调用本机 JSON API，展示设备状态和窗口列表。

### 8.5 三通道端口汇总

| 通道 | 协议 | 端口 | 说明 |
|------|------|:--:|------|
| 管控系统 ↔ 模拟器 | UDP + TLV 二进制 | port | 业务指令下发与响应 |
| 设备发现 | UDP 广播 | discoveryPort | 管控系统扫描局域网设备 |
| 前端管理页面 | HTTP（JDK HttpServer） | port（TCP） | 浏览器查看/调试设备状态 |

### 8.6 端口规划

| 模块 | 端口 | 发现端口 |
|------|:--:|:--:|
| TLV 输入设备-1 | 8090 | 9995 |
| TLV 输入设备-2 | 8091 | 9994 |
| TLV 输出设备-1 | 8092 | 9993 |
| TLV 输出设备-2 | 8093 | 9992 |

> 设备端口（TCP 前端 + UDP 通信）和发现端口（UDP 广播）分离，与 REST 模拟器模式一致。端口范围 8090-8093 与 REST 模拟器（8086-8089）不重叠，发现端口 9992-9995 与 REST 模拟器（9996-9999）不重叠。





