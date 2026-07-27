# REST 模拟设备接口文档

## 1. 概述

REST 模拟设备是异构硬件设备管控系统中的**独立 Spring Boot 程序**，用于模拟分布式节点设备，通过 HTTP + JSON 提供设备信息和状态查询接口。

模拟设备与管控系统分离部署，各自独立启动，通过 HTTP 通信，模拟真实分布式场景。

## 2. 项目结构（多模块）

```
demo1/                                  ← Maven 父项目
├── pom.xml
├── demo1-common/                       ← 公共模块（server 和 simulator 共享）
│   └── src/main/java/com/example/demo/
│       ├── common/
│       │   ├── Result.java             ← 统一返回格式
│       │   ├── PageDTO.java
│       │   └── PageResult.java
│       └── model/
│           ├── SimDeviceInfo.java       ← 设备信息模型
│           └── SimDeviceStatus.java     ← 设备状态模型
│
├── demo1-server/                       ← 管控系统后端（端口 8085）
│   └── src/main/java/com/example/demo/
│       ├── driver/
│       │   ├── DeviceDriver.java       ← 统一设备驱动接口
│       │   └── RestDeviceDriver.java   ← REST 设备驱动实现
│       └── ...
│
└── demo1-simulator/                    ← REST 模拟设备（端口 8086）
    └── src/main/java/com/example/demo/simulator/
        ├── controller/
        │   └── SimDeviceController.java    REST 接口层
        └── core/
            └── SimDeviceManager.java       设备管理核心（内存存储）
```

## 3. 统一设备驱动接口

管控系统通过 `DeviceDriver` 接口调用模拟设备，不直接依赖 REST 协议。后续接入 TLV 设备时只需新增 `TlvDeviceDriver` 实现，管控系统代码无需改动。

```java
public interface DeviceDriver {

    DeviceInfo getInfo(DeviceEndpoint endpoint);

    DeviceStatus getStatus(DeviceEndpoint endpoint);

    CommandResult openWindow(DeviceEndpoint endpoint, WindowCommand command);

    CommandResult closeWindow(DeviceEndpoint endpoint, String windowId);
}
```

`DeviceEndpoint` 描述设备的连接信息：

```java
public class DeviceEndpoint {
    private String deviceId;       // 设备唯一标识
    private String deviceType;     // 设备类型：REST / TLV
    private String baseUrl;        // REST 设备基地址，如 http://localhost:8086
    private String ip;             // TLV 设备 IP
    private int port;              // TLV 设备端口
}
```

`RestDeviceDriver` 当前只实现 `getInfo` 和 `getStatus`，`openWindow` / `closeWindow` 在 V1 阶段补齐：

```java
@Component
public class RestDeviceDriver implements DeviceDriver {

    public DeviceInfo getInfo(DeviceEndpoint endpoint) {
        String url = endpoint.getBaseUrl() + "/simulator/device/"
                + endpoint.getDeviceId() + "/info";
        return restTemplate.getForObject(url, DeviceInfo.class);
    }

    public DeviceStatus getStatus(DeviceEndpoint endpoint) {
        String url = endpoint.getBaseUrl() + "/simulator/device/"
                + endpoint.getDeviceId() + "/status";
        return restTemplate.getForObject(url, DeviceStatus.class);
    }

    // openWindow / closeWindow 在 V1 阶段实现
}
```

调用链路：

```
管控系统 (demo1-server :8085)
    │
    └── DeviceService.getDeviceInfo("device-001")
            │
            └── RestDeviceDriver.getInfo(endpoint)
                    │
                    └── HTTP GET http://localhost:8086/simulator/device/device-001/info
                            │
                            └── 模拟设备 (demo1-simulator :8086)
                                    │
                                    └── SimDeviceController → SimDeviceManager
```

## 4. 数据模型

### 4.1 SimDeviceInfo — 设备基本信息

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | String | 设备唯一标识，如 `device-001` |
| deviceName | String | 设备名称，如 `REST-Node-01` |
| deviceType | String | 设备类型，当前为 `REST` |
| model | String | 设备型号，如 `DS-D2055NH-A` |
| serialNumber | String | 序列号 |
| outputChannels | int | 输出通道数 |
| maxResolution | String | 最大分辨率，如 `1920x1080` |

### 4.2 SimDeviceStatus — 设备运行状态

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | String | 设备唯一标识 |
| online | boolean | 是否在线，当前始终为 `true` |
| windowCount | int | 当前窗口数量，当前始终为 `0` |
| uptime | String | 设备启动时间，格式 `yyyy-MM-dd HH:mm:ss` |

## 5. 接口列表

模拟设备独立运行在端口 **8086**，基路径：`http://localhost:8086/simulator`

### 5.1 获取设备信息

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

### 5.2 获取设备状态

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

## 6. 默认设备列表

模拟设备启动后自动注册 2 台设备：

| 设备 ID | 名称 | 型号 | 通道数 | 分辨率 |
|--------|------|------|--------|--------|
| device-001 | REST-Node-01 | DS-D2055NH-A | 2 | 1920x1080 |
| device-002 | REST-Node-02 | DS-D2055NH-B | 2 | 1920x1080 |

## 7. 设计说明

### 7.1 存储方式

设备数据全部存储在内存中（`LinkedHashMap`），模拟设备重启后自动清空并重新初始化 2 台默认设备。

### 7.2 与管控系统的关系

模拟设备与管控系统是**两个独立进程**，各自启动：

| 模块 | 端口 | 启动方式 |
|------|------|----------|
| demo1-server（管控系统） | 8085 | `mvn spring-boot:run` |
| demo1-simulator（模拟设备） | 8086 | `mvn spring-boot:run` |

管控系统通过 `RestDeviceDriver` 发起 HTTP 请求调用模拟设备，模拟设备离线时 HTTP 请求失败，管控系统即可检测到设备下线。

### 7.3 多实例模拟

模拟设备端口可配置，启动多个实例即可模拟多个分布式节点：

```bash
# 实例1：端口 8086
java -jar demo1-simulator.jar --server.port=8086

# 实例2：端口 8087
java -jar demo1-simulator.jar --server.port=8087
```

### 7.4 统一返回格式

接口返回统一使用 `Result<T>` 封装：

```json
{
  "code": 1,     // 1=成功，0=失败
  "msg": null,   // 失败时包含错误信息
  "data": {}     // 业务数据
}
```
