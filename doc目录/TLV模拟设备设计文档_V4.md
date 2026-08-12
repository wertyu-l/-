# TLV 模拟设备设计文档

## 1. 概述

### 1.1 模块定位

TLV 模拟设备是 V4 阶段新增的**第二类异构设备**，通过 UDP + TLV 二进制协议与管控系统通信。

**V4 核心目标：验证 `DeviceDriver` 统一抽象接口的正确性**——管控系统业务代码不改一行，通过 `DeviceEndpoint.deviceType` 区分设备类型，同一套开窗接口同时控制多类设备。

> **设计原则：** TLV 模拟设备使用 `demo1-common` 公共数据模型，通信层采用 UDP + TLV 二进制协议。
>
> **设备类别说明：** 模拟设备分为两类，由 `deviceCategory` 字段标识（`INPUT`/`OUTPUT`）：
> - **输入设备（`deviceCategory = "INPUT"`）：** 拥有输入通道，负责提供信号源。支持窗口创建/关闭/查询。不具备窗口移动、缩放、叠加的设备能力，DEVICE_CAPABILITY 表无 `max_windows` 字段。
> - **输出设备（`deviceCategory = "OUTPUT"`）：** 拥有输出通道，用于大屏绑定显示。输出设备不支持窗口操作，仅提供设备信息、状态、能力查询接口。DEVICE_CAPABILITY 表有 `max_windows` 字段。

---

## 2. 模块结构

### 2.1 文件结构

TLV 模拟设备是独立 Java 程序，共有 2 个模拟器模块，每个模块可通过命令行参数启动多个进程，每个进程代表一台设备。数据模型与管控系统共享 `demo1-common`，编解码器也放在 `demo1-common` 中供模拟器和管控系统共用。

**demo1-common（V4 新增编解码器）**

```
demo1-common/src/main/java/com/example/demo/codec/   ← V4 新增
    ├── TlvFrame.java            ← TLV 帧定义（Type + Length + Value）
    ├── TlvCommand.java          ← 命令类型常量
    ├── TlvEncoder.java          ← 编码器：TlvFrame → byte[]
    └── TlvDecoder.java          ← 解码器：byte[] → TlvFrame
```

> 管控系统的 `TlvDeviceDriver` 编请求包、模拟器的 `TlvServer` 解请求包，两边都需要编解码，放在公共模块避免重复。

**demo1-simulator-tlv-input**（输入设备模块）

```
demo1-simulator-tlv-input/
└── src/main/resources/
    ├── device-8090.json                ← TLV 输入设备-1 配置（1个输入通道 HDMI-1）
    └── device-8092.json                ← TLV 输入设备-2 配置（2个输入通道 HDMI-1, HDMI-2）

└── src/main/java/com/example/demo/simulator/tlv/input/
    ├── server/
    │   ├── TlvServer.java              ← UDP 服务端（监听端口，请求分发）
    │   └── DiscoveryListener.java      ← UDP 设备发现监听
    ├── handler/
    │   ├── GetInfoHandler.java         ← 查询设备信息
    │   ├── GetStatusHandler.java       ← 查询设备状态
    │   ├── CreateWindowHandler.java    ← 创建窗口
    │   └── CloseWindowHandler.java     ← 关闭窗口
    └── core/
        ├── SimDeviceManager.java       ← 设备管理核心（窗口存内存）
        └── DeviceConfig.java           ← 设备配置加载（从 JSON 文件读取）
```

**demo1-simulator-tlv-output**（输出设备模块）

```
demo1-simulator-tlv-output/
└── src/main/resources/
    ├── device-8091.json                ← TLV 输出设备-1 配置（2个输出通道，maxWindows=4）
    └── device-8093.json                ← TLV 输出设备-2 配置（3个输出通道，maxWindows=6）

└── src/main/java/com/example/demo/simulator/tlv/output/
    ├── server/
    │   ├── TlvServer.java              ← UDP 服务端（监听端口，请求分发）
    │   └── DiscoveryListener.java      ← UDP 设备发现监听
    ├── handler/
    │   ├── GetInfoHandler.java         ← 查询设备信息
    │   └── GetStatusHandler.java       ← 查询设备状态
    └── core/
        ├── SimDeviceManager.java       ← 设备管理核心
        └── DeviceConfig.java           ← 设备配置加载（从 JSON 文件读取）
```

