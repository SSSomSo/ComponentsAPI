# 构建说明

## ComponentsAPI 插件端（plugin/）

```bash
cd plugin
mvn clean package
```

产物：`plugin/target/ComponentsAPI-1.2.0.jar`

## ComponentsAPI 客户端模组（mod/）

首次克隆后需要生成 Gradle Wrapper JAR（仓库未包含二进制 wrapper jar）：

```bash
cd mod
gradle wrapper --gradle-version 9.7.0
```

然后构建：

```bash
./gradlew build
# Windows
gradlew.bat build
```

产物：`mod/build/libs/ComponentsAPI-mod-1.2.0.jar`

## 环境要求

- JDK 25
- Maven 3.9+（插件端）
- Gradle 9.7.0（首次生成 wrapper 用）
- Paper API 26.2.build.129-stable
- Fabric MC 26.2 / Loader 0.19.5 / FAPI 0.161.0+26.2

## 注意

- `mod/gradle/wrapper/gradle-wrapper.jar` 未纳入版本控制（二进制体积过大），首次构建前请运行 `gradle wrapper` 生成。
- 直连 github.com 可能被墙，建议配置镜像或代理。
