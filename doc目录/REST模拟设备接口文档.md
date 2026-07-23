# REST 模拟设备接口文档

## 1. 概述

REST 模拟设备是异构硬件设备管控系统中的一个内置模块，用于模拟分布式节点设备，通过 HTTP + JSON 提供设备信息和状态查询接口。


## 2. 模块结构

```
src/main/java/com/example/demo/simulator/
├── controller/
│   └── SimDeviceController.java    REST 接口层
├── core/
│   └── SimDeviceManager.java       设备管理核心（内存存储）
└── model/
    ├── SimDeviceInfo.java          设备基本信息模型
    └── SimDeviceStatus.java        设备运行状态模型
```

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
|------|------|------|
| deviceId | String | 设备唯一标识 |
| online | boolean | 是否在线，当前始终为 `true` |
| windowCount | int | 当前窗口数量，当前始终为 `0` |
| uptime | String | 设备启动时间，格式 `yyyy-MM-dd HH:mm:ss` |

## 4. 接口列表

基路径：`http://localhost:8085/simulator`

### 4.1 获取设备信息

```
GET /simulator/device/{deviceId}/info
```

**请求示例：**

```
GET http://localhost:8085/simulator/device/device-001/info
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
GET http://localhost:8085/simulator/device/device-001/status
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

## 5. 默认设备列表

应用启动后自动注册 2 台模拟设备：

| 设备 ID | 名称 | 型号 | 通道数 | 分辨率 |
|--------|------|------|--------|--------|
| device-001 | REST-Node-01 | DS-D2055NH-A | 2 | 1920x1080 |
| device-002 | REST-Node-02 | DS-D2055NH-B | 2 | 1920x1080 |

## 6. 设计说明

### 6.1 存储方式

设备数据全部存储在内存中（`LinkedHashMap`），应用重启后自动清空并重新初始化。

### 6.2 与管控系统的关系

模拟设备与管控系统运行在同一 JVM 进程中，无需额外启动。管控系统通过 HTTP 调用本地接口即可获取设备数据。

### 6.3 统一返回格式

接口返回统一使用 `Result<T>` 封装：

```json
{
  "code": 1,     // 1=成功，0=失败
  "msg": null,   // 失败时包含错误信息
  "data": {}     // 业务数据
}
```