> 添加设备只需新增对应的 JSON 配置文件，启动新进程即可。

**demo1-server（管控侧，V4 新增）**

```
demo1-server/src/main/java/com/example/demo/driver/
    ├── DeviceDriver.java          (已有)
    ├── RestDeviceDriver.java      (已有)
    └── TlvDeviceDriver.java       (V4 新增 - UDP + TLV 驱动)
```

### 2.2 依赖关系

```text
管控系统（demo1-server）
    DeviceDriver (接口)
        ├── RestDeviceDriver   → HTTP + JSON
        └── TlvDeviceDriver    → UDP + TLV
                ├── TlvEncoder / TlvDecoder  → demo1-common/codec/
                └── UDP Socket → TLV 模拟设备

TLV 模拟设备（demo1-simulator-tlv-input / demo1-simulator-tlv-output）
    TlvServer (UDP 监听)
        ├── TlvEncoder / TlvDecoder  → demo1-common/codec/
        ├── GetInfoHandler
        ├── GetStatusHandler
        ├── CreateWindowHandler（仅输入设备）
        ├── CloseWindowHandler（仅输入设备）
        └── SimDeviceManager
                └── DeviceConfig → JSON 配置文件
```

---

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
    "udpPort": 8090,
    "discoveryPort": 9996
  }
}
```

**输出设备配置**（`device-8091.json`）：

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
    "udpPort": 8091,
    "discoveryPort": 9995
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
| udpPort | int | UDP 通信端口 |
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

### 4.5 TlvFrame — TLV 帧结构

位于 `demo1-common` 的 `codec/` 包下，V4 新增。

```java
public class TlvFrame {
    private int type;        // 命令类型（2 字节，大端序）
    private byte[] value;    // 载荷数据（JSON 的 UTF-8 字节）
}
```

### 4.6 TlvCommand — 命令类型常量

位于 `demo1-common` 的 `codec/` 包下，V4 新增。

```java
public class TlvCommand {
    public static final int CMD_GET_INFO       = 0x0001;
    public static final int CMD_GET_STATUS     = 0x0002;
    public static final int CMD_GET_CAPABILITY = 0x0003;
    public static final int CMD_CREATE_WINDOW  = 0x0010;
    public static final int CMD_CLOSE_WINDOW   = 0x0012;
    public static final int RESP_INFO          = 0x8001;
    public static final int RESP_STATUS        = 0x8002;
    public static final int RESP_CAPABILITY    = 0x8003;
    public static final int RESP_WINDOW        = 0x8010;
    public static final int RESP_ERROR         = 0xFFFF;
}
```

---

## 5. 接口

TLV 模拟设备通过 UDP 通信，默认端口 **8090**（输入）/ **8091**（输出）。管控系统通过 `DeviceEndpoint` 指定 `deviceType = "TLV"` 和 `baseUrl = "udp://ip:port"`。

> **注意：** 输出设备不支持窗口操作（CMD_CREATE_WINDOW、CMD_CLOSE_WINDOW），仅支持查询类命令（CMD_GET_INFO、CMD_GET_STATUS、CMD_GET_CAPABILITY）。

### 5.1 接口汇总

| 命令 | Type | 方向 | 用途 | 说明 |
|------|------|------|------|------|
| CMD_GET_INFO | 0x0001 | 管控→设备 | 查询设备信息 | Value 为空 |
| CMD_GET_STATUS | 0x0002 | 管控→设备 | 查询设备状态 | Value 为空 |
| CMD_GET_CAPABILITY | 0x0003 | 管控→设备 | 查询设备能力 | Value 为空 |
| CMD_CREATE_WINDOW | 0x0010 | 管控→设备 | 创建窗口 | Value 为 SimWindow JSON |
| CMD_CLOSE_WINDOW | 0x0012 | 管控→设备 | 关闭窗口 | Value 为 `{"windowId":"xxx"}` |
| RESP_INFO | 0x8001 | 设备→管控 | 设备信息响应 | Value 为 Result\<SimDeviceInfo\> JSON |
| RESP_STATUS | 0x8002 | 设备→管控 | 设备状态响应 | Value 为 Result\<SimDeviceStatus\> JSON |
| RESP_CAPABILITY | 0x8003 | 设备→管控 | 设备能力响应 | Value 为 Result\<SimDeviceCapability\> JSON |
| RESP_WINDOW | 0x8010 | 设备→管控 | 窗口操作响应 | Value 为 Result\<SimWindow\> JSON |
| RESP_ERROR | 0xFFFF | 设备→管控 | 错误响应 | Value 为 Result JSON（code=0） |

### 5.2 接口详情

#### 5.2.1 查询设备信息

**请求：** Type = `0x0001`，Value = 空（Length = 0）

**响应：** Type = `0x8001`

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "deviceName": "TLV输入设备-1",
    "deviceType": "TLV",
    "deviceCategory": "INPUT",
    "model": "DS-TLV2048-A",
    "serialNumber": "SN-TLV-2024-0001",
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "outputChannel1": "",
    "outputChannel2": "",
    "maxResolution": "1920x1080"
  }
}
```

