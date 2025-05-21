# SoftApHelper (Xposed)

SoftAp static server IP(v4) for Android 9+

SoftAp type hide for Android 10+

SoftAp 5G channel and bandwidth lock for Android 13+ 

## 功能
1. 固定IP地址 (Android 9+)
2. 隐藏热点类型 (Android 10+)
3. 锁定5G信道和频宽 (Android 13+)

## 安卓14已知问题：
部分安卓14系统由于存在缓存，需要手动**重新优化Tethering**，模块才能生效

LSPosed-模块-SoftApHelper-长按Tethering-重新优化-重启手机

[图片示例](https://xhy-1252675344.cos.ap-beijing.myqcloud.com/imgs/softap-redex.jpg)

## 注意
**网络前缀冲突**会导致网络连接失败（`Android 10`及以下）或仍使用随机IP（`Android 11`及以上，日志提示`isConflictPrefix`）。

wifi热点为`192.168.43.1`，同时提供了`192.168.1.1`版本（`43.1`**连不上的先试试这个**）。

支持设置`WIFI`、`USB`、`蓝牙`的热点IP（`Android 11`及以上）。


| Type      | IP                             |
|-----------|--------------------------------|
| USB       | 192.168.42.1                   |
| WIFI      | WIFI_HOST_IFACE_ADDR(43.1/1.1) |
| BlueTooth | 192.168.44.1                   |
| P2P       | 192.168.49.1                   |
| ETHERNET  | 192.168.45.1                   |

安卓13+开启5G热点时，如果未指定5G信道(未指定单个channel或者使用allowedAcsChannels)，模块将锁定频段为`149,153,157,161,165`，最大频宽为`320MHZ`(受硬件限制，实际可能只有`80MHZ`)。

如果需要锁定频段为其他范围（比如`36,40,44`），请使用[VPNHotspot](https://github.com/Mygod/VPNHotspot)，填写`5 GHz ACS 可选频段`。

## 下载
[Release](https://github.com/XhyEax/SoftApHelper/releases)

## 作用域
推荐使用`LSPosed`指定作用域（已配置推荐作用域）
### 安卓11及以下
系统框架

### 安卓12及以上（以及部分安卓11设备）
注意：高版本LSPosed勾选Tethering失败是正常现象，不影响插件生效

系统框架（一般只钩这个就可以了，勾选Tethering是保险起见）

`com.google.android.networkstack.tethering.inprocess`

`com.android.networkstack.tethering.inprocess`

`com.google.android.networkstack.tethering`

`com.android.networkstack.tethering`

## 连接测试&问题反馈
开启热点后，手机端使用`ifconfig`命令查看IP（或usb连接电脑后，进入`adb shell`执行）。或使用其他机器连接热点后，`ping 192.168.43.1`。

如果插件未生效，作用域可尝试勾选更多包名包含`networkstack.tethering`的应用。

若仍未生效，请上传设备执行`ifconfig`的结果，以及`/apex/com.android.tethering/priv-app/`下的apk到[Issues](https://github.com/XhyEax/SoftApHelper/issues)。

## 关于热点IP自定义
由于涉及配置保存，需要适配不同的安卓系统版本(系统层面限制)以及Xposed版本(API)，时间成本较高，该功能暂不考虑开发。

解决方法：自行使用MT管理器重编译dex，把192.168.43.1替换成目标IP

## 原理
[安卓9 固定Wifi热点IP (Xposed)](https://blog.xhyeax.com/2021/03/01/android-9-set-hotpot-ip/)

[安卓10、11 固定Wifi热点IP (Xposed)](https://blog.xhyeax.com/2021/12/06/android-10-11-hostpot-set-ip/)

[安卓12 固定Wifi热点IP (Xposed)](https://blog.xhyeax.com/2022/07/06/android-12-hostpot-set-ip/)

## 固定热点IP-Hook点
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
Hook点同安卓12

[IpServer.java#664](http://aospxref.com/android-13.0.0_r3/xref/packages/modules/Connectivity/Tethering/src/android/net/ip/IpServer.java#664)
```java
private LinkAddress requestIpv4Address(final boolean useLastAddress)
```

### 安卓14
Hook点同安卓12（参数有变化，但函数名没变）

[IpServer.java#684](http://aospxref.com/android-14.0.0_r2/xref/packages/modules/Connectivity/Tethering/src/android/net/ip/IpServer.java#684)
```java
private LinkAddress requestIpv4Address(final int scope, final boolean useLastAddress)
```

## 隐藏热点类型
`android.net.dhcp.DhcpServingParamsParcelExt`的`setMetered`函数。

```java
    /**
     * Set whether the DHCP server should send the ANDROID_METERED vendor-specific option.
     *
     * <p>If not set, the default value is false.
     */
    public DhcpServingParamsParcelExt setMetered(boolean metered) {
        this.metered = metered;
        return this;
    }
```

### 安卓15
Hook点同安卓14

## 固定5G热点信道
### 方法1：使用本插件
（TODO）安卓12及以下：指定AP频段为特定信道。

安卓13+：如果开启5G热点时，未指定5G信道(单个channel或者allowedAcsChannels)，锁定频段为`149,153,157,161,165`，频宽为`320MHZ`(受硬件限制，实际可能只有`80MHZ`)。

### 方法2：使用VPNHotspot
使用[VPNHotspot](https://github.com/Mygod/VPNHotspot)设置系统热点配置。

安卓12及以下：指定AP频段为特定信道。

安卓13+：指定频段为5G，ACS可选频段为信道，或指定AP频段为特定信道。

手机重启后可能需要手动指定。


## 感谢
[@mmfmkuang](https://github.com/mmfmkuang)

[@dsfgdadg](https://github.com/dsfgdadg)
