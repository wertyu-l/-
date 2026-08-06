# REST 模拟设备接口文档

## 1. 概述

REST 模拟设备是异构硬件设备管控系统中的**独立 Spring Boot 程序**，每个进程模拟**一台**分布式节点设备，通过 HTTP + JSON 提供设备信息和状态查询接口。

模拟设备与管控系统分离部署，各自独立启动，通过 HTTP 通信，模拟真实分布式场景。

**核心设计：一个端口 = 一台设备。** 如需模拟多台设备，启动多个进程并绑定不同端口即可。


> **设备类别说明：** 模拟设备分为两类，由 `deviceCategory` 字段标识（`INPUT`/`OUTPUT`）：
> - **输入设备（`deviceCategory = "INPUT"`）：** 拥有输入通道，负责提供信号源。**窗口相关接口（创建/关闭/查询/更新窗口）仅对输入设备有意义。**
> - **输出设备（`deviceCategory = "OUTPUT"`）：** 拥有输出通道，用于大屏绑定显示。**输出设备不存在窗口概念，不涉及窗口操作。**
## 2. 项目结构

模拟设备是独立的 Spring Boot 程序，共有 4 个模拟器模块，数据模型与管控系统共享 `demo1-common`。

```
demo1-simulator/                        ← 模拟设备1（端口 8086，输入设备，1个输入通道）
demo1-simulator2/                       ← 模拟设备2（端口 8087，输入设备，2个输入通道）
demo1-simulator3/                       ← 模拟设备3（端口 8088，输出设备，2个输出通道）
demo1-simulator4/                       ← 模拟设备4（端口 8089，输出设备，2个输出通道）
└── src/main/java/com/example/demo/simulator{2,3,4}/
    ├── controller/
    │   └── SimDeviceController.java    ← REST 接口层
    ├── core/
    │   ├── SimDeviceManager.java       ← 设备管理核心（数据从 DB 加载）
    │   └── DeviceRepository.java       ← 数据访问层（JdbcTemplate）
    └── server/
        └── DiscoveryListener.java      ← UDP 设备发现监听
    ├── main/resources/
    │   ├── application.yaml            ← 含 H2 数据源 + 端口 + 发现端口配置
    │   ├── schema.sql                  ← 建表脚本（自启动执行）
    │   └── data.sql                    ← 初始数据（仅首次导入）

demo1-common/                           ← 共享数据模型
└── src/main/java/com/example/demo/model/
    ├── SimDeviceInfo.java
    ├── SimDeviceStatus.java
    ├── SimWindow.java
    └── SimDeviceCapability.java
```

> 管控系统（demo1-server）通过 `DeviceDriver` 接口调用模拟设备，具体看设备管理模块设计文档。
> 输入设备拥有窗口操作 API（创建/关闭/查询/更新窗口），输出设备不涉及窗口操作。

## 3. 数据模型

### 3.1 SimDeviceInfo — 设备基本信息

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceName | String | 设备名称，如 `REST-Node-01` |
| deviceType | String | 设备类型，当前为 `REST` |
| deviceCategory | String | 设备类别，`INPUT`=输入设备，`OUTPUT`=输出设备 |
| model | String | 设备型号，如 `DS-D2055NH-A` |
| serialNumber | String | 序列号 |
| inputChannel1 | String | 输入通道1名称，如 `HDMI-1`，为空表示无该通道 |
| inputChannel2 | String | 输入通道2名称，如 `HDMI-2`，为空表示无该通道 |
| outputChannel1 | String | 输出通道1名称，如 `OUT-1`，为空表示无该通道 |
| outputChannel2 | String | 输出通道2名称，如 `OUT-2`，为空表示无该通道 |
| maxResolution | String | 最大分辨率，如 `1920x1080` |

> **通道名唯一性：** 同一设备下所有非空通道名不可重复。
>
> **设备类型说明：** 设备是单一类型的，由 `deviceCategory` 字段标识（`INPUT`/`OUTPUT`）。
> - **输入设备（`deviceCategory = "INPUT"`）：** 仅 `inputChannel1` 有值，`outputChannel1`/`outputChannel2` 为空，用于提供信号源
> - **输出设备（`deviceCategory = "OUTPUT"`）：** 仅 `outputChannel1`/`outputChannel2` 有值，`inputChannel1` 为空，用于大屏绑定显示
> - 数据模型中同时保留输入/输出通道字段仅为方便，实际使用中按 `deviceCategory` 判断

