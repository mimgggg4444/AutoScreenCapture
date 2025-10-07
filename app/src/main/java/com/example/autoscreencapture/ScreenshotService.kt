package com.example.autoscreencapture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class ScreenshotService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var screenshotCount = 0
    private var width = 0
    private var height = 0

    private val screenshotRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "screenshotRunnable 실행 - 스크린샷 캡처 시도")
            captureScreenshot()
            handler.postDelayed(this, 5000) // 5초마다 실행
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenshotService onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ScreenshotService onStartCommand() 시작")

        // 즉시 Foreground Service로 시작
        startForegroundService()

        // Intent에서 데이터 추출
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val dataExtras = intent?.getBundleExtra("dataExtras")

        Log.d(TAG, "받은 데이터 - resultCode: $resultCode, dataExtras: $dataExtras")

        if (resultCode != 0 && dataExtras != null) {
            // Bundle을 다시 Intent로 변환
            val mediaProjectionIntent = Intent().apply {
                putExtras(dataExtras)
            }

            Log.d(TAG, "MediaProjection Intent 재구성 완료")

            // MediaProjection 설정
            if (setupMediaProjection(resultCode, mediaProjectionIntent)) {
                Log.d(TAG, "MediaProjection 설정 성공, 5초 후 첫 캡처 시작")
                handler.postDelayed(screenshotRunnable, 5000) // 첫 캡처는 5초 후
            } else {
                Log.e(TAG, "MediaProjection 설정 실패")
                stopSelf()
            }
        } else {
            Log.e(TAG, "잘못된 Intent 데이터 - resultCode: $resultCode, dataExtras: $dataExtras")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        Log.d(TAG, "startForegroundService() 호출")

        val channelId = "screenshot_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "스크린샷 캡처 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("자동 스크린샷")
            .setContentText("5초마다 스크린샷을 캡처합니다")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
        Log.d(TAG, "Foreground 서비스 시작 완료")
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent): Boolean {
        return try {
            Log.d(TAG, "setupMediaProjection() 시작")

            val mediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            Log.d(TAG, "MediaProjection 객체 생성: $mediaProjection")

            // MediaProjection Callback 등록 (Android 14+ 필수)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    handler.removeCallbacks(screenshotRunnable)
                    stopSelf()
                }
            }, handler)

            Log.d(TAG, "MediaProjection Callback 등록 완료")

            // Service에서는 WindowManager의 defaultDisplay를 사용
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()

            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)

            width = displayMetrics.widthPixels
            height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi

            Log.d(TAG, "화면 정보 - width: $width, height: $height, density: $density")

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            Log.d(TAG, "ImageReader 생성 완료")

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            Log.d(TAG, "VirtualDisplay 생성 완료: $virtualDisplay")

            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 설정 실패", e)
            false
        }
    }

    private fun captureScreenshot() {
        try {
            Log.d(TAG, "captureScreenshot() 시작")

            val image: Image? = imageReader?.acquireLatestImage()

            if (image == null) {
                Log.e(TAG, "Image가 null입니다")
                return
            }

            Log.d(TAG, "Image 획득 성공 - width: ${image.width}, height: ${image.height}")

            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            Log.d(TAG, "Bitmap 생성 완료")

            val saved = saveScreenshot(bitmap)
            if (saved) {
                screenshotCount++
                updateNotification(screenshotCount)
                Log.d(TAG, "✅ 스크린샷 저장 성공: $screenshotCount")
            } else {
                Log.e(TAG, "❌ 스크린샷 저장 실패")
            }
        } catch (e: Exception) {
            Log.e(TAG, "스크린샷 캡처 중 오류 발생", e)
        }
    }

    private fun saveScreenshot(bitmap: Bitmap): Boolean {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Screenshot_$timestamp.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 이상 - MediaStore 사용
                Log.d(TAG, "MediaStore API 사용 (Android 10+)")
                saveWithMediaStore(bitmap, fileName)
            } else {
                // Android 9 이하 - 직접 파일 저장
                Log.d(TAG, "직접 파일 저장 (Android 9 이하)")
                saveToExternalStorage(bitmap, fileName)
            }

            bitmap.recycle()
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveScreenshot 실패", e)
            false
        }
    }

    private fun saveWithMediaStore(bitmap: Bitmap, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AutoScreenshots")
        }

        val uri: Uri? = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
            Log.d(TAG, "MediaStore 저장 완료: $uri")
        } ?: Log.e(TAG, "MediaStore URI 생성 실패")
    }

    private fun saveToExternalStorage(bitmap: Bitmap, fileName: String) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "AutoScreenshots"
        )

        if (!directory.exists()) {
            directory.mkdirs()
            Log.d(TAG, "디렉토리 생성: ${directory.absolutePath}")
        }

        val file = File(directory, fileName)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }

        Log.d(TAG, "파일 저장 완료: ${file.absolutePath}")
    }

    private fun updateNotification(count: Int) {
        val channelId = "screenshot_channel"
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("자동 스크린샷")
            .setContentText("캡처된 스크린샷: ${count}장")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "ScreenshotService onDestroy()")
        handler.removeCallbacks(screenshotRunnable)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    companion object {
        private const val TAG = "ScreenshotService"
    }
}