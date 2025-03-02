
# Android Performance Monitor 
BlockCanary是一个Android平台的一个非侵入式的性能监控组件，应用只需要实现一个抽象类，提供一些该组件需要的上下文环境，就可以在平时使用应用的时候检测主线程上的各种卡慢问题，并通过组件提供的各种信息分析出原因并进行修复。

# 包介绍

- blockcanary-android  记录block信息的核心实现
- blockcanary-no-op    空包，为了release打包时不编译进去

# 引入

**一般选取以下其中一个 case 引入即可**

**如果有多个buildTypes需求，请使用 ```buildTypeComple ``` 关键字根据buildTypes组合使用即可**

```gradle
dependencies {
    // 仅在debug包启用BlockCanary进行卡顿监控和提示的话，可以这么用
    debugImplementation 'com.github.yuchuangu85.AndroidPerformanceMonitor:blockcanary-android:v1.0'
    releaseImplementation 'com.github.yuchuangu85.AndroidPerformanceMonitor:blockcanary-android-no-op:v1.0'
}
```

PS: 由于该库使用了 `getMainLooper().setMessageLogging()`, 请确认是否与你的app冲突.

# 使用方法
在Application中：
```java
public class DemoApplication extends Application {
    @Override
    public void onCreate() {
        // 在主进程初始化调用哈
        BlockCanary.install(this, new AppBlockCanaryContext()).start();
    }
}
```

实现自己的监控上下文（强烈建议看清所有配置项，避免使用错误）：
```java
public class AppBlockCanaryContext extends BlockCanaryContext {
    // 实现各种上下文，包括应用标示符，用户uid，网络类型，卡慢判断阙值，Log保存位置等

    /**
     * Implement in your project.
     *
     * @return Qualifier which can specify this installation, like version + flavor.
     */
    public String provideQualifier() {
        return "unknown";
    }

    /**
     * Implement in your project.
     *
     * @return user id
     */
    public String provideUid() {
        return "uid";
    }

    /**
     * Network type
     *
     * @return {@link String} like 2G, 3G, 4G, wifi, etc.
     */
    public String provideNetworkType() {
        return "unknown";
    }

    /**
     * Config monitor duration, after this time BlockCanary will stop, use
     * with {@code BlockCanary}'s isMonitorDurationEnd
     *
     * @return monitor last duration (in hour)
     */
    public int provideMonitorDuration() {
        return -1;
    }

    /**
     * Config block threshold (in millis), dispatch over this duration is regarded as a BLOCK. You may set it
     * from performance of device.
     *
     * @return threshold in mills
     */
    public int provideBlockThreshold() {
        return 1000;
    }

    /**
     * Thread stack dump interval, use when block happens, BlockCanary will dump on main thread
     * stack according to current sample cycle.
     * <p>
     * Because the implementation mechanism of Looper, real dump interval would be longer than
     * the period specified here (especially when cpu is busier).
     * </p>
     *
     * @return dump interval (in millis)
     */
    public int provideDumpInterval() {
        return provideBlockThreshold();
    }

    /**
     * Path to save log, like "/blockcanary/", will save to sdcard if can.
     *
     * @return path of log files
     */
    public String providePath() {
        return "/blockcanary/";
    }

    /**
     * If need notification to notice block.
     * 如果不是通过通知则会通过log形式输出
     *
     * @return true if need, else if not need.
     */
    public boolean displayNotification() {
        return true;
    }

    /**
     * Implement in your project, bundle files into a zip file.
     *
     * @param src  files before compress
     * @param dest files compressed
     * @return true if compression is successful
     */
    public boolean zip(File[] src, File dest) {
        return false;
    }

    /**
     * Implement in your project, bundled log files.
     *
     * @param zippedFile zipped file
     */
    public void upload(File zippedFile) {
        throw new UnsupportedOperationException();
    }


    /**
     * Packages that developer concern, by default it uses process name,
     * put high priority one in pre-order.
     *
     * @return null if simply concern only package with process name.
     */
    public List<String> concernPackages() {
        return null;
    }

    /**
     * Filter stack without any in concern package, used with @{code concernPackages}.
     *
     * @return true if filter, false it not.
     */
    public boolean filterNonConcernStack() {
        return false;
    }

    /**
     * Provide white list, entry in white list will not be shown in ui list.
     *
     * @return return null if you don't need white-list filter.
     */
    public List<String> provideWhiteList() {
        LinkedList<String> whiteList = new LinkedList<>();
        whiteList.add("org.chromium");
        return whiteList;
    }

    /**
     * Whether to delete files whose stack is in white list, used with white-list.
     *
     * @return true if delete, false it not.
     */
    public boolean deleteFilesInWhiteList() {
        return true;
    }

    /**
     * Block interceptor, developer may provide their own actions.
     * 自定义log输出形式。
     */
    public void onBlock(Context context, BlockInfo blockInfo) {

    }
    
    // 设置输出log输出的TAG
    public String getTag() {
        return "TAG";
    }
}
```

# 功能及原理
见[BlockCanary — 轻松找出Android App界面卡顿元凶](http://blog.zhaiyifan.cn/2016/01/16/BlockCanaryTransparentPerformanceMonitor/).

或见下图
![flow](art/flow.png "flow")

# 如何分析log
除了图形界面可以供开发、测试阶段直接看卡顿原因外，更多的使用场景其实在于大范围的log采集和分析：如线上环境和monkey，或者测试同学们在整个测试阶段的log收集和分析。

对于分析，主要可以从以下维度
- 卡顿时间
- 同堆栈的卡顿出现次数
进行排序和归类。

接着说说对各个log分析的过程。
- 首先可以根据手机性能，如核数、机型、内存来判断对应耗时是不是应该判定为卡顿。如一些差的机器，或者内存本身不足的时候。
- 根据CPU情况，是否是app拿不到cpu，被其他应用拿走了。
- 看timecost和threadtimecost，如果两者差得很多，则是主线程被等待或者资源被抢占。
- 看卡顿发生前最近的几次堆栈，如果堆栈相同，则可以判定为是该处发生卡顿，否则需要比较分析。

# Demo工程
**请参考本项目下的demo module，点击三个按钮会触发对应的耗时事件，消息栏则会弹出block的notification，点击可以进去查看详细信息。**  
![Block detail](art/shot1.png "detail")
![Block list](art/shot2.png "list")



## Maven库配置：

root目录下gradle配置：

```gradle
buildscript {
    repositories {
        ...
        maven { url "https://jitpack.io" }
        maven { url "https://maven.google.com" }
    }
    dependencies {
        ...
        classpath 'com.github.dcendents:android-maven-gradle-plugin:1.4'
    }
}

allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
        maven { url "https://maven.google.com" }
    }
}
```

library下的gradle文件：

```
apply plugin: 'com.github.dcendents.android-maven'

// groupid 
group='com.github.codemx'
```

然后去Jitpack网址配置，生成库链接：

```
https://jitpack.io/
```

在log页面找到下面生成链接，复制到项目中使用：

```
Build artifacts:
com.github.yuchuangu85.AndroidPerformanceMonitor:blockcanary-android:v1.0
com.github.yuchuangu85.AndroidPerformanceMonitor:blockcanary-android-no-op:v1.0
```



# 协议

    Copyright (C) 2016 MarkZhai (http://zhaiyifan.cn).
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