### 3.2 SimDeviceStatus — 设备运行状态

| 字段 | 类型 | 说明 |
|------|------|----|
| online | boolean | 是否在线，当前始终为 `true` |
| windowCount | int | 窗口数 |
| uptime | String | 设备启动时间，格式 `yyyy-MM-dd HH:mm:ss` |

### 3.3 SimWindow — 窗口信息

窗口是管控系统下发到模拟设备的内容展示单元，每个窗口绑定到设备的某个输入通道。窗口操作仅对输入设备有意义。

| 字段 | 类型 | 必填 | 不可重复 | 说明 |
|------|------|:--:|:----:|------|
| windowId | String | 是 | 是 | 窗口唯一标识，由管控系统生成，全局唯一 |
| channelName | String | 是 | 否 | 绑定的输入通道名称，必须是该设备已定义的输入通道名之一。同一输入通道可以有多个窗口 |
| x | int | 否 | 否 | 窗口左上角 X 坐标，默认 0 |
| y | int | 否 | 否 | 窗口左上角 Y 坐标，默认 0 |
| width | int | 否 | 否 | 窗口宽度（像素），默认 1920 |
| height | int | 否 | 否 | 窗口高度（像素），默认 1080 |
| sourceType | String | 否 | 否 | 信号源类型，由设备根据通道配置返回，如 `HDMI`、`VGA`、`Stream` |
| sourceUrl | String | 否 | 否 | 信号源地址，由设备根据通道配置返回，如流媒体 URL，默认 `""` |
| createTime | String | 否 | 否 | 窗口创建时间，格式 `yyyy-MM-dd HH:mm:ss`，自动生成 |

### 3.4 SimDeviceCapability — 设备能力

设备能力描述设备的功能限制，控制窗口创建时的校验规则。能力可以在运行时动态变更，用于模拟设备能力变化场景。

| 字段 | 类型 | 说明 |
|------|------|------|
| maxWindows | int | 最大窗口数量，用于设备级别窗口总数校验 |
| supportMove | boolean | 是否支持窗口移动 |
| supportResize | boolean | 是否支持窗口缩放 |
| supportOverlay | boolean | 是否支持窗口叠加 |
| maxResolution | String | 最大分辨率，如 `1920x1080` |
| inputChannel1 | String | 输入通道1名称 |
| inputChannel2 | String | 输入通道2名称 |
| outputChannel1 | String | 输出通道1名称 |
| outputChannel2 | String | 输出通道2名称 |

> **注意：** 能力表中的通道名和 `maxResolution` 与设备信息表共享，更新能力时会自动同步到设备信息表。
> `inputChannel1`/`inputChannel2`/`outputChannel1`/`outputChannel2` 字段含义同上，按设备类型二选一。


## 4. 接口列表

模拟设备独立运行，默认端口 **8086**，基路径：`http://192.168.1.100:8086/simulator`。不同设备进程使用不同端口，接口路径结构完全一致，仅端口号不同。

> **注意：** 管控系统仅接受 IP+端口 格式的 `baseUrl`，`localhost` 和域名不允许。

### 4.1 获取设备信息

```
GET /simulator/device/info
```

**请求示例：**

```
GET http://192.168.1.100:8086/simulator/device/info
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "deviceName": "REST-Node-01",
    "deviceType": "REST",
    "deviceCategory": "INPUT",
    "model": "DS-D2055NH-A",
    "serialNumber": "SN-REST-2024-0001",
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "outputChannel1": "",
    "outputChannel2": "",
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
GET http://192.168.1.100:8086/simulator/device/status
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
GET http://192.168.1.100:8086/simulator/device/capability
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
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "outputChannel1": "",
    "outputChannel2": ""
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
  "inputChannel1": "HDMI-1",
  "inputChannel2": "",
  "outputChannel1": "",
  "outputChannel2": ""
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
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "outputChannel1": "",
    "outputChannel2": ""
  }
}
```

