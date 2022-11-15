# SoftApHelper

SoftAp static server IP(v4) for Android P-T (Xposed)

## 下载
[Release](https://github.com/XhyEax/SoftApHelper/releases)

## 连接测试
手机端使用`ifconfig`命令查看ip，或使用其他机器连接热点后，`ping 192.168.43.1`。

### 注意
日志报错`ClassNotFoundException`，不影响正常使用。**插件生效与否请手动测试**。

默认设置ip为`192.168.43.1`，同时提供了`192.168.1.1`的版本。

如果在高版本系统上不能工作，可能是因为目标包名不对，需要自行适配。

当然，你也可以选择使用不过滤包名版本（`non-filter`），毕竟`LSPosed`提供了作用域功能

## 作用域
系统框架

PS：保险起见，你也可以勾选包名包含`networkstack.tethering`的应用

## Todo
- [ ] 1. 增加配置页面，自定义ip和包名过滤开关

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

注意：由于该函数还被用于分配下游IP地址，所以需要先判断调用者（遍历堆栈即可），再进行替换。

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
