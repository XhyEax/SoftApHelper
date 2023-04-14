# SoftApHelper

SoftAp static server IP(v4) for Android P-T (Xposed)

## 下载
[Release](https://github.com/XhyEax/SoftApHelper/releases)

## 连接测试
开启热点后，手机端使用`ifconfig`命令查看ip。或使用其他机器连接热点后，`ping 192.168.43.1`。

### 注意
**网络前缀冲突**会导致网络连接失败（`Android 10`及以下）或仍使用随机IP（`Android 11`及以上，日志提示`isConflictPrefix`）。

wifi热点为`192.168.43.1`，同时提供了`192.168.1.1`版本（`43.1`**连不上的先试试这个**）。

从`v1.040`开始，提供`wifionly`和`all`版本，支持设置`WIFI`、`USB`、`蓝牙`的热点IP（`Android 11`及以上）。


|           Type          |         IP              |
|-------------------------|-----------------------|
| WIFI | WIFI_HOST_IFACE_ADDR  |
| USB  | 192.168.42.1          |
| BlueTooth   | 192.168.44.1          |


推荐使用`LSPosed`指定作用域（已配置推荐作用域）
## 作用域
### 安卓11及以下
系统框架

### 安卓12及以上（以及部分安卓11设备）
`com.google.android.networkstack.tethering.inprocess`

`com.android.networkstack.tethering.inprocess`

`com.google.android.networkstack.tethering`

`com.android.networkstack.tethering`

PS：如果未生效，可尝试勾选更多包名包含`networkstack.tethering`的应用。

若仍未生效，请上传`/apex/com.android.tethering/priv-app/`下的apk到[Issues](https://github.com/XhyEax/SoftApHelper/issues)。

## Todo
- [ ] 自定义ip
- [ ] 自定义生效的网络类型

## 原理
[安卓9 固定Wifi热点IP (Xposed)](https://blog.xhyeax.com/2021/03/01/android-9-set-hotpot-ip/)

[安卓10、11 固定Wifi热点IP (Xposed)](https://blog.xhyeax.com/2021/12/06/android-10-11-hostpot-set-ip/)

[安卓12 固定Wifi热点IP (Xposed)](https://blog.xhyeax.com/2022/07/06/android-12-hostpot-set-ip/)

## Hook点
### 安卓9
`com.android.server.connectivity.tethering.TetherInterfaceStateMachine`的`getRandomWifiIPv4Address`函数。

[TetherInterfaceStateMachine.java#259](http://aospxref.com/android-9.0.0_r61/xref/frameworks/base/services/core/java/com/android/server/connectivity/tethering/TetherInterfaceStateMachine.java#259)
```java
private String getRandomWifiIPv4Address()
```

### 安卓10
`android.net.ip.IpServer`的`getRandomWifiIPv4Address`函数。

[IpServer.java#469](http://aospxref.com/android-10.0.0_r47/xref/frameworks/base/services/net/java/android/net/ip/IpServer.java#469)
```java
private String getRandomWifiIPv4Address()
```

### 安卓11
`android.net.ip.IpServer`的`requestIpv4Address`函数。

[IpServer.java#645](http://aospxref.com/android-11.0.0_r21/xref/frameworks/base/packages/Tethering/src/android/net/ip/IpServer.java#645)
```java
private LinkAddress requestIpv4Address()
```

由于该函数还被用于其他方式的网络共享及更换前缀，所以需要判断网络类型（`mInterfaceType == TETHERING_WIFI`）和调用者（遍历堆栈查找`configureIPv4`），最后进行替换。


### 安卓12
`android.net.ip.IpServer`的`requestIpv4Address`函数。

[IpServer.java#655](http://aospxref.com/android-12.0.0_r3/xref/packages/modules/Connectivity/Tethering/src/android/net/ip/IpServer.java#655)
```java
private LinkAddress requestIpv4Address(final boolean useLastAddress)
```

### 安卓13
Hook点同安卓12（未测试，理论上可用）

[IpServer.java#664](http://aospxref.com/android-13.0.0_r3/xref/packages/modules/Connectivity/Tethering/src/android/net/ip/IpServer.java#664)
```java
private LinkAddress requestIpv4Address(final boolean useLastAddress)
```

## 感谢
[@mmfmkuang](https://github.com/mmfmkuang)

[@dsfgdadg](https://github.com/dsfgdadg)
