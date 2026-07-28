# REST 模拟设备接口文档

## 1. 概述

REST 模拟设备是异构硬件设备管控系统中的**独立 Spring Boot 程序**，用于模拟分布式节点设备，通过 HTTP + JSON 提供设备信息和状态查询接口。

模拟设备与管控系统分离部署，各自独立启动，通过 HTTP 通信，模拟真实分布式场景。

## 2. 项目结构

模拟设备是独立的 Spring Boot 程序，位于 `demo1-simulator` 模块，数据模型与管控系统共享 `demo1-common`。

```
demo1-simulator/                        ← 模拟设备（端口 8086）
└── src/main/java/com/example/demo/simulator/
    ├── controller/
    │   └── SimDeviceController.java    ← REST 接口层
    ├── core/
    │   └── SimDeviceManager.java       ← 设备管理核心（内存存储）
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
| deviceId | String | 设备唯一标识，如 `device-001` |
| deviceName | String | 设备名称，如 `REST-Node-01` |
| deviceType | String | 设备类型，当前为 `REST` |
| model | String | 设备型号，如 `DS-D2055NH-A` |
| serialNumber | String | 序列号 |
| outputChannels | int | 输出通道数 |
| maxResolution | String | 最大分辨率，如 `1920x1080` |

### 3.2 SimDeviceStatus — 设备运行状态

| 字段 | 类型 | 说明 |
|------|------|----|
| deviceId | String | 设备唯一标识 |
| online | boolean | 是否在线，当前始终为 `true` |
| windowCount | int | 窗口数|
| uptime | String | 设备启动时间，格式 `yyyy-MM-dd HH:mm:ss` |

### 3.3 SimWindow — 窗口信息

窗口是管控系统下发到模拟设备的内容展示单元，每个窗口绑定到设备的某个输出通道。

| 字段 | 类型 | 必填 | 不可重复 | 说明 |
|------|------|:--:|:----:|------|
| windowId | String | 是  |  是   | 窗口唯一标识，由管控系统生成，全局唯一 |
| channel | int | 是  |  是   | 绑定的输出通道编号，从 1 开始，同一设备下不可重复 |
| x | int | 否  |  否   | 窗口左上角 X 坐标，默认 0 |
| y | int | 否  |  否   | 窗口左上角 Y 坐标，默认 0 |
| width | int | 否  |  否   | 窗口宽度（像素），默认 1920 |
| height | int | 否  |  否   | 窗口高度（像素），默认 1080 |
| sourceType | String | 否  |  否   | 信号源类型，如 `HDMI`、`VGA`、`Stream` |
| sourceUrl | String | 否  |  否   | 信号源地址，如流媒体 URL，默认 `""` |
| createTime | String | 否  |  否   | 窗口创建时间，格式 `yyyy-MM-dd HH:mm:ss`，自动生成 |

### 3.4 SimDeviceCapability — 设备能力

设备能力描述设备的功能限制，控制窗口创建时的校验规则。能力可以在运行时动态变更，用于模拟设备能力变化场景。

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | String | 所属设备唯一标识 |
| maxWindows | int | 最大窗口数量，超出后拒绝创建 |
| supportMove | boolean | 是否支持窗口移动 |
| supportResize | boolean | 是否支持窗口缩放 |
| supportOverlay | boolean | 是否支持窗口叠加 |
| maxResolution | String | 最大分辨率，如 `1920x1080` |
| outputChannels | int | 输出通道数 |

## 4. 接口列表

模拟设备独立运行在端口 **8086**，基路径：`http://localhost:8086/simulator`

### 4.1 获取设备信息

```
GET /simulator/device/{deviceId}/info
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/device-001/info
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "deviceId": "device-001",
    "deviceName": "REST-Node-01",
    "deviceType": "REST",
    "model": "DS-D2055NH-A",
    "serialNumber": "SN-REST-2024-0001",
    "outputChannels": 2,
    "maxResolution": "1920x1080"
  }
}
```

**失败响应（设备不存在）：**

```json
{
  "code": 0,
  "msg": "设备不存在: device-999",
  "data": null
}
```

### 4.2 获取设备状态

```
GET /simulator/device/{deviceId}/status
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/device-001/status
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "deviceId": "device-001",
    "online": true,
    "windowCount": 0,
    "uptime": "2026-07-23 16:40:00"
  }
}
```

