# REST 模拟设备设计文档

## 1. 概述

### 1.1 模块定位

REST 模拟设备是异构硬件设备管控系统中的**独立 Spring Boot 程序**，每个进程模拟**一台**分布式节点设备，通过 HTTP + JSON 提供设备信息和状态查询接口。

模拟设备与管控系统分离部署，各自独立启动，通过 HTTP 通信，模拟真实分布式场景。

**核心设计：一个模块可通过 Spring Profile 启动多个进程，一个端口 = 一台设备。** 如需模拟多台设备，启动多个进程并绑定不同端口即可。

> **设备类别说明：** 模拟设备分为两类，由 `deviceCategory` 字段标识（`INPUT`/`OUTPUT`）：
> - **输入设备（`deviceCategory = "INPUT"`）：** 拥有输入通道，负责提供信号源。输入设备不直接参与窗口操作，仅提供设备信息、状态、能力查询接口，以及通道播放地址设置。管控系统通过 /simulator/device/window/notify 接口向输入设备推送窗口信息，使输入设备感知其通道被哪些窗口占用。DEVICE_CAPABILITY 表无 `max_windows`、`support_move`、`support_resize`、`support_overlay` 字段。
> - **输出设备（`deviceCategory = "OUTPUT"`）：** 拥有输出通道，用于大屏绑定显示。支持窗口创建/关闭/查询/更新。具备窗口移动、缩放、叠加的设备能力，DEVICE_CAPABILITY 表有 `max_windows`、`support_move`、`support_resize`、`support_overlay` 字段。

---

## 2. 模块结构

### 2.1 文件结构

模拟设备是独立的 Spring Boot 程序，共有 2 个模拟器模块，每个模块可通过 Spring Profile 启动多个进程，每个进程代表一台设备。数据模型与管控系统共享 `demo1-common`。

```
demo1-simulator-input/                  ← 输入设备模块
└── src/main/resources/
    ├── application.yaml                ← 默认配置（端口 8086，UDP 9999）
    ├── application-8086.yaml           ← Profile 8086：输入设备-1（1个输入通道 HDMI-1）
    ├── application-8087.yaml           ← Profile 8087：输入设备-2（2个输入通道 HDMI-1, HDMI-2）
    ├── data-8086.sql                   ← 输入设备-1 初始数据
    └── data-8087.sql                   ← 输入设备-2 初始数据

demo1-simulator-output/                 ← 输出设备模块
└── src/main/resources/
    ├── application.yaml                ← 默认配置（端口 8088，UDP 9997）
    ├── application-8088.yaml           ← Profile 8088：输出设备-1（2个输出通道，maxWindows=4）
    ├── application-8089.yaml           ← Profile 8089：输出设备-2（3个输出通道，maxWindows=6）
    ├── data-8088.sql                   ← 输出设备-1 初始数据
    └── data-8089.sql                   ← 输出设备-2 初始数据

公共结构（两个模块相同）：
└── src/main/java/com/example/demo/simulator/
    ├── controller/
    │   └── SimDeviceController.java    ← REST 接口层
    ├── core/
    │   ├── SimDeviceManager.java       ← 设备管理核心（数据从 DB 加载）
    │   └── DeviceRepository.java       ← 数据访问层（JdbcTemplate）
    └── server/
        └── DiscoveryListener.java      ← UDP 设备发现监听
```

### 2.2 依赖关系

```text
SimDeviceController
    └── SimDeviceManager
            ├── DeviceRepository        → 数据库 CRUD（DEVICE_INFO、DEVICE_CAPABILITY）
            └── ConcurrentHashMap       → 窗口内存存储（不持久化）
```

---

## 3. 数据库设计

数据库只有两张表：**DEVICE_INFO**（设备信息表）和 **DEVICE_CAPABILITY**（设备能力表）。输入设备和输出设备模块的表结构不同。

### 3.1 建表 SQL

