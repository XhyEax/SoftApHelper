# SoftApHelper (Xposed)

用于配置 Android 热点的 Xposed 模块。

## 功能

1. 自定义 Wi-Fi 热点 IPv4 地址（Android 9+）及以太网共享 IPv4 地址（Android 11+）。
2. 隐藏热点的计费网络标记（Android 10+）。
3. 自定义或随机生成热点 BSSID，并可选择每次开启热点时重新随机（Android 13+）。
4. 配置 5 GHz 热点的可选信道和最大带宽（Android 13+）。
5. 阻止 Wi-Fi 热点向客户端通告 IPv6（Android 11+，实验性功能，默认关闭）。

## 使用方法

1. 安装模块，在 LSPosed 中启用并设置下方的[作用域](#作用域)，然后重启手机。
2. 打开 SoftApHelper，设置热点 IP、BSSID、5 GHz 信道/带宽或 IPv6 选项，点击「保存」。
3. 关闭并重新开启热点，使新配置生效。页面中的「热点 IP（实际IP）」显示模块最近一次上报的 Wi-Fi 热点地址。

Wi-Fi 热点 IP 默认是 `192.168.1.1`，可选择 `192.168.43.1`、`172.20.10.1`、`172.31.10.1`，也可手动输入。以太网共享 IP 默认是 `192.168.45.1`，需切换到「以太网热点」后单独保存。只接受 `10.x.x.x`、`172.16-31.x.x` 或 `192.168.x.x` 的私有 IPv4 地址，末位不能为 `0` 或 `255`。

BSSID 默认是 `aa:bb:cc:dd:ee:ff`。关闭「替换热点MAC地址」后，热点使用系统生成的 BSSID。「随机」按钮生成一个新地址；「每次启动热点随机MAC地址」则会在每次开启热点时生成新地址。

5 GHz 信道可输入逗号分隔的列表，页面提供 `36,40,44` 和 `149,153,157,161,165` 两组快捷值；最大带宽可选 `20`、`40`、`80`、`160`、`320 MHz`。只有系统将热点设为 5 GHz，且未指定单个信道或 ACS 可选信道时，模块才会应用这些设置。实际信道和带宽仍受设备、地区和系统限制。

## 安卓14已知问题
部分安卓14系统由于存在缓存，需要手动**重新优化Tethering**，模块才能生效

LSPosed-模块-SoftApHelper-长按Tethering-重新优化-重启手机

[图片示例](https://xhy-1252675344.cos.ap-beijing.myqcloud.com/imgs/softap-redex.jpg)

## 注意

**网络前缀冲突**会导致网络连接失败（Android 10 及以下），或让系统继续使用随机 IP（Android 11 及以上，日志可能提示 `isConflictPrefix`）。发生冲突时，请在应用中改用其他私有网段。

Android 11+ 中，USB、蓝牙及 P2P 共享仍使用下表中的固定地址；应用内可配置的是 Wi-Fi 和以太网共享地址。

| 类型 | 默认 IP | 应用内可配置 |
| --- | --- | --- |
| Wi-Fi | 192.168.1.1 | 是 |
| 以太网 | 192.168.45.1 | Android 11+ |
| USB | 192.168.42.1 | 否 |
| 蓝牙 | 192.168.44.1 | 否 |
| P2P | 192.168.49.1 | 否 |

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
开启热点后，可在应用中查看最近一次上报的「实际IP」，也可在手机端使用 `ifconfig` 查看（或通过 USB 连接电脑后执行 `adb shell`）。还可以从连接热点的设备 `ping` 实际使用的 Wi-Fi 热点 IP。

如果插件未生效，作用域可尝试勾选更多包名包含`networkstack.tethering`的应用。

若仍未生效，请上传设备执行`ifconfig`的结果，以及`/apex/com.android.tethering/priv-app/`下的apk到[Issues](https://github.com/XhyEax/SoftApHelper/issues)。

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
Android 13+：在应用中启用「5G 锁定」，设置 5 GHz 信道列表和最大带宽，保存后重新开启热点。默认信道为 `149,153,157,161,165`，最大带宽为 `320 MHz`。如果系统已指定单个 5 GHz 信道或 ACS 可选信道，模块不会覆盖原配置。

### 方法2：使用VPNHotspot
使用[VPNHotspot](https://github.com/Mygod/VPNHotspot)设置系统热点配置。

安卓12及以下：指定AP频段为特定信道。

安卓13+：指定频段为5G，ACS可选频段为信道，或指定AP频段为特定信道。

手机重启后可能需要手动指定。


## 感谢
[@mmfmkuang](https://github.com/mmfmkuang)

[@dsfgdadg](https://github.com/dsfgdadg)
