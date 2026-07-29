# REST 模拟设备接口文档

## 1. 概述

REST 模拟设备是异构硬件设备管控系统中的**独立 Spring Boot 程序**，每个进程模拟**一台**分布式节点设备，通过 HTTP + JSON 提供设备信息和状态查询接口。

模拟设备与管控系统分离部署，各自独立启动，通过 HTTP 通信，模拟真实分布式场景。

**核心设计：一个端口 = 一台设备。** 如需模拟多台设备，启动多个进程并绑定不同端口即可。

## 2. 项目结构

模拟设备是独立的 Spring Boot 程序，位于 `demo1-simulator` 模块，数据模型与管控系统共享 `demo1-common`。

```
demo1-simulator/                        ← 模拟设备（默认端口 8086，每进程一台设备）
└── src/main/java/com/example/demo/simulator/
    ├── controller/
    │   └── SimDeviceController.java    ← REST 接口层
    ├── core/
    │   └── SimDeviceManager.java       ← 设备管理核心（内存存储，单设备）
    └── server/
        └── DiscoveryListener.java      ← UDP 设备发现监听

demo1-common/                           ← 共享数据模型
└── src/main/java/com/example/demo/model/
    ├── SimDeviceInfo.java
    ├── SimDeviceStatus.java
    ├── SimWindow.java
    └── SimDeviceCapability.java
```

> 管控系统（demo1-server）通过 `DeviceDriver` 接口调用模拟设备，具体看设备管理模块设计文档。

## 3. 数据模型

### 3.1 SimDeviceInfo — 设备基本信息

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceName | String | 设备名称，如 `REST-Node-01` |
| deviceType | String | 设备类型，当前为 `REST` |
| model | String | 设备型号，如 `DS-D2055NH-A` |
| serialNumber | String | 序列号 |
| outputChannels | int | 输出通道数 |
| maxResolution | String | 最大分辨率，如 `1920x1080` |

### 3.2 SimDeviceStatus — 设备运行状态

| 字段 | 类型 | 说明 |
|------|------|----|
| online | boolean | 是否在线，当前始终为 `true` |
| windowCount | int | 窗口数 |
| uptime | String | 设备启动时间，格式 `yyyy-MM-dd HH:mm:ss` |

### 3.3 SimWindow — 窗口信息

窗口是管控系统下发到模拟设备的内容展示单元，每个窗口绑定到设备的某个输出通道。

| 字段 | 类型 | 必填 | 不可重复 | 说明 |
|------|------|:--:|:----:|------|
| windowId | String | 是 | 是 | 窗口唯一标识，由管控系统生成，全局唯一 |
| channel | int | 是 | 是 | 绑定的输出通道编号，从 1 开始，同一设备下不可重复 |
| x | int | 否 | 否 | 窗口左上角 X 坐标，默认 0 |
| y | int | 否 | 否 | 窗口左上角 Y 坐标，默认 0 |
| width | int | 否 | 否 | 窗口宽度（像素），默认 1920 |
| height | int | 否 | 否 | 窗口高度（像素），默认 1080 |
| sourceType | String | 否 | 否 | 信号源类型，如 `HDMI`、`VGA`、`Stream` |
| sourceUrl | String | 否 | 否 | 信号源地址，如流媒体 URL，默认 `""` |
| createTime | String | 否 | 否 | 窗口创建时间，格式 `yyyy-MM-dd HH:mm:ss`，自动生成 |

### 3.4 SimDeviceCapability — 设备能力

设备能力描述设备的功能限制，控制窗口创建时的校验规则。能力可以在运行时动态变更，用于模拟设备能力变化场景。

| 字段 | 类型 | 说明 |
|------|------|------|
| maxWindows | int | 最大窗口数量，超出后拒绝创建 |
| supportMove | boolean | 是否支持窗口移动 |
| supportResize | boolean | 是否支持窗口缩放 |
| supportOverlay | boolean | 是否支持窗口叠加 |
| maxResolution | String | 最大分辨率，如 `1920x1080` |
| outputChannels | int | 输出通道数 |