**输入设备模块**（`demo1-simulator-input/src/main/resources/schema.sql`）：

```sql
CREATE TABLE IF NOT EXISTS DEVICE_INFO (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name      VARCHAR(200)  NOT NULL,
    device_type      VARCHAR(50)   DEFAULT 'REST',
    device_category  VARCHAR(20)   DEFAULT 'INPUT',
    model            VARCHAR(100)  DEFAULT '',
    serial_number    VARCHAR(100)  DEFAULT '',
    channel_count    INT           DEFAULT 1,
    input_channel_1  VARCHAR(100)  DEFAULT '',
    input_channel_2  VARCHAR(100)  DEFAULT '',
    input_channel_3  VARCHAR(100)  DEFAULT '',
    input_channel_4  VARCHAR(100)  DEFAULT '',
    input_channel_5  VARCHAR(100)  DEFAULT '',
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080'
);

CREATE TABLE IF NOT EXISTS DEVICE_CAPABILITY (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080',
    channel_count    INT           DEFAULT 1,
    input_channel_1  VARCHAR(100)  DEFAULT '',
    input_channel_2  VARCHAR(100)  DEFAULT '',
    input_channel_3  VARCHAR(100)  DEFAULT '',
    input_channel_4  VARCHAR(100)  DEFAULT '',
    input_channel_5  VARCHAR(100)  DEFAULT ''
);
```

**输出设备模块**（`demo1-simulator-output/src/main/resources/schema.sql`）：

```sql
CREATE TABLE IF NOT EXISTS DEVICE_INFO (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name      VARCHAR(200)  NOT NULL,
    device_type      VARCHAR(50)   DEFAULT 'REST',
    device_category  VARCHAR(20)   DEFAULT 'OUTPUT',
    model            VARCHAR(100)  DEFAULT '',
    serial_number    VARCHAR(100)  DEFAULT '',
    channel_count    INT           DEFAULT 1,
    max_windows      INT           DEFAULT 4,
    output_channel_1 VARCHAR(100)  DEFAULT '',
    output_channel_2 VARCHAR(100)  DEFAULT '',
    output_channel_3 VARCHAR(100)  DEFAULT '',
    output_channel_4 VARCHAR(100)  DEFAULT '',
    output_channel_5 VARCHAR(100)  DEFAULT '',
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080'
);

CREATE TABLE IF NOT EXISTS DEVICE_CAPABILITY (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    max_windows      INT           DEFAULT 4,
    support_move     BOOLEAN       DEFAULT TRUE,
    support_resize   BOOLEAN       DEFAULT TRUE,
    support_overlay  BOOLEAN       DEFAULT TRUE,
    max_resolution   VARCHAR(50)   DEFAULT '1920x1080',
    channel_count    INT           DEFAULT 1,
    output_channel_1 VARCHAR(100)  DEFAULT '',
    output_channel_2 VARCHAR(100)  DEFAULT '',
    output_channel_3 VARCHAR(100)  DEFAULT '',
    output_channel_4 VARCHAR(100)  DEFAULT '',
    output_channel_5 VARCHAR(100)  DEFAULT ''
);
```

### 3.2 表关系图

```text
DEVICE_INFO (设备信息)          DEVICE_CAPABILITY (设备能力)
  id ────────────────────────── id
  device_name                   max_windows (仅输出设备)
  device_type                   support_move (仅输出设备)
  device_category               support_resize (仅输出设备)
  model                         support_overlay (仅输出设备)
  serial_number                 max_resolution
  channel_count                 channel_count
  input_channel_1~5             input_channel_1~5 (仅输入设备)
  output_channel_1~5 (仅输出)     output_channel_1~5 (仅输出设备)
  max_windows (仅输出设备)
  max_resolution

两表均为单条记录，通过各自 id 关联。
```

### 3.3 字段说明