#### 5.2.2 查询设备状态

**请求：** Type = `0x0002`，Value = 空（Length = 0）

**响应：** Type = `0x8002`

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "online": true,
    "windowCount": 0,
    "uptime": "2026-08-12 10:00:00"
  }
}
```

#### 5.2.3 查询设备能力

**请求：** Type = `0x0003`，Value = 空（Length = 0）

**响应：** Type = `0x8003`

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "maxWindows": 4,
    "supportMove": false,
    "supportResize": false,
    "supportOverlay": false,
    "maxResolution": "1920x1080",
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "outputChannel1": "",
    "outputChannel2": ""
  }
}
```

#### 5.2.4 创建窗口

**请求：** Type = `0x0010`

```json
{
  "windowId": "win-001",
  "channelName": "HDMI-1",
  "x": 0,
  "y": 0,
  "width": 960,
  "height": 540
}
```

**成功响应：** Type = `0x8010`

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "windowId": "win-001",
    "channelName": "HDMI-1",
    "x": 0,
    "y": 0,
    "width": 960,
    "height": 540,
    "sourceType": "HDMI",
    "sourceUrl": "",
    "createTime": "2026-08-12 14:30:00"
  }
}
```

**失败响应（窗口 ID 重复）：** Type = `0xFFFF`

```json
{ "code": 0, "msg": "窗口已存在: win-001", "data": null }
```

**失败响应（通道名无效）：** Type = `0xFFFF`

```json
{ "code": 0, "msg": "通道名无效: OUT-99", "data": null }
```

#### 5.2.5 关闭窗口

**请求：** Type = `0x0012`

```json
{ "windowId": "win-001" }
```

**成功响应：** Type = `0x8010`

```json
{ "code": 1, "msg": null, "data": null }
```

**失败响应（窗口不存在）：** Type = `0xFFFF`

```json
{ "code": 0, "msg": "窗口不存在: win-999", "data": null }
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
            └── 6. 解析 value 中的 JSON → Result<SimDeviceInfo>
```

### 6.2 窗口创建与关闭流程

```text
管控系统 → CMD_CREATE_WINDOW (0x0010)

  ├── TlvDeviceDriver 构造 TlvFrame，JSON 为 {windowId, channelName, x, y, width, height}
  ├── TlvEncoder.encode() → UDP 发送
  │
  └── TLV 模拟设备 TlvServer 收到
        ├── TlvDecoder.decode() → 解析 JSON
        ├── 校验 channelName 是否为有效通道
        │     └── 无效 → RESP_ERROR "通道名无效"
        ├── 校验 windowId 是否重复
        │     └── 重复 → RESP_ERROR "窗口已存在"
        ├── 校验窗口总数是否超过 maxWindows
        │     └── 超限 → RESP_ERROR "窗口数量已达上限"
        └── 校验通过 → 存入 ConcurrentHashMap → RESP_WINDOW