## 4. 接口列表

模拟设备独立运行，默认端口 **8086**，基路径：`http://localhost:8086/simulator`。不同设备进程使用不同端口，接口路径结构完全一致，仅端口号不同。

### 4.1 获取设备信息

```
GET /simulator/device/info
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/info
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "deviceName": "REST-Node-01",
    "deviceType": "REST",
    "model": "DS-D2055NH-A",
    "serialNumber": "SN-REST-2024-0001",
    "outputChannels": 2,
    "maxResolution": "1920x1080"
  }
}
```

### 4.2 获取设备状态

```
GET /simulator/device/status
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/status
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "online": true,
    "windowCount": 0,
    "uptime": "2026-07-23 16:40:00"
  }
}
```

### 4.3 获取设备能力

查询设备当前的能力限制，用于管控系统校验窗口操作是否合法。

```
GET /simulator/device/capability
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/capability
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "maxWindows": 4,
    "supportMove": true,
    "supportResize": true,
    "supportOverlay": true,
    "maxResolution": "1920x1080",
    "outputChannels": 2
  }
}
```

### 4.4 更新设备能力（模拟能力变化）

动态修改设备能力，用于模拟设备能力变化场景（如运行时热插拔、固件升级等）。

```
PUT /simulator/device/capability
Content-Type: application/json
```

**请求体：**

```json
{
  "maxWindows": 2,
  "supportMove": false,
  "supportResize": true,
  "supportOverlay": false,
  "maxResolution": "1920x1080",
  "outputChannels": 2
}
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "maxWindows": 2,
    "supportMove": false,
    "supportResize": true,
    "supportOverlay": false,
    "maxResolution": "1920x1080",
    "outputChannels": 2
  }
}
```

### 4.5 创建窗口

向设备下发创建窗口命令。创建前会校验：窗口总数是否超过 `maxWindows` 限制。

```
POST /simulator/device/window
Content-Type: application/json
```

**请求体：**

```json
{
  "windowId": "win-001",
  "channel": 1,
  "x": 0,
  "y": 0,
  "width": 960,
  "height": 540,
  "sourceType": "Stream",
  "sourceUrl": "rtsp://example.com/stream1"
}
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "windowId": "win-001",
    "channel": 1,
    "x": 0,
    "y": 0,
    "width": 960,
    "height": 540,
    "sourceType": "Stream",
    "sourceUrl": "rtsp://example.com/stream1",
    "createTime": "2026-07-28 14:30:00"
  }
}
```

**失败响应（超过最大窗口数）：**

```json
{
  "code": 0,
  "msg": "窗口数量已达上限: 4",
  "data": null
}
```

**失败响应（窗口 ID 重复）：**

```json
{
  "code": 0,
  "msg": "窗口已存在: win-001",
  "data": null
}
```

### 4.6 关闭窗口

关闭设备上指定 ID 的窗口，释放通道资源。

```
DELETE /simulator/device/window/{windowId}
```

**请求示例：**

```
DELETE http://localhost:8086/simulator/device/window/win-001
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": null
}
```

**失败响应（窗口不存在）：**

```json
{
  "code": 0,
  "msg": "窗口不存在: win-999",
  "data": null
}
```

### 4.7 查询窗口列表（状态回读）

返回设备上当前所有窗口的列表，用于管控系统刷新页面后从模拟设备重新读取窗口状态。

```
GET /simulator/device/windows
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/windows
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": [
    {
      "windowId": "win-001",
      "channel": 1,
      "x": 0,
      "y": 0,
      "width": 960,
      "height": 540,
      "sourceType": "Stream",
      "sourceUrl": "rtsp://example.com/stream1",
      "createTime": "2026-07-28 14:30:00"
    },
    {
      "windowId": "win-002",
      "channel": 2,
      "x": 960,
      "y": 0,
      "width": 960,
      "height": 540,
      "sourceType": "HDMI",
      "sourceUrl": "",
      "createTime": "2026-07-28 14:31:00"
    }
  ]
}
```