**DEVICE_INFO（设备信息表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| device_name | VARCHAR(200) | 设备名称，如 `输入设备-1` |
| device_type | VARCHAR(50) | 设备类型，默认 `REST` |
| device_category | VARCHAR(20) | 设备类别，`INPUT`=输入设备，`OUTPUT`=输出设备 |
| model | VARCHAR(100) | 设备型号 |
| serial_number | VARCHAR(100) | 序列号 |
| channel_count | INT | 通道数量 |
| input_channel_1~5 | VARCHAR(100) | 输入通道名称（仅输入设备） |
| output_channel_1~5 | VARCHAR(100) | 输出通道名称（仅输出设备） |
| max_windows | INT | 最大窗口数（仅输出设备） |
| max_resolution | VARCHAR(50) | 最大分辨率 |

**DEVICE_CAPABILITY（设备能力表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| max_windows | INT | 最大窗口数（仅输出设备） |
| support_move | BOOLEAN | 是否支持窗口移动（仅输出设备） |
| support_resize | BOOLEAN | 是否支持窗口缩放（仅输出设备） |
| support_overlay | BOOLEAN | 是否支持窗口叠加（仅输出设备） |
| max_resolution | VARCHAR(50) | 最大分辨率 |
| channel_count | INT | 通道数量 |
| input_channel_1~5 | VARCHAR(100) | 输入通道名称（仅输入设备） |
| output_channel_1~5 | VARCHAR(100) | 输出通道名称（仅输出设备） |

---

## 4. 数据模型

数据模型定义在 `demo1-common` 模块中，供模拟器和管控系统共享。

### 4.1 SimDeviceInfo — 设备基本信息

位于 `demo1-common` 的 `model/` 包下，一个进程 = 一台设备，`baseUrl`（含端口）即设备唯一标识。

```java
@Data
public class SimDeviceInfo {

    private String deviceName;      // 设备名称，如 输入设备-1
    private String deviceType;      // 设备类型，当前为 REST
    private String deviceCategory;  // 设备类别：INPUT=输入设备，OUTPUT=输出设备
    private String model;           // 设备型号，如 DS-D2055NH-A
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

> 设备类型由具体拥有哪些通道决定。输入设备拥有输入通道，输出设备拥有输出通道。

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

---

## 5. 接口

### 5.1 接口汇总

模拟设备独立运行，通过 Spring Profile 指定端口，接口路径结构完全一致，仅端口号不同。输入设备默认端口 **8086**，输出设备默认端口 **8088**。

> **注意：** 管控系统仅接受 IP+端口 格式的 `baseUrl`，`localhost` 和域名不允许。
>
> **输出设备：** 输出设备支持窗口操作（创建、更新、关闭、查询），管控系统将大屏窗口按单元拆分为子窗口后推送到各输出设备。输入设备不直接参与窗口操作，但通过窗口信息反馈接口感知通道被哪些窗口占用。

| 方法 | 路径 | 用途 | 适用设备 | 说明 |
|------|------|------|:----:|------|
| GET | /simulator/device/info | 获取设备信息 |  全部  | 返回 SimDeviceInfo |
| GET | /simulator/device/status | 获取设备状态 |  全部  | 返回 SimDeviceStatus |
| GET | /simulator/device/capability | 获取设备能力 |  全部  | 返回 SimDeviceCapability |
| PUT | /simulator/device/capability | 更新设备能力 |  全部  | 模拟能力变化场景 |
| POST | /simulator/device/window | 创建窗口 |  输出  | 管控系统推送窗口到设备 |
| DELETE | /simulator/device/window/{windowId} | 关闭窗口 |  输出  | 释放窗口资源 |
| GET | /simulator/device/windows | 查询窗口列表 |  输出  | 状态回读，输出设备返回子窗口 |
| GET | /simulator/device/window/{windowId} | 查询单个窗口 |  输出  | 查询指定窗口详情 |
| PUT | /simulator/device/window/{windowId} | 更新窗口位置/大小 |  输出  | 移动窗口或调整大小，受supportMove/supportResize限制 |
| PUT | /simulator/channel/{channelName}/url | 设置通道播放地址 |  输入  | 为输入通道配置信号源 URL |
| GET | /simulator/channel/urls | 获取所有通道播放地址 |  输入  | 返回通道名到播放地址的映射 |
| POST | /simulator/device/window/notify | 窗口信息反馈 |  输入  | 管控系统推送窗口信息给输入设备，使其感知通道占用 |

### 5.2 接口详情

#### 5.2.1 获取设备信息

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
    "deviceName": "输入设备-1",
    "deviceType": "REST",
    "deviceCategory": "INPUT",
    "model": "DS-D2055NH-A",
    "serialNumber": "SN-INPUT-001",
    "inputChannel1": "HDMI-1",
    "inputChannel2": "",
    "outputChannel1": "",
    "outputChannel2": "",
    "maxResolution": "1920x1080"
  }
}
```