管控系统 → CMD_CLOSE_WINDOW (0x0012)

  └── TLV 模拟设备 TlvServer 收到
        ├── 校验 windowId 是否存在
        │     └── 不存在 → RESP_ERROR "窗口不存在"
        └── 存在 → 从 ConcurrentHashMap 移除 → RESP_WINDOW
```

### 6.3 设备发现流程

```text
管控系统启动
  │
  ├── UDP 广播:255.255.255.255:9996 → TLV 输入设备-1 (:8090)
  └── UDP 广播:255.255.255.255:9995 → TLV 输出设备-1 (:8091)

各 TLV 模拟设备 DiscoveryListener 收到 {"action":"discovery"}
  └── 单播回复 {"deviceType":"TLV","baseUrl":"udp://192.168.1.100:8090"}

管控系统 3 秒超时收集所有回复
  └── 汇总返回给前端，用户选择添加设备
        └── POST /device → 管控系统通过 TlvDeviceDriver 拉取设备详细信息
```

---

## 7. 补充

### 7.1 默认设备

系统提供 4 台 TLV 模拟设备，通过命令行参数启动不同进程。每台设备进程启动后自动初始化，管控系统启动后即可直接查询。

| 模块 | 端口 | UDP端口 | UDP发现 | 类别 | 通道 | maxWindows |
|------|:--:|:--:|:--:|:--:|------|:--:|
| demo1-simulator-tlv-input | 8090 | 8090 | 9996 | INPUT | 1个输入：HDMI-1 | — |
| demo1-simulator-tlv-input | 8092 | 8092 | 9994 | INPUT | 2个输入：HDMI-1, HDMI-2 | — |
| demo1-simulator-tlv-output | 8091 | 8091 | 9995 | OUTPUT | 2个输出：OUT-1, OUT-2 | 4 |
| demo1-simulator-tlv-output | 8093 | 8093 | 9993 | OUTPUT | 3个输出：OUT-1, OUT-2, OUT-3 | 6 |

### 7.2 存储方式

| 数据类型 | 存储方式 | 说明 |
|------|------|------|
| 设备基本信息 | JSON 配置文件 | 启动时一次性加载到内存 |
| 设备能力 | JSON 配置文件 | 启动时一次性加载到内存 |
| 窗口数据 | 内存 `ConcurrentHashMap` | 不持久化，进程重启后丢失，由管控系统重新推送 |

**配置文件：** 每台模拟设备使用独立的 JSON 配置文件，以设备端口命名。

| 模拟设备 | 配置文件 |
|---------|-----------|
| TLV 输入设备-1（端口 8090） | `device-8090.json` |
| TLV 输入设备-2（端口 8092） | `device-8092.json` |
| TLV 输出设备-1（端口 8091） | `device-8091.json` |
| TLV 输出设备-2（端口 8093） | `device-8093.json` |

### 7.3 多设备模拟

每个模块通过命令行参数指定配置文件启动多个进程，**一个进程 = 一台设备**。添加设备只需新增 JSON 配置文件并启动新进程：

```bash
# TLV 输入设备模块：启动 2 个进程
java -jar demo1-simulator-tlv-input.jar --config device-8090.json  # TLV 输入设备-1
java -jar demo1-simulator-tlv-input.jar --config device-8092.json  # TLV 输入设备-2

