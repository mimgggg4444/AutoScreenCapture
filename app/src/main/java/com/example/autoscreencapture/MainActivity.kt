package com.example.autoscreencapture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private var isServiceRunning = false

    // 저장소 권한 요청 (Android 10 미만)
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        android.util.Log.d("MainActivity", "저장소 권한 결과: $permissions")
        if (allGranted) {
            android.util.Log.d("MainActivity", "저장소 권한 모두 허용됨")
            checkNotificationPermission()
        } else {
            android.util.Log.e("MainActivity", "저장소 권한 거부됨")
            Toast.makeText(this, "저장소 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    // MediaProjection 권한 요청
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("MainActivity", "screenCaptureLauncher - resultCode: ${result.resultCode}, data: ${result.data}")
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                android.util.Log.d("MainActivity", "MediaProjection 권한 획득 성공")
                startScreenshotService(result.resultCode, data)
            } else {
                android.util.Log.e("MainActivity", "data가 null입니다")
                Toast.makeText(this, "데이터 전달 오류", Toast.LENGTH_SHORT).show()
            }
        } else {
            android.util.Log.e("MainActivity", "스크린 캡처 권한 거부됨")
            Toast.makeText(this, "스크린 캡처 권한이 거부되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    // 알림 권한 요청 (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("MainActivity", "알림 권한 결과: $isGranted")
        if (isGranted) {
            android.util.Log.d("MainActivity", "알림 권한 허용됨")
            requestScreenCapturePermission()
        } else {
            android.util.Log.e("MainActivity", "알림 권한 거부됨")
            Toast.makeText(this, "알림 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        android.util.Log.d("MainActivity", "onCreate() - Android SDK: ${Build.VERSION.SDK_INT}")

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)

        updateUI()

        btnStart.setOnClickListener {
            android.util.Log.d("MainActivity", "시작 버튼 클릭")
            checkPermissionsAndStart()
        }

        btnStop.setOnClickListener {
            android.util.Log.d("MainActivity", "중지 버튼 클릭")
            stopScreenshotService()
        }
    }

    private fun checkPermissionsAndStart() {
        android.util.Log.d("MainActivity", "checkPermissionsAndStart() - SDK: ${Build.VERSION.SDK_INT}")

        // Android 10 미만에서는 저장소 권한 필요
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            android.util.Log.d("MainActivity", "Android 9 이하 - 저장소 권한 확인")
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val needsPermission = permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (needsPermission) {
                android.util.Log.d("MainActivity", "저장소 권한 요청")
                storagePermissionLauncher.launch(permissions)
                return
            }
        } else {
            android.util.Log.d("MainActivity", "Android 10 이상 - 저장소 권한 불필요 (MediaStore 사용)")
        }

        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        // Android 13 이상에서는 알림 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("MainActivity", "Android 13+ - 알림 권한 확인")
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    android.util.Log.d("MainActivity", "알림 권한 이미 허용됨")
                    requestScreenCapturePermission()
                }
                else -> {
                    android.util.Log.d("MainActivity", "알림 권한 요청")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            android.util.Log.d("MainActivity", "Android 12 이하 - 알림 권한 불필요")
            requestScreenCapturePermission()
        }
    }

    private fun requestScreenCapturePermission() {
        android.util.Log.d("MainActivity", "requestScreenCapturePermission() - MediaProjection 권한 요청")
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startScreenshotService(resultCode: Int, data: Intent?) {
        android.util.Log.d("MainActivity", "startScreenshotService() - resultCode: $resultCode")

        if (data == null) {
            android.util.Log.e("MainActivity", "Intent data가 null입니다!")
            Toast.makeText(this, "데이터 오류", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, ScreenshotService::class.java).apply {
            putExtra("resultCode", resultCode)
            // Intent의 extras를 Bundle로 전달
            putExtra("dataExtras", data.extras)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
                android.util.Log.d("MainActivity", "startForegroundService 호출")
            } else {
                startService(serviceIntent)
                android.util.Log.d("MainActivity", "startService 호출")
            }

            isServiceRunning = true
            updateUI()
            Toast.makeText(this, "스크린샷 캡처 시작", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "서비스 시작 실패", e)
            Toast.makeText(this, "서비스 시작 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopScreenshotService() {
        android.util.Log.d("MainActivity", "stopScreenshotService() 호출")
        val serviceIntent = Intent(this, ScreenshotService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false
        updateUI()
        Toast.makeText(this, "스크린샷 캡처 중지", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        if (isServiceRunning) {
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            tvStatus.text = "상태: 캡처 중..."
            android.util.Log.d("MainActivity", "UI 업데이트: 캡처 중")
        } else {
            btnStart.isEnabled = true
            btnStop.isEnabled = false
            tvStatus.text = "상태: 대기 중"
            android.util.Log.d("MainActivity", "UI 업데이트: 대기 중")
        }
    }
}