#### 5.2.2 获取设备状态

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

#### 5.2.3 获取设备能力

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

#### 5.2.4 更新设备能力（模拟能力变化）

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
  "supportResize": false,
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

#### 5.2.5 创建窗口

向设备下发创建窗口命令。创建前会校验：`channelName` 是否为该设备有效的通道名、窗口总数是否超过 `maxWindows` 限制。`sourceType` 和 `sourceUrl` 由设备根据通道配置自动返回，无需调用方传入。

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
  "height": 540
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
{ "code": 0, "msg": "窗口ID不能为空", "data": null }
```

**失败响应（通道名无效）：**

```json
{ "code": 0, "msg": "通道名无效: OUT-99", "data": null }
```

**失败响应（窗口 ID 重复）：**

```json
{ "code": 0, "msg": "窗口已存在: win-001", "data": null }
```

**失败响应（超过最大窗口数）：**

```json
{ "code": 0, "msg": "窗口数量已达上限: 4", "data": null }
```

#### 5.2.6 关闭窗口

关闭设备上指定 ID 的窗口，释放资源。

```
DELETE /simulator/device/window/{windowId}
```

**请求示例：**

```
DELETE http://192.168.1.100:8086/simulator/device/window/win-001
```

**成功响应：**

```json
{ "code": 1, "msg": null, "data": null }
```

**失败响应（窗口不存在）：**

```json
{ "code": 0, "msg": "窗口不存在: win-999", "data": null }
```

#### 5.2.7 查询窗口列表（状态回读）

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
      "x": 0, "y": 0,
      "width": 960, "height": 540,
      "sourceType": "HDMI",
      "sourceUrl": "",
      "createTime": "2026-07-28 14:30:00"
    }
  ]
}
```

#### 5.2.8 查询单个窗口

```
GET /simulator/device/window/{windowId}
```

**请求示例：**

```
GET http://192.168.1.100:8086/simulator/device/window/win-001
```

**成功响应：** 同创建窗口响应格式。

**失败响应（窗口不存在）：**

```json
{ "code": 0, "msg": "窗口不存在: win-999", "data": null }
```

#### 5.2.9 更新窗口位置/大小

移动窗口位置或调整窗口大小。校验 `supportMove` 和 `supportResize` 能力限制。

```
PUT /simulator/device/window/{windowId}
Content-Type: application/json
```

**请求体（移动窗口）：**

```json
{ "x": 100, "y": 200 }
```

**请求体（调整大小）：**

```json
{ "width": 800, "height": 600 }
```

**请求体（同时移动+缩放）：**

```json
{ "x": 100, "y": 200, "width": 800, "height": 600 }
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
    "x": 100, "y": 200,
    "width": 800, "height": 600,
    "sourceType": "HDMI",
    "sourceUrl": "",
    "createTime": "2026-07-28 14:30:00"
  }
}
```

**失败响应（不支持移动）：**

```json
{ "code": 0, "msg": "设备不支持窗口移动", "data": null }
```

**失败响应（不支持缩放）：**

