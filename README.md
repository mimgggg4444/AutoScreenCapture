
> Android 앱에서 5초마다 자동으로 스크린샷을 캡처하는 애플리케이션

AutoScreenCapture는 사용자의 화면을 5초 간격으로 자동 캡처하여 저장하는 Android 앱입니다. MediaProjection API를 활용하여 백그라운드에서 안정적으로 작동하며, 캡처된 이미지는 자동으로 갤러리에 저장됩니다.

## ✨ 주요 기능

- ⏱️ **자동 캡처**: 5초 간격으로 자동 스크린샷 촬영
- 🔄 **백그라운드 실행**: Foreground Service를 통한 안정적인 백그라운드 동작
- 📁 **자동 저장**: MediaStore API를 사용한 갤러리 자동 저장
- 🔔 **실시간 알림**: 캡처된 스크린샷 개수를 알림창에 실시간 표시

## 🛠️ 기술 스택

### Language & Tools
- **Kotlin** - 메인 개발 언어
- **Android Studio** - IDE
- **Gradle (Kotlin DSL)** - 빌드 도구

### Android 버전
- **Minimum SDK**: API 21 (Android 5.0 Lollipop)
- **Target SDK**: API 34 (Android 14)
- **Compile SDK**: API 34

### 주요 라이브러리
```gradle
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}
```

## 🔑 사용된 주요 Android API

### 1. MediaProjection API
화면 캡처를 위한 핵심 API
```kotlin
// MediaProjection 인스턴스 생성
val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

// Callback 등록 (Android 14+ 필수)
mediaProjection.registerCallback(callback, handler)

// VirtualDisplay 생성
val virtualDisplay = mediaProjection.createVirtualDisplay(
    name, width, height, density,
    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
    surface, null, null
)
```

### 2. ImageReader
스크린 이미지 캡처
```kotlin
val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
val image = imageReader.acquireLatestImage()
```

### 3. MediaStore API (Android 10+)
갤러리에 이미지 저장
```kotlin
val contentValues = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AutoScreenshots")
}
val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
```

### 4. Foreground Service
백그라운드 안정성 보장
```kotlin
startForeground(notificationId, notification)
```

### 5. Handler & Runnable
주기적 작업 실행
```kotlin
private val handler = Handler(Looper.getMainLooper())
private val screenshotRunnable = object : Runnable {
    override fun run() {
        captureScreenshot()
        handler.postDelayed(this, 5000) // 5초 간격
    }
}
```

## 📋 필요 권한

### AndroidManifest.xml
```xml
<!-- Foreground Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<!-- 저장소 권한 (Android 9 이하) -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- 알림 권한 (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## 🚀 설치 및 실행

### 1. 프로젝트 클론
```bash
git clone https://github.com/yourusername/AutoScreenCapture.git
cd AutoScreenCapture
```

### 2. Android Studio에서 열기
- Android Studio 실행
- `Open an existing project` 선택
- 클론한 프로젝트 폴더 선택

### 3. Gradle Sync
- 프로젝트가 열리면 자동으로 Gradle Sync 실행
- 또는 `File` → `Sync Project with Gradle Files`

### 4. 빌드 및 실행
- 에뮬레이터 또는 실제 디바이스 연결
- `Run` → `Run 'app'` (Shift + F10)

## 📱 사용 방법

1. **앱 실행**
2. **"시작" 버튼** 클릭
3. **권한 허용**:
   - 알림 권한 허용 (Android 13+)
   - MediaProjection 권한: "지금 시작" 클릭
4. **자동 캡처 시작** - 5초마다 스크린샷이 자동으로 저장됩니다
5. **"중지" 버튼**으로 캡처 중지

### 📂 저장 위치
- **경로**: `Pictures/AutoScreenshots/`
- **파일명**: `Screenshot_YYYYMMDD_HHMMSS.png`
- **확인 방법**: 갤러리 앱 → 앨범 → AutoScreenshots

## 🏗️ 프로젝트 구조

```
AutoScreenCapture/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/autoscreencapture/
│   │       │   ├── MainActivity.kt              # 메인 UI 및 권한 관리
│   │       │   └── ScreenshotService.kt         # 스크린샷 캡처 서비스
│   │       ├── res/
│   │       │   └── layout/
│   │       │       └── activity_main.xml        # UI 레이아웃
│   │       └── AndroidManifest.xml              # 앱 설정 및 권한
│   └── build.gradle.kts                         # 앱 레벨 빌드 설정
└── build.gradle.kts                             # 프로젝트 레벨 빌드 설정
```

## 🔧 주요 함수 설명

### MainActivity.kt

| 함수명 | 설명 |
|--------|------|
| `checkPermissionsAndStart()` | 필요한 권한 확인 및 요청 |
| `requestScreenCapturePermission()` | MediaProjection 권한 요청 |
| `startScreenshotService()` | 스크린샷 서비스 시작 |
| `stopScreenshotService()` | 스크린샷 서비스 중지 |

### ScreenshotService.kt

| 함수명 | 설명 |
|--------|------|
| `onStartCommand()` | 서비스 시작 시 초기화 |
| `startForegroundService()` | Foreground Service 및 알림 생성 |
| `setupMediaProjection()` | MediaProjection 및 VirtualDisplay 설정 |
| `captureScreenshot()` | 스크린샷 캡처 및 Bitmap 변환 |
| `saveScreenshot()` | 이미지 저장 (MediaStore 또는 파일 시스템) |
| `updateNotification()` | 알림 업데이트 (캡처 횟수 표시) |

## ⚙️ 커스터마이징

### 캡처 간격 변경
`ScreenshotService.kt`의 다음 부분 수정:
```kotlin
handler.postDelayed(this, 5000) // 5000 = 5초 (밀리초 단위)
```

**예시**:
- 10초 간격: `10000`
- 1초 간격: `1000`
- 30초 간격: `30000`

### 이미지 품질 변경
```kotlin
bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream) // 100 = 최고 품질
```

### 저장 위치 변경
```kotlin
put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YourFolderName")
```

## ⚠️ 주의사항

1. **배터리 소모**: 백그라운드에서 지속적으로 스크린샷을 캡처하므로 배터리 소모가 있을 수 있습니다
2. **저장 공간**: 5초마다 스크린샷이 저장되므로 충분한 저장 공간이 필요합니다
3. **개인정보**: 화면의 모든 내용이 캡처되므로 개인정보 보호에 유의하세요
4. **배터리 최적화**: 일부 기기에서는 배터리 최적화 설정에서 앱을 제외해야 안정적으로 작동합니다

## 🐛 알려진 이슈

- 일부 삼성 기기에서 백그라운드 제한으로 인해 캡처가 중단될 수 있습니다
  - **해결**: 설정 → 배터리 → 백그라운드 사용 제한 해제


## 🤝 기여하기

Pull Request는 언제나 환영합니다!

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request


<img width="1080" height="2400" alt="Screenshot_20251007_163919" src="https://github.com/user-attachments/assets/ac81529e-d4db-48d8-8a7b-24fea55df91f" />