# TLV 输出设备模块：启动 2 个进程
java -jar demo1-simulator-tlv-output.jar --config device-8091.json  # TLV 输出设备-1
java -jar demo1-simulator-tlv-output.jar --config device-8093.json  # TLV 输出设备-2
```

### 7.4 TLV 协议帧格式

```
+---------+---------+----------+
|  Type   | Length  |  Value   |
| 2 bytes | 2 bytes | N bytes  |
+---------+---------+----------+
```

| 字段 | 字节数 | 说明 |
|------|--------|------|
| Type | 2 | 命令类型（大端序） |
| Length | 2 | Value 部分的字节长度（大端序） |
| Value | N | 载荷数据，JSON 字符串的 UTF-8 字节 |

> V4 第一版不引入 CRC 校验，协议保持简单。Value 部分使用 JSON 字符串而非自定义二进制格式，原因：与 `demo1-common` 数据模型对齐、易于调试、后续升级无需修改编解码器。

### 7.5 编解码器设计

位于 `demo1-common/src/main/java/com/example/demo/codec/`。

**TlvEncoder.java**

```java
public class TlvEncoder {
    public static byte[] encode(TlvFrame frame) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write((frame.getType() >> 8) & 0xFF);
        bos.write(frame.getType() & 0xFF);
        int len = frame.getValue().length;
        bos.write((len >> 8) & 0xFF);
        bos.write(len & 0xFF);
        bos.write(frame.getValue());
        return bos.toByteArray();
    }
}
```

**TlvDecoder.java**

```java
public class TlvDecoder {
    public static TlvFrame decode(byte[] data) {
        int type = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        int length = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        byte[] value = new byte[length];
        System.arraycopy(data, 4, value, 0, length);
        return new TlvFrame(type, value);
    }
}
```

> **使用方：** 管控侧 `TlvDeviceDriver` 调 `TlvEncoder.encode()` 编请求包 / `TlvDecoder.decode()` 解响应包；模拟器侧 `TlvServer` 调 `TlvDecoder.decode()` 解请求包 / `TlvEncoder.encode()` 编响应包。

### 7.6 超时与重试

| 参数 | 值 | 说明 |
|------|-----|------|
| 发送超时 | 3 秒 | 单次 UDP 请求-响应超时 |
| 重试次数 | 2 次 | 超时后重试，共 3 次机会 |
| 重试间隔 | 无 | 立即重试 |

> UDP 丢包是正常现象，2 次重试可覆盖大部分网络抖动场景。管控系统已有的 `retryPendingWindows` 机制会兜底处理最终失败的情况。

### 7.7 统一返回格式

响应 Value 统一使用 `Result<T>` 封装：

```json
{
  "code": 1,
  "msg": null,
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | `1`=成功，`0`=失败 |
| msg | String | 失败时包含错误信息，成功时为 `null` |
| data | T | 业务数据，类型视命令而定 |

### 7.8 与管控系统的关系

管控系统通过 `DeviceDriver` 统一接口调用两类设备，业务代码不感知设备类型差异。TLV 模拟设备与管控系统是**两个独立进程**，各自启动：

| 模块 | 端口 | 说明 |
|------|------|------|
| demo1-server（管控系统） | 8085 | 通过 `mvn spring-boot:run` 启动 |
| TLV 输入设备-1（端口 8090） | 8090 | 1个输入通道，创建/关闭/查询窗口 |
| TLV 输入设备-2（端口 8092） | 8092 | 2个输入通道，创建/关闭/查询窗口 |
| TLV 输出设备-1（端口 8091） | 8091 | 2个输出通道，仅查询接口 |
| TLV 输出设备-2（端口 8093） | 8093 | 3个输出通道，仅查询接口 |



```text
DeviceDriver (接口)
    ├── RestDeviceDriver   ← HTTP + JSON
    └── TlvDeviceDriver    ← UDP + TLV（V4 新增）
            │
            ├── 依赖 demo1-common/codec/TlvFrame      ← TLV 帧结构
            ├── 依赖 demo1-common/codec/TlvCommand    ← 命令类型常量
            ├── 依赖 demo1-common/codec/TlvEncoder    ← 编码：对象 → byte[]
            └── 依赖 demo1-common/codec/TlvDecoder    ← 解码：byte[] → 对象
```