```json
{ "code": 0, "msg": "设备不支持窗口缩放", "data": null }
```

#### 5.2.10 设置通道播放地址

为指定输入通道配置信号源 URL，供前端预览使用。**仅输入设备支持。**

```
PUT /simulator/channel/{channelName}/url
Content-Type: application/json
```

**请求示例：**

```
PUT http://192.168.1.100:8086/simulator/channel/HDMI-1/url
Content-Type: application/json

{"sourceUrl": "rtsp://192.168.1.50:554/stream1"}
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| sourceUrl | String | 否 | 信号源播放地址（流媒体 URL），传空字符串表示清空 |

**成功响应：**

```json
{ "code": 1, "msg": null, "data": null }
```

**失败响应（通道名无效）：**

```json
{ "code": 0, "msg": "通道名无效: OUT-99", "data": null }
```

#### 5.2.11 获取所有通道播放地址

返回当前设备所有通道名到播放地址的映射。**仅输入设备支持。**

```
GET /simulator/channel/urls
```

**请求示例：**

```
GET http://192.168.1.100:8086/simulator/channel/urls
```

**成功响应：**

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "HDMI-1": "rtsp://192.168.1.50:554/stream1",
    "HDMI-2": ""
  }
}
```


#### 5.2.12 窗口信息反馈（输入设备）

**POST /simulator/device/window/notify**

管控系统在输出设备上完成窗口操作后，通过此接口将窗口信息推送给输入设备，使输入设备感知其通道被哪些窗口占用。

**请求体：**

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

| 字段 | 类型 | 说明 |
|------|------|------|
| windowId | String | 窗口ID |
| channelName | String | 占用的输入通道名 |
| x | int | 窗口左上角x坐标 |
| y | int | 窗口左上角y坐标 |
| width | int | 窗口宽度 |
| height | int | 窗口高度 |

**推送时机：**

| 输出设备操作 | 输入设备收到的通知 |
|-------------|------|
| 创建窗口 | 含完整字段 |
| 更新窗口 | 含变更后的字段 |
| 关闭窗口 | 仅 windowId + channelName，坐标和尺寸为 0 |

**输入设备侧行为：**

- 内存中维护 `channelWindows` 映射（`Map<String, Set<String>>`，channelName → 占用该通道的 windowId 集合）
- 创建窗口时：将 windowId 加入对应 channelName 的集合
- 关闭窗口时：从对应 channelName 的集合中移除 windowId
- 查询时：可通过 GET /simulator/device/windows 返回该映射

**错误响应：**

| 条件 | 返回信息 |
|------|------|
| channelName 不在已定义通道中 | `"通道名无效: xxx"` |


---

## 6. 要完成的功能（流程图）

### 6.1 设备信息获取流程

```text
管控系统 HTTP 请求
  │
  ├── GET /simulator/device/info
  │     └── SimDeviceManager.getDeviceInfo()
  │           └── DeviceRepository.findDeviceInfo()
  │                 └── 从 DEVICE_INFO 表读取单条记录
  │                       └── 组装 SimDeviceInfo 返回
  │
  ├── GET /simulator/device/status
  │     └── SimDeviceManager.getDeviceStatus()
  │           └── 读取 online 状态 + 统计 ConcurrentHashMap 窗口数 + 计算 uptime
  │                 └── 组装 SimDeviceStatus 返回
  │
  └── GET /simulator/device/capability
        └── SimDeviceManager.getDeviceCapability()
              └── DeviceRepository.findDeviceCapability()
                    └── 从 DEVICE_CAPABILITY 表读取单条记录
                          └── 组装 SimDeviceCapability 返回
```

### 6.2 窗口创建、更新与关闭流程（核心链路）

