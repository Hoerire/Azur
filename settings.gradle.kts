// 仓库顺序铁律：国内镜像优先，google() 只作兜底。
// 实测本机 dl.google.com 三次探测两次 000（连不上），原来 google() 写在最前面 → 每次都先撞墙再回退。
// chaquo.com/maven 已删：com.chaquo.python:gradle:17.0.0 在 mavenCentral 与 aliyun 都能取到（实测 200），
// 而 chaquo.com 本身 404/000，留着只会拖慢解析。
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
        gradlePluginPortal()
        google()   // 仅兜底
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
        google()   // 仅兜底
    }
}
rootProject.name = "Anvil"
include(":app")
include(":ui-kit")
include(":remote-view")
include(":store")
include(":agent-runtime")

// 本地配置读取口：给 app 模块以外的模块按需依赖，见 xsettings/XSettings.kt 的「懒」规则。
include(":xsettings")

// Business 层：契约（api）与实现（agent）分离，业务方只依赖 api。
include(":business:api")
include(":business:agent")
include(":business:files")
include(":business:notification")
include(":business:application")
include(":business:permission")

// Vendored libraries (see libs/README.md)
include(":libs:logging")
include(":libs:okia")
include(":libs:libterm-core")
project(":libs:libterm-core").projectDir = file("libs/libterm/libterm-core")
include(":libs:libterm-runtime")
project(":libs:libterm-runtime").projectDir = file("libs/libterm/libterm-runtime")
include(":libs:libterm-backend-libsu")
project(":libs:libterm-backend-libsu").projectDir = file("libs/libterm/libterm-backend-libsu")
include(":libs:libterm-backend-shizuku")
project(":libs:libterm-backend-shizuku").projectDir = file("libs/libterm/libterm-backend-shizuku")
include(":libs:libterm-backend-ssh")
project(":libs:libterm-backend-ssh").projectDir = file("libs/libterm/libterm-backend-ssh")
