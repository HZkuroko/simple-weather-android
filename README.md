# NMC 天气 (安卓 12+)

一个极简的天气 App，数据来自中国气象网 nmc.cn。

## 功能
- 首次打开弹窗选择：**省份 → 城市**，以及**雷达地区**（全国/华北/东北/华东/华中/华南/西南/西北）。
- 顶部：当前气温、天气、体感、湿度、风、气压、空气。
- 中部：24 小时实况、未来 5 天预报。
- 底部：内嵌 nmc 对应地区的雷达页（带官方动图播放），可用右上角下拉或点城市名切换地区。
- **更新时机：仅“下拉刷新”与“冷启动（重新打开 App）”各更新 1 次，其余时间完全静默，无后台轮询。**
- 支持 WiFi 与移动数据（仅声明 INTERNET 权限）。

## 技术
- Kotlin，minSdk 31 (安卓 12)，targetSdk 34。
- 无重型依赖：网络用 HttpURLConnection，JSON 用内置 org.json，异步用协程。
- 城市内部 code 由接口自动获取（不需手写拼音）。

## 接口说明
- 省份：`https://www.nmc.cn/rest/province/all`
- 城市：`https://www.nmc.cn/rest/province/{省code}`（如 AGD）
- 天气：`https://www.nmc.cn/rest/weather?stationid={城市code}`
- 雷达页：`https://www.nmc.cn/publish/radar/{地区}.html`

## 如何构建 APK（两种方式）

### A. Android Studio（推荐，最简单）
1. 用 Android Studio（Hedgehog / Koala 或更新）选择 **Open**，打开本文件夹 `NMCWeather`。
2. 等 Gradle 同步完成（首次会自动下载依赖，需联网）。
3. 菜单 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。
4. 完成后点“locate”，得到 `app/build/outputs/apk/debug/app-debug.apk`，传到手机安装即可。

### B. 命令行
本工程未包含 gradle-wrapper.jar。若本机已装 Gradle 8.7+：
```bash
cd NMCWeather
gradle wrapper            # 生成 ./gradlew
./gradlew assembleDebug   # 需已装 Android SDK 并配置 ANDROID_HOME / local.properties
```
产物同样在 `app/build/outputs/apk/debug/`。

## 短临降水（和风天气 QWeather）

nmc.cn 本身不提供“未来分钟级降雨概率/降雨量曲线”，所以这个卡片用**和风天气**的分钟级降水接口实现。

### 注册并拿到 Key + API Host
1. 打开 https://dev.qweather.com 注册账号（免费）。
2. 进入控制台 → **项目管理** → 创建项目 → 创建**凭据**，凭据类型选 **API KEY**，复制这串 Key。
3. 在控制台“设置”里能看到你账号专属的 **API Host**（形如 `abcd1234.qweatherapi.com`）。
4. 打开 App，点顶部城市名，在弹窗**最底部**把 Key 和 API Host 粘进去，保存即可。

### 显示内容
- **未来 30 分钟**：根据前 6 个 5 分钟点判断是否有雨、强度，并给出一个**估算概率**（标注“估算”，非官方真实概率）。
- 官方 `summary` 文字提示（如“未来两小时不会下雨”），这是最权威的一句话结论。
- **未来 1 小时降水曲线**：每 5 分钟一个点（共 12 点），自绘轻量曲线，单位毫米。

### 准不准？
- 分钟级降水是**实况外推**，对“接下来 1~2 小时会不会下、大概什么时候下”比较准；时间越往后越不确定。
- 和风的分钟级预报覆盖中国大陆，逐 5 分钟更新，通常比 nmc 的“逐日/逐小时”预报更适合判断马上要不要带伞。
- App 里的百分比是我用降水量点位**估算**出来的，仅供参考；官方那句 `summary` 更可信。
- 免费“开发版”即可使用分钟级降水；主界面天气与雷达仍由 nmc 提供，二者互不影响。接口失败时只有这张卡片提示错误，不影响其余内容。

## 说明
- nmc.cn 为公益网站，请仅作个人/学习用途，不要高频拉取。
- 若将来 nmc 接口或页面结构变化，可能需相应调整解析代码。