### 4.8 查询单个窗口

查询指定 ID 的窗口详细信息。

```
GET /simulator/device/window/{windowId}
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/window/win-001
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "windowId": "win-001",
    "channel": 1,
    "x": 0,
    "y": 0,
    "width": 960,
    "height": 540,
    "sourceType": "Stream",
    "sourceUrl": "rtsp://example.com/stream1",
    "createTime": "2026-07-28 14:30:00"
  }
}
```

### 4.9 更新窗口位置/大小

移动窗口位置或调整窗口大小。校验 `supportMove` 和 `supportResize` 能力限制。

```
PUT /simulator/device/window/{windowId}
Content-Type: application/json
```

**请求体（移动窗口）：**

```json
{
  "x": 100,
  "y": 200
}
```

**请求体（调整大小）：**

```json
{
  "width": 800,
  "height": 600
}
```

**请求体（同时移动+缩放）：**

```json
{
  "x": 100,
  "y": 200,
  "width": 800,
  "height": 600
}
```

> 请求体只需包含要更新的字段，未传字段保持不变。

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "windowId": "win-001",
    "channel": 1,
    "x": 100,
    "y": 200,
    "width": 800,
    "height": 600,
    "sourceType": "Stream",
    "sourceUrl": "rtsp://example.com/stream1",
    "createTime": "2026-07-28 14:30:00"
  }
}
```

**失败响应（窗口不存在）：**

```json
{
  "code": 0,
  "msg": "窗口不存在: win-999",
  "data": null
}
```

**失败响应（不支持移动）：**

```json
{
  "code": 0,
  "msg": "设备不支持窗口移动",
  "data": null
}
```

**失败响应（不支持缩放）：**

```json
{
  "code": 0,
  "msg": "设备不支持窗口缩放",
  "data": null
}
```

---

## 5. 设备发现协议（UDP）

模拟设备除了提供 HTTP 接口外，还通过 UDP 协议支持设备自动发现。管控系统发送 UDP 广播搜索设备，每台模拟设备收到后回复自身信息。

### 5.1 协议参数

| 参数 | 值 |
|------|-----|
| 传输协议 | UDP |
| 监听端口 | 9999 |
| 广播地址 | 255.255.255.255 |
| 序列化格式 | JSON |

### 5.2 请求格式

管控系统向 `255.255.255.255:9999` 发送 UDP 广播：

```json
{"action": "discovery"}
```

### 5.3 响应格式

每台模拟设备收到广播后，单播回复自身地址：

```json
{
  "baseUrl": "http://192.168.1.100:8086"
}
```

| 字段 | 说明       |
|------|----------|
| baseUrl | 模拟设备进程的 HTTP 地址（含实际端口），管控系统可直接用于后续 HTTP 请求 |

> 管控系统在 3 秒超时内会收到多台模拟设备的回复，每个回复代表一台独立设备（不同端口），汇总后展示给用户。管控系统拿到 `baseUrl` 后，走手动添加流程（`POST /device`），通过 HTTP 拉取设备详细信息。

### 5.4 交互时序

```
管控系统 (UDP 客户端)              模拟设备-A (:8086)        模拟设备-B (:8087)
       │                                      │                      │
       │── UDP 广播:255.255.255.255:9999 ──→  │                      │
       │   {"action":"discovery"}             │                      │
       │                                      │── 解析请求            │
       │←── UDP 单播回复 ──────────────────│                      │
       │   {"baseUrl":"http://...:8086"}      │                      │
       │                                      │                      │── 解析请求
       │←── UDP 单播回复 ───────────────────────────────────────│
       │   {"baseUrl":"http://...:8087"}      │                      │
       │                                      │                      │
       │  3 秒超时，收集所有回复，汇总返回给前端                          │