**失败响应（设备不存在）：**

```json
{
  "code": 0,
  "msg": "设备不存在: device-999",
  "data": null
}
```

### 4.3 获取设备能力

查询设备当前的能力限制，用于管控系统校验窗口操作是否合法。

```
GET /simulator/device/{deviceId}/capability
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/device-001/capability
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "deviceId": "device-001",
    "maxWindows": 4,
    "supportMove": true,
    "supportResize": true,
    "supportOverlay": false,
    "maxResolution": "1920x1080",
    "outputChannels": 2
  }
}
```

### 4.4 更新设备能力（模拟能力变化）

动态修改设备能力，用于模拟设备能力变化场景（如运行时热插拔、固件升级等）。

```
PUT /simulator/device/{deviceId}/capability
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
  "msg": "能力已更新",
  "data": {
    "deviceId": "device-001",
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

向设备下发创建窗口命令。创建前会校验：设备是否存在、窗口总数是否超过 `maxWindows` 限制。

```
POST /simulator/device/{deviceId}/window
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
  "msg": "窗口创建成功",
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
DELETE /simulator/device/{deviceId}/window/{windowId}
```

**请求示例：**

```
DELETE http://localhost:8086/simulator/device/device-001/window/win-001
```

**成功响应：**

```json
{
  "code": 1,
  "msg": "窗口已关闭",
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
GET /simulator/device/{deviceId}/windows
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/device-001/windows
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
GET /simulator/device/{deviceId}/window/{windowId}
```

**请求示例：**

```
GET http://localhost:8086/simulator/device/device-001/window/win-001
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
PUT /simulator/device/{deviceId}/window/{windowId}
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
  "msg": "窗口已更新",
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

模拟设备除了提供 HTTP 接口外，还通过 UDP 协议支持设备自动发现。管控系统发送 UDP 广播搜索设备，模拟设备收到后回复自身信息。

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

模拟设备收到广播后，单播回复：

```json
{
  "baseUrl": "http://192.168.1.100:8086",
  "devices": [
    {
      "deviceId": "device-001",
      "deviceName": "REST-Node-01",
      "deviceType": "REST",
      "model": "DS-D2055NH-A",
      "outputChannels": 2,
      "maxResolution": "1920x1080"
    },
    {
      "deviceId": "device-002",
      "deviceName": "REST-Node-02",
      "deviceType": "REST",
      "model": "DS-D2055NH-B",
      "outputChannels": 2,
      "maxResolution": "1920x1080"
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| baseUrl | 模拟设备进程的 HTTP 地址，管控系统可直接用于后续 HTTP 请求 |
| devices | 该进程内所有设备的基本信息列表 |

### 5.4 交互时序

```
管控系统 (UDP 客户端)              模拟设备 (UDP 监听 :9999)
       │                                      │
       │── UDP 广播 ──────────────────────→  │
       │   {"action":"discovery"}             │
       │                                      │── 解析请求
       │                                      │── 从 SimDeviceManager 获取设备列表
       │                                      │── 构造 JSON 回复
       │←── UDP 单播回复 ──────────────────│
       │   {"baseUrl":"...","devices":[...]}  │
       │                                      │
       │  3 秒超时，收集所有回复                │
```

### 5.5 实现说明

`DiscoveryListener` 在模拟设备启动时通过 `@PostConstruct` 自动开启守护线程，监听 UDP 9999 端口。收到 `{"action":"discovery"}` 时，从 `SimDeviceManager` 获取所有设备信息，构造 JSON 回复并原路返回。

纯 JDK 实现，无需引入额外依赖（`java.net.DatagramSocket` + `DatagramPacket`）。

---

## 6. 默认设备列表

模拟设备进程启动后，自动暴露 2 台模拟硬件资源。每台设备代表一个独立的分布式节点，管控系统启动后即可直接查询，无需手动"创建"设备。

### 6.1 设备信息（SimDeviceInfo）

| 字段 | device-001 | device-002 |
|------|-----------|-----------|
| deviceId | device-001 | device-002 |
| deviceName | REST-Node-01 | REST-Node-02 |
| deviceType | REST | REST |
| model | DS-D2055NH-A | DS-D2055NH-B |
| serialNumber | SN-REST-2024-0001 | SN-REST-2024-0002 |
| outputChannels | 2 | 2 |
| maxResolution | 1920x1080 | 1920x1080 |

### 6.2 设备状态（SimDeviceStatus）

| 字段 | device-001 | device-002 |
|------|-----------|-----------|
| deviceId | device-001 | device-002 |
| online | true | true |
| windowCount | 0 | 0 |
| uptime | 进程启动时间，格式 `yyyy-MM-dd HH:mm:ss` | 进程启动时间，格式 `yyyy-MM-dd HH:mm:ss` |

### 6.3 设备能力（SimDeviceCapability）

| 字段 | device-001 | device-002 |
|------|-----------|-----------|
| deviceId | device-001 | device-002 |
| maxWindows | 4 | 4 |
| supportMove | true | true |
| supportResize | true | true |
| supportOverlay | true | true |
| maxResolution | 1920x1080 | 1920x1080 |
| outputChannels | 2 | 2 |

> 默认设备启动时窗口为空（`windowCount=0`），窗口由管控系统通过 `POST /window` 创建后动态管理。

## 7. 设计说明

### 7.1 存储方式

设备数据全部存储在内存中（`LinkedHashMap`）。模拟设备进程重启后，内存清空并重新初始化 2 台默认硬件资源，能力恢复为默认值，窗口清空。

> **窗口恢复：** 模拟设备自身不持久化窗口数据，但管控系统（demo1-server）将窗口状态保存在数据库中。管控系统重启后可从数据库读取窗口列表，逐个调用 `POST /window` 重新下发到模拟设备，恢复窗口状态。

存储结构如下：

- **设备信息**：`Map<String, SimDeviceInfo>`，key 为 deviceId
- **设备能力**：`Map<String, SimDeviceCapability>`，key 为 deviceId，每台设备初始化时附带默认能力
- **窗口集合**：`Map<String, SimWindow>`，key 为 windowId，存储在对应设备名下

窗口与设备的关系：每个窗口属于一台设备，窗口的 `windowId` 为全局唯一标识。创建窗口时校验能力限制，关闭窗口时从集合中移除。

### 7.2 与管控系统的关系

模拟设备与管控系统是**两个独立进程**，各自启动：

| 模块 | 端口 | 启动方式 |
|------|------|----------|
| demo1-server（管控系统） | 8085 | `mvn spring-boot:run` |
| demo1-simulator（模拟设备） | 8086 | `mvn spring-boot:run` |

管控系统通过 HTTP 请求调用本接口文档中的 API，模拟设备离线时 HTTP 请求失败，管控系统即可检测到设备下线。

管控系统内部通过 `DeviceDriver` 接口封装 HTTP 调用，`RestDeviceDriver` 为 REST 设备的实现，具体看设备管理模块设计文档。

**设备唯一标识：** 管控系统通过 `baseUrl + deviceId` 组合来定位一台设备。`baseUrl` 指向模拟设备进程地址（如 `http://localhost:8086`），`deviceId` 区分该进程内的具体设备。同一进程内 `deviceId` 唯一，不会重复。

### 7.3 多实例模拟

每个模拟设备进程默认暴露 2 台硬件资源。单个进程的设备数量固定，增加设备数量有两种方式：

1. **启动多个进程**（不同端口）

```bash
# 进程1：端口 8086，暴露 2 台设备
java -jar demo1-simulator.jar --server.port=8086

# 进程2：端口 8087，暴露 2 台设备
java -jar demo1-simulator.jar --server.port=8087
```

此时管控系统可分别向 `8086` 和 `8087` 添加设备，总共管理 4 台。

2. **配置单进程设备数量**（推荐）

```properties
# application.properties
simulator.device-count=4
```

一个进程即可暴露 4 台设备，端口固定，管控系统只需向一个地址添加即可。

| 方式 | 进程数 | 端口数 | 总设备数 |
|------|-------|-------|---------|
| 默认 | 1 | 1 | 2 |
| 多进程 | 2 | 2 | 4 |
| 配置数量 | 1 | 1 | 4 |

### 7.4 统一返回格式

接口返回统一使用 `Result<T>` 封装：

```json
{
  "code": 1,     // 1=成功，0=失败
  "msg": null,   // 失败时包含错误信息
  "data": {}     // 业务数据
}
```

### 7.5 窗口管理流程

窗口操作由调用方（管控系统）通过 HTTP 请求触发，模拟设备被动响应。以创建窗口为例：

```text
调用方                             模拟设备
  │                                  │
  │── POST /device/{id}/window ──→  │── 校验设备是否存在
  │                                  │── 校验窗口 ID 是否重复
  │                                  │── 校验是否超过 maxWindows
  │                                  │── 存入窗口 Map
  │←── 200 OK ────────────────────  │
```

状态回读（调用方刷新/恢复状态时）：

```text
调用方                             模拟设备
  │                                  │
  │── GET /device/{id}/status ──→   │── 返回实时 windowCount
  │── GET /device/{id}/windows ─→   │── 返回窗口完整列表
  │                                  │
  │  调用方根据返回数据重建前端状态    │
```

> 调用方（管控系统）通过 `DeviceDriver` 接口封装 HTTP 调用，见设备管理模块设计文档。

### 7.6 接口汇总

**HTTP 接口：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/simulator/device/{deviceId}/info` | 获取设备信息 |
| GET | `/simulator/device/{deviceId}/status` | 获取设备状态（含实时 windowCount） |
| GET | `/simulator/device/{deviceId}/capability` | 获取设备能力 |
| PUT | `/simulator/device/{deviceId}/capability` | 更新设备能力（模拟能力变化） |
| POST | `/simulator/device/{deviceId}/window` | 创建窗口 |
| PUT | `/simulator/device/{deviceId}/window/{windowId}` | 更新窗口位置/大小 |
| DELETE | `/simulator/device/{deviceId}/window/{windowId}` | 关闭窗口 |
| GET | `/simulator/device/{deviceId}/windows` | 查询窗口列表（状态回读） |
| GET | `/simulator/device/{deviceId}/window/{windowId}` | 查询单个窗口 |

**UDP 发现协议：**

| 协议 | 端口 | 方向 | 说明 |
|------|------|------|------|
| UDP | 9999 | 广播接收 | 收到 `{"action":"discovery"}` 后回复设备列表 |

### 7.7 错误码

所有接口统一使用 `Result<T>` 封装，`code` 字段标识成功/失败：

| code | 说明 |
|------|------|
| 1 | 成功 |

失败时 `code` 统一为 `0`，具体错误由 `msg` 字段描述：

| 错误场景 | 触发接口 | msg 示例 |
|---------|---------|---------|
| 设备不存在 | GET info/status/capability/windows、POST window、PUT capability | `"设备不存在: device-999"` |
| 窗口不存在 | GET/DELETE/PUT window/{id} | `"窗口不存在: win-999"` |
| 窗口 ID 重复 | POST window | `"窗口已存在: win-001"` |
| 超过最大窗口数 | POST window | `"窗口数量已达上限: 4"` |
| 不支持窗口移动 | PUT window/{id} | `"设备不支持窗口移动"` |
| 不支持窗口缩放 | PUT window/{id} | `"设备不支持窗口缩放"` |
| 参数校验失败 | POST window | `"windowId 不能为空"` |

### 7.8 设备状态生命周期

模拟设备的数据全部在内存中，其生命周期与进程绑定。

**进程级生命周期：**

```text
进程启动
  │
  ├── SimDeviceManager 初始化
  │     ├── 创建默认设备（device-001, device-002）
  │     └── 初始化能力（maxWindows=4, ...）
  │
  └── 进入就绪状态，等待 HTTP 请求
        │
        ├── 接受窗口 CRUD 操作
        │     ├── 创建窗口 → windowCount++
        │     └── 关闭窗口 → windowCount--
        │
        ├── 接受能力变更（PUT capability）
        │     └── 运行时调整 maxWindows 等限制
        │
        └── 进程关闭 → 内存清空，所有数据丢失
```

**能力变更对窗口的影响：**

| 能力变更 | 对已有窗口的影响 |
|---------|----------------|
| 降低 maxWindows | 已有窗口不受影响，但新创建会被拒绝（若当前窗口数 ≥ 新上限） |
| 关闭 supportMove | 仅影响后续行为，模拟设备当前不强制校验 |
| 关闭 supportResize | 同上 |

**关键特性：**

- 设备**不存在**"创建"或"删除"操作——由进程启动/关闭自然决定
- 设备状态 `online` 始终为 `true`（进程存在即可达），离线由调用方 HTTP 超时判断
- 窗口数据为**唯一致信源**——调用方刷新页面时从模拟设备回读，而非依赖本地缓存