### 4.5 创建窗口

向设备下发创建窗口命令。创建前会校验：`channelName` 是否为该设备有效的输入通道名、窗口总数是否超过 `maxWindows` 限制。输入通道不限制窗口数量，同一通道可创建多个窗口。`sourceType` 和 `sourceUrl` 由设备根据通道配置自动返回，无需调用方传入。

```
POST /simulator/device/window
Content-Type: application/json
```

**请求体：**

```json
{
  "windowId": "win-001",
  "channelName": "HDMI-1",
  "x": 0,
  "y": 0,
  "width": 960,
  "height": 540,
}
```

**成功响应：**

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
    "createTime": "2026-07-28 14:30:00"
  }
}
```

**失败响应（窗口 ID 为空）：**

```json
{
  "code": 0,
  "msg": "窗口ID不能为空",
  "data": null
}
```

**失败响应（通道名无效）：**

```json
{
  "code": 0,
  "msg": "通道名无效: OUT-99",
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

**失败响应（超过最大窗口数）：**

```json
{
  "code": 0,
  "msg": "窗口数量已达上限: 4",
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
DELETE http://192.168.1.100:8086/simulator/device/window/win-001
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
GET http://192.168.1.100:8086/simulator/device/windows
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": [
    {
      "windowId": "win-001",
      "channelName": "HDMI-1",
      "x": 0,
      "y": 0,
      "width": 960,
      "height": 540,
      "sourceType": "HDMI",
      "sourceUrl": "",
      "createTime": "2026-07-28 14:30:00"
    },
    {
      "windowId": "win-002",
      "channelName": "HDMI-2",
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
GET http://192.168.1.100:8086/simulator/device/window/win-001
```

**成功响应：**

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
    "channelName": "HDMI-1",
    "x": 100,
    "y": 200,
    "width": 800,
    "height": 600,
    "sourceType": "HDMI",
    "sourceUrl": "",
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
| 监听端口 | 每台模拟设备独立 UDP 端口（9999 / 9998 / 9997 / 9996） |
| 广播地址 | 255.255.255.255 |
| 序列化格式 | JSON |

> 由于多台模拟设备进程可能运行在同一台机器上，每台设备绑定独立的 UDP 发现端口以避免端口冲突。管控系统向所有端口广播搜索请求。

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

`DiscoveryListener` 在模拟设备启动时通过 `@PostConstruct` 自动开启守护线程，监听配置的 UDP 端口（由 `application.yaml` 中的 `discovery.port` 指定）。收到 `{"action":"discovery"}` 时，构造仅含 `baseUrl` 的 JSON 回复并原路返回。

纯 JDK 实现，无需引入额外依赖（`java.net.DatagramSocket` + `DatagramPacket`）。

管控系统通过 `discovery.ports` 配置向所有模拟设备的 UDP 端口广播搜索请求。当前配置为 `9999, 9998, 9997, 9996`。

---

## 6. 默认设备

系统提供 4 台模拟设备，每台设备进程启动后自动初始化。管控系统启动后即可直接查询，无需手动创建。

### 6.1 模拟设备1 — 输入设备（端口 8086，UDP 9999）

**设备信息（SimDeviceInfo）**

| 字段 | 值 |
|------|-----|
| deviceName | REST-Node-01 |
| deviceType | REST |
| deviceCategory | INPUT |
| model | DS-D2055NH-A |
| serialNumber | SN-REST-2024-0001 |
| inputChannel1 | HDMI-1 |
| inputChannel2 | |
| outputChannel1 | |
| outputChannel2 | |
| maxResolution | 1920x1080 |

**设备状态（SimDeviceStatus）**：online=true, windowCount=0, uptime=进程启动时间

**设备能力（SimDeviceCapability）**：maxWindows=4, supportMove/Resize/Overlay=true, maxResolution=1920x1080, inputChannel1=HDMI-1

### 6.2 模拟设备2 — 输入设备（端口 8087，UDP 9998）

| 字段 | 值 |
|------|-----|
| deviceName | REST-Node-02 |
| deviceType | REST |
| deviceCategory | INPUT |
| model | DS-D2055NH-B |
| serialNumber | SN-REST-2024-0002 |
| inputChannel1 | HDMI-1 |
| inputChannel2 | HDMI-2 |
| outputChannel1 | |
| outputChannel2 | |
| maxResolution | 1920x1080 |

> 能力、状态与设备1相同，inputChannel2=HDMI-2。

### 6.3 模拟设备3 — 输出设备（端口 8088，UDP 9997）

| 字段 | 值 |
|------|-----|
| deviceName | REST-Node-03 |
| deviceType | REST |
| deviceCategory | OUTPUT |
| model | DS-D2055NH-C |
| serialNumber | SN-REST-2024-0003 |
| inputChannel1 | |
| inputChannel2 | |
| outputChannel1 | OUT-1 |
| outputChannel2 | OUT-2 |
| maxResolution | 1920x1080 |

> 输出设备不涉及窗口操作，无窗口 API。

### 6.4 模拟设备4 — 输出设备（端口 8089，UDP 9996）

| 字段 | 值 |
|------|-----|
| deviceName | REST-Node-04 |
| deviceType | REST |
| deviceCategory | OUTPUT |
| model | DS-D2055NH-D |
| serialNumber | SN-REST-2024-0004 |
| inputChannel1 | |
| inputChannel2 | |
| outputChannel1 | OUT-1 |
| outputChannel2 | OUT-2 |
| maxResolution | 1920x1080 |

> 输出设备不涉及窗口操作，无窗口 API。


---

## 7. 设计说明

### 7.1 存储方式

设备数据使用 **H2 文件数据库** 持久化存储，进程重启后数据保留，窗口自动恢复。

| 数据类型 | 存储方式 | 说明 |
|------|------|------|
| 设备基本信息 | H2 数据库 `DEVICE_INFO` 表 | 仅一条记录，首次启动自动导入 |
| 设备能力 | H2 数据库 `DEVICE_CAPABILITY` 表 | 仅一条记录，运行时修改会持久化 |
| 窗口数据 | H2 数据库 `DEVICE_WINDOW` 表 | 创建/更新/删除即时写入 DB |

**数据库文件：** 每台模拟设备使用独立的 H2 文件数据库，路径为 `./data/` 目录下以模块名命名的文件。

| 模拟设备 | 数据库文件 |
|------|------|
| demo1-simulator（端口 8086） | `./data/simulator1.mv.db` |
| demo1-simulator2（端口 8087） | `./data/simulator2.mv.db` |
| demo1-simulator3（端口 8088） | `./data/simulator3.mv.db` |
| demo1-simulator4（端口 8089） | `./data/simulator4.mv.db` |

**自启动初始化：** `application.yaml` 中配置 `spring.sql.init.mode: always`，启动时自动执行 `schema.sql`（`CREATE TABLE IF NOT EXISTS`）和 `data.sql`（`INSERT ... WHERE NOT EXISTS` 仅首次插入）。

### 7.2 数据库表结构

**DEVICE_INFO（设备信息表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| device_name | VARCHAR(200) | 设备名称，如 `REST-Node-01` |
| device_type | VARCHAR(50) | 设备类型，默认 `REST` |
| device_category | VARCHAR(20) | 设备类别，`INPUT`=输入设备，`OUTPUT`=输出设备 |
| model | VARCHAR(100) | 设备型号 |
| serial_number | VARCHAR(100) | 序列号 |
| input_channel_1 | VARCHAR(100) | 输入通道1名称，为空表示无该通道 |
| input_channel_2 | VARCHAR(100) | 输入通道2名称，为空表示无该通道 |
| output_channel_1 | VARCHAR(100) | 输出通道1名称，为空表示无该通道 |
| output_channel_2 | VARCHAR(100) | 输出通道2名称，为空表示无该通道 |
| max_resolution | VARCHAR(50) | 最大分辨率 |

**DEVICE_CAPABILITY（设备能力表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| max_windows | INT | 最大窗口数量 |
| support_move | BOOLEAN | 是否支持窗口移动 |
| support_resize | BOOLEAN | 是否支持窗口缩放 |
| support_overlay | BOOLEAN | 是否支持窗口叠加 |
| max_resolution | VARCHAR(50) | 最大分辨率 |
| input_channel_1 | VARCHAR(100) | 输入通道1名称，为空表示无该通道 |
| input_channel_2 | VARCHAR(100) | 输入通道2名称，为空表示无该通道 |
| output_channel_1 | VARCHAR(100) | 输出通道1名称，为空表示无该通道 |
| output_channel_2 | VARCHAR(100) | 输出通道2名称，为空表示无该通道 |

**DEVICE_WINDOW（窗口表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| window_id | VARCHAR(100) | 主键，窗口唯一标识 |
| channel_name | VARCHAR(100) | 绑定的输入通道名称 |
| x | INT | 窗口 X 坐标，默认 0 |
| y | INT | 窗口 Y 坐标，默认 0 |
| width | INT | 窗口宽度，默认 1920 |
| height | INT | 窗口高度，默认 1080 |
| source_type | VARCHAR(50) | 信号源类型 |
| source_url | VARCHAR(500) | 信号源地址 |
| create_time | VARCHAR(20) | 窗口创建时间 |

> 窗口表仅在输入设备中存在，输出设备不涉及窗口操作，无此表。

### 7.3 与管控系统的关系

模拟设备与管控系统是**两个独立进程**，各自启动：

| 模块 | 端口 | 说明 |
|------|------|------|
| demo1-server（管控系统） | 8085 | 通过 `mvn spring-boot:run` 启动 |
| demo1-simulator（输入设备1） | 8086 | 1个输入通道，有窗口 API |
| demo1-simulator2（输入设备2） | 8087 | 2个输入通道，有窗口 API |
| demo1-simulator3（输出设备1） | 8088 | 2个输出通道，无窗口 API |
| demo1-simulator4（输出设备2） | 8089 | 2个输出通道，无窗口 API |

管控系统通过 HTTP 请求调用本接口文档中的 API，模拟设备离线时 HTTP 请求失败，管控系统即可检测到设备下线。

管控系统内部通过 `DeviceDriver` 接口封装 HTTP 调用，`RestDeviceDriver` 为 REST 设备的实现，具体看设备管理模块设计文档。

**设备定位：** 管控系统通过 `baseUrl` 来定位一台设备。`baseUrl` 指向模拟设备进程地址（如 `http://192.168.1.100:8086`），**仅接受 IP+端口格式**，每个进程只有一台设备，`baseUrl` 即设备唯一标识。

### 7.4 多设备模拟

系统提供 4 个独立的模拟器模块，**一个进程 = 一台设备**：

```bash
# 输入设备1：端口 8086，1个输入通道 HDMI-1
java -jar demo1-simulator.jar

# 输入设备2：端口 8087，2个输入通道 HDMI-1、HDMI-2
java -jar demo1-simulator2.jar

# 输出设备3：端口 8088，2个输出通道 OUT-1、OUT-2
java -jar demo1-simulator3.jar

# 输出设备4：端口 8089，2个输出通道 OUT-1、OUT-2
java -jar demo1-simulator4.jar
```

管控系统分别向对应地址添加设备即可。

| 模块 | 端口 | UDP发现 | 类别 | 通道 |
|------|:--:|:--:|:--:|------|
| demo1-simulator | 8086 | 9999 | INPUT | 1个输入：HDMI-1 |
| demo1-simulator2 | 8087 | 9998 | INPUT | 2个输入：HDMI-1, HDMI-2 |
| demo1-simulator3 | 8088 | 9997 | OUTPUT | 2个输出：OUT-1, OUT-2 |
| demo1-simulator4 | 8089 | 9996 | OUTPUT | 2个输出：OUT-1, OUT-2 |

> 每个进程内的设备信息完全独立，管控系统通过 `baseUrl` 区分不同设备。
> 如需模拟更多同类型设备，可复制任一模拟器模块，修改 `application.yaml` 中的端口和 UDP 发现端口即可。

### 7.5 统一返回格式

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