```

### 5.5 实现说明

`DiscoveryListener` 在模拟设备启动时通过 `@PostConstruct` 自动开启守护线程，监听 UDP 9999 端口。收到 `{"action":"discovery"}` 时，构造仅含 `baseUrl` 的 JSON 回复并原路返回。

纯 JDK 实现，无需引入额外依赖（`java.net.DatagramSocket` + `DatagramPacket`）。

---

## 6. 默认设备

模拟设备进程启动后，自动初始化 **1 台**默认设备。管控系统启动后即可直接查询，无需手动创建。

### 6.1 设备信息（SimDeviceInfo）

| 字段 | 值 |
|------|-----|
| deviceName | REST-Node-01 |
| deviceType | REST |
| model | DS-D2055NH-A |
| serialNumber | SN-REST-2024-0001 |
| outputChannels | 2 |
| maxResolution | 1920x1080 |

### 6.2 设备状态（SimDeviceStatus）

| 字段 | 值 |
|------|-----|
| online | true |
| windowCount | 0 |
| uptime | 进程启动时间，格式 `yyyy-MM-dd HH:mm:ss` |

### 6.3 设备能力（SimDeviceCapability）

| 字段 | 值 |
|------|-----|
| maxWindows | 4 |
| supportMove | true |
| supportResize | true |
| supportOverlay | true |
| maxResolution | 1920x1080 |
| outputChannels | 2 |

> 默认设备启动时窗口为空（`windowCount=0`），窗口由管控系统通过 `POST /simulator/device/window` 创建后动态管理。

---

## 7. 设计说明

### 7.1 存储方式

设备数据全部存储在内存中，进程重启后清空并重新初始化默认设备，能力恢复为默认值，窗口清空。

> **窗口恢复：** 模拟设备自身不持久化窗口数据，但管控系统（demo1-server）将窗口状态保存在数据库中。管控系统重启后可从数据库读取窗口列表，逐个调用 `POST /simulator/device/window` 重新下发到模拟设备，恢复窗口状态。

### 7.2 与管控系统的关系

模拟设备与管控系统是**两个独立进程**，各自启动：

| 模块 | 端口 | 说明 |
|------|------|------|
| demo1-server（管控系统） | 8085 | 通过 `mvn spring-boot:run` 启动 |
| demo1-simulator（模拟设备） | 8086（可自定义） | 每台设备一个进程，端口不同 |

管控系统通过 HTTP 请求调用本接口文档中的 API，模拟设备离线时 HTTP 请求失败，管控系统即可检测到设备下线。

管控系统内部通过 `DeviceDriver` 接口封装 HTTP 调用，`RestDeviceDriver` 为 REST 设备的实现，具体看设备管理模块设计文档。

**设备定位：** 管控系统通过 `baseUrl` 来定位一台设备。`baseUrl` 指向模拟设备进程地址（如 `http://localhost:8086`），每个进程只有一台设备，`baseUrl` 即设备唯一标识。

### 7.3 多设备模拟

**一个进程 = 一台设备**，如需模拟多台设备，启动多个进程并绑定不同端口：

```bash
# 设备1：端口 8086
java -jar demo1-simulator.jar --server.port=8086

# 设备2：端口 8087
java -jar demo1-simulator.jar --server.port=8087

# 设备3：端口 8088
java -jar demo1-simulator.jar --server.port=8088
```

管控系统分别向 `http://localhost:8086`、`http://localhost:8087`、`http://localhost:8088` 添加设备，每个地址对应一台独立设备。

| 端口 | 说明 |
|------|------|
| 8086 | 进程 1，独立设备 |
| 8087 | 进程 2，独立设备 |
| 8088 | 进程 3，独立设备 |

> 每个进程内的设备信息完全独立，管控系统通过 `baseUrl` 区分不同设备。

### 7.4 统一返回格式

接口返回统一使用 `Result<T>` 封装：

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
| data | T | 业务数据，类型视接口而定 |