```text
管控系统业务层
  │
  ├── Phase 1: 查询各输出设备能力（maxWindows、叠加、移动、缩放）
  │     └── GET /simulator/device/capability → 各输出设备
  │
  ├── Phase 2: 按单元拆分子窗口 → 推送到各输出设备
  │     └── POST /simulator/device/window
  │           ├── 校验 channelName 是否为该设备已定义的通道名
  │           │     └── 无效 → 返回 "通道名无效: xxx"
  │           ├── 校验 windowId 是否已存在
  │           │     └── 已存在 → 返回 "窗口已存在: xxx"
  │           ├── 校验窗口总数是否超过 maxWindows
  │           │     └── 超限 → 返回 "窗口数量已达上限: N"
  │           └── 校验通过 → 存入 ConcurrentHashMap → 返回 SimWindow
  │
  ├── Phase 3: 窗口信息反馈 → 推送到输入设备
  │     └── POST /simulator/device/window/notify
  │           └── 输入设备更新 channelWindows 映射（channelName → windowId 集合）
  │
  └── 窗口关闭/更新同理
        ├── DELETE /simulator/device/window/{windowId} → 输出设备
        │     └── 存在 → 从 ConcurrentHashMap 移除 → 返回成功
        ├── PUT /simulator/device/window/{windowId} → 输出设备
        │     └── 校验 supportMove/supportResize → 更新坐标/尺寸 → 返回成功
        └── POST /simulator/device/window/notify → 输入设备（反馈变更）
```

### 6.3 设备发现流程

```text
管控系统启动
  │
  ├── UDP 广播:255.255.255.255:9999 → 输入设备-1 (:8086)
  ├── UDP 广播:255.255.255.255:9998 → 输入设备-2 (:8087)
  ├── UDP 广播:255.255.255.255:9997 → 输出设备-1 (:8088)
  └── UDP 广播:255.255.255.255:9996 → 输出设备-2 (:8089)

各模拟设备 DiscoveryListener 收到 {"action":"discovery"}
  └── 单播回复 {"baseUrl":"http://192.168.1.100:8086","deviceType":"INPUT"}

管控系统 3 秒超时收集所有回复
  └── 汇总返回给前端，用户选择添加设备
        └── POST /device → 管控系统通过 HTTP 拉取设备详细信息
```

---

## 7. 补充

### 7.1 默认设备

系统提供 4 台模拟设备，通过 Spring Profile 启动不同进程。每台设备进程启动后自动初始化，管控系统启动后即可直接查询。

| 模块 | Profile | HTTP端口 | UDP发现 | 类别 | 通道 | maxWindows |
|------|:--:|:--:|:--:|:--:|------|:--:|
| demo1-simulator-input | 8086 | 8086 | 9999 | INPUT | 1个输入：HDMI-1 | — |
| demo1-simulator-input | 8087 | 8087 | 9998 | INPUT | 2个输入：HDMI-1, HDMI-2 | — |
| demo1-simulator-output | 8088 | 8088 | 9997 | OUTPUT | 2个输出：OUT-1, OUT-2 | 4 |
| demo1-simulator-output | 8089 | 8089 | 9996 | OUTPUT | 3个输出：OUT-1, OUT-2, OUT-3 | 6 |

### 7.2 存储方式

| 数据类型 | 存储方式 | 说明 |
|------|------|------|
| 设备基本信息 | H2 数据库 `DEVICE_INFO` 表 | 仅一条记录，首次启动自动导入 |
| 设备能力 | H2 数据库 `DEVICE_CAPABILITY` 表 | 仅一条记录，运行时修改会持久化 |
| 窗口数据 | 内存 `ConcurrentHashMap` | 不持久化，进程重启后丢失，由管控系统重新推送 |

**数据库文件：** 每台模拟设备使用独立的 H2 文件数据库，路径为 `./data/` 目录下以 Profile 命名的文件。

| 模拟设备 | 数据库文件 |
|---------|-----------|
| 输入设备-1（Profile 8086） | `./data/simulator_input_8086.mv.db` |
| 输入设备-2（Profile 8087） | `./data/simulator_input_8087.mv.db` |
| 输出设备-1（Profile 8088） | `./data/simulator_output_8088.mv.db` |
| 输出设备-2（Profile 8089） | `./data/simulator_output_8089.mv.db` |

**自启动初始化：** `application.yaml` 中配置 `spring.sql.init.mode: always`，启动时自动执行 `schema.sql`（`CREATE TABLE IF NOT EXISTS`）和 `data.sql`（`INSERT ... WHERE NOT EXISTS` 仅首次插入）。

### 7.3 多设备模拟

每个模块通过 Spring Profile 启动多个进程，**一个进程 = 一台设备**：

```bash
# 输入设备模块：启动 2 个进程
java -jar demo1-simulator-input.jar --spring.profiles.active=8086   # 输入设备-1
java -jar demo1-simulator-input.jar --spring.profiles.active=8087   # 输入设备-2

# 输出设备模块：启动 2 个进程
java -jar demo1-simulator-output.jar --spring.profiles.active=8088  # 输出设备-1
java -jar demo1-simulator-output.jar --spring.profiles.active=8089  # 输出设备-2
```

> 每个进程内的设备信息完全独立，管控系统通过 `baseUrl` 区分不同设备。如需模拟更多同类型设备，可新增 Profile 配置文件（如 `application-8090.yaml`），修改端口和 UDP 发现端口即可。

### 7.4 与管控系统的关系

模拟设备与管控系统是**两个独立进程**，各自启动：

| 模块 | 端口 | 说明 |
|------|------|------|
| demo1-server（管控系统） | 8085 | 通过 `mvn spring-boot:run` 启动 |
| 输入设备-1（Profile 8086） | 8086 | 1个输入通道，查询接口 + 窗口信息反馈 |
| 输入设备-2（Profile 8087） | 8087 | 2个输入通道，查询接口 + 窗口信息反馈 |
| 输出设备-1（Profile 8088） | 8088 | 2个输出通道，创建/关闭/查询窗口 |
| 输出设备-2（Profile 8089） | 8089 | 3个输出通道，创建/关闭/查询窗口 |

管控系统通过 HTTP 请求调用本接口文档中的 API，模拟设备离线时 HTTP 请求失败，管控系统即可检测到设备下线。

**设备定位：** 管控系统通过 `baseUrl` 来定位一台设备。`baseUrl` 指向模拟设备进程地址（如 `http://192.168.1.100:8086`），**仅接受 IP+端口格式**，每个进程（Profile）只有一台设备，`baseUrl` 即设备唯一标识。

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

### 7.6 设备发现协议（UDP）

模拟设备通过 UDP 协议支持设备自动发现。管控系统发送 UDP 广播搜索设备，每台模拟设备收到后回复自身信息。

| 参数 | 值 |
|------|-----|
| 传输协议 | UDP |
| 监听端口 | 每台模拟设备独立 UDP 端口（9999 / 9998 / 9997 / 9996） |
| 广播地址 | 255.255.255.255 |
| 序列化格式 | JSON |

**请求格式：** 管控系统向所有 UDP 端口发送广播：

```json
{"action": "discovery"}
```

**响应格式：** 每台模拟设备收到广播后，单播回复自身地址和设备类型：

```json
{"baseUrl": "http://192.168.1.100:8086", "deviceType": "INPUT"}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| baseUrl | String | 设备 HTTP 地址 |
| deviceType | String | `INPUT` 或 `OUTPUT` |

管控系统收到回复后无需再调用 `/simulator/device/info` 即可判别设备类型。

`DiscoveryListener` 在模拟设备启动时通过 `@PostConstruct` 自动开启守护线程，监听配置的 UDP 端口。收到 `{"action":"discovery"}` 时，构造含 `baseUrl` 和 `deviceType` 的 JSON 回复并原路返回。纯 JDK 实现（`java.net.DatagramSocket` + `DatagramPacket`），无需引入额外依赖。