package net.tripro.videorecorder

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import kotlinx.android.synthetic.main.activity_main.*
import net.tripro.videorecorder.databinding.ActivityMainBinding
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {

    private var flashEnabled: Boolean = false
    private var lens = CameraSelector.LENS_FACING_BACK
    private lateinit var binding: ActivityMainBinding
    private var timeInMilliseconds = 0L
    private var startTime: Long = SystemClock.uptimeMillis()
    private var updatedTime: Long = 0L
    private var timeSwapBuff: Long = 0L
    private val customHandler = Handler(Looper.getMainLooper())

    var isRecording = false
    var CAMERA_PERMISSION = Manifest.permission.CAMERA
    var RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    val TAG = MainActivity::class.java.simpleName

    var RC_PERMISSION = 101

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)


        val recordFiles = ContextCompat.getExternalFilesDirs(this, Environment.DIRECTORY_MOVIES)
        val storageDirectory = recordFiles[0]
        val videoRecordingFilePath =
            "${storageDirectory.absoluteFile}/${System.currentTimeMillis()}_video.mp4"
        val imageCaptureFilePath =
            "${storageDirectory.absoluteFile}/${System.currentTimeMillis()}_image.jpg"

        if (checkPermissions()) startCameraSession() else requestPermissions()

        binding.imgCapture.alpha = 1.0f
        binding.imgCapture.setOnLongClickListener(OnLongClickListener {
            binding.hintTextView.visibility = View.INVISIBLE
            try {
                if (!isRecording) {
                    isRecording = true
                    Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show()
                    startTime = SystemClock.uptimeMillis()
                    customHandler.postDelayed(updateTimerThread, 0)
                    recordVideo(videoRecordingFilePath)
                } else {
                    return@OnLongClickListener false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            binding.textCounter.visibility = View.VISIBLE
            binding.imgChangeCamera.setVisibility(View.GONE)
            binding.imgFlashOnOff.visibility = View.GONE
            scaleUpAnimation()
            binding.imgCapture.setOnTouchListener(OnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS) {
                    return@OnTouchListener true
                }
                if (event.action == MotionEvent.ACTION_UP) {
                    scaleDownAnimation()
                    binding.hintTextView.visibility = View.VISIBLE
                    if (isRecording) {
                        isRecording = false
                        Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show()
                        binding.cameraView.stopRecording()
                        saveVideo()
                    }
                    return@OnTouchListener true
                }
                true
            })
            true
        })


        binding.imgCapture.setOnClickListener { captureImage(imageCaptureFilePath) }

        binding.imgChangeCamera.setOnClickListener { switchCamera() }

        binding.imgFlashOnOff.setOnClickListener { toggleFlash() }

    }

    private fun saveVideo() {
        binding.textCounter.visibility = View.GONE
        binding.imgChangeCamera.visibility = View.VISIBLE
        binding.imgFlashOnOff.visibility = View.VISIBLE
    }

    private fun scaleUpAnimation() {
        val scaleDownX = ObjectAnimator.ofFloat(imgCapture, "scaleX", 2f)
        val scaleDownY = ObjectAnimator.ofFloat(imgCapture, "scaleY", 2f)
        scaleDownX.duration = 100
        scaleDownY.duration = 100
        val scaleDown = AnimatorSet()
        scaleDown.play(scaleDownX).with(scaleDownY)
        scaleDownX.addUpdateListener {
            val p = imgCapture.parent as View
            p.invalidate()
        }
        scaleDown.start()
    }

    private fun scaleDownAnimation() {
        val scaleDownX = ObjectAnimator.ofFloat(imgCapture, "scaleX", 1f)
        val scaleDownY = ObjectAnimator.ofFloat(imgCapture, "scaleY", 1f)
        scaleDownX.duration = 100
        scaleDownY.duration = 100
        val scaleDown = AnimatorSet()
        scaleDown.play(scaleDownX).with(scaleDownY)
        scaleDownX.addUpdateListener {
            val p = imgCapture.parent as View
            p.invalidate()
        }
        scaleDown.start()
    }

    private val updateTimerThread: Runnable by lazy {
        object : Runnable {
            override fun run() {
                timeInMilliseconds = SystemClock.uptimeMillis() - startTime
                updatedTime = timeSwapBuff + timeInMilliseconds
                var secs = (updatedTime / 1000).toInt()
                val mins = secs / 60
                val hrs = mins / 60
                secs = secs % 60
                textCounter.text =
                    String.format("%02d", mins) + ":" + String.format("%02d", secs)
                customHandler.postDelayed(this, 0)
            }
        }
    }

    private fun toggleFlash() {
        if (!flashEnabled) {
            flashEnabled = true
            binding.cameraView.enableTorch(flashEnabled)
            binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_on)
        } else {
            flashEnabled = false
            binding.cameraView.enableTorch(flashEnabled)
            binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_off)
        }
    }

    private fun switchCamera() {
        when (lens) {
            CameraSelector.LENS_FACING_BACK -> {
                lens = CameraSelector.LENS_FACING_FRONT
                toggleFlash()
                binding.imgFlashOnOff.visibility = View.GONE
            }
            CameraSelector.LENS_FACING_FRONT -> {
                lens = CameraSelector.LENS_FACING_BACK
                binding.imgFlashOnOff.visibility = View.VISIBLE
            }
        }
        binding.cameraView.cameraLensFacing = lens
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(CAMERA_PERMISSION, RECORD_AUDIO_PERMISSION),
            RC_PERMISSION
        )
    }

    private fun checkPermissions(): Boolean {
        return ((ActivityCompat.checkSelfPermission(
            this,
            CAMERA_PERMISSION
        )) == PackageManager.PERMISSION_GRANTED
                && (ActivityCompat.checkSelfPermission(
            this,
            CAMERA_PERMISSION
        )) == PackageManager.PERMISSION_GRANTED)
    }

    private fun startCameraSession() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        binding.cameraView.bindToLifecycle(this)
    }

    private fun permissionsNotGranted() {
        AlertDialog.Builder(this).setTitle("Permissions required")
            .setMessage("These permissions are required to use this app. Please allow Camera and Audio permissions first")
            .setCancelable(false)
            .setPositiveButton("Grant") { dialog, which -> requestPermissions() }
            .show()
    }

    private fun recordVideo(videoRecordingFilePath: String) {
        binding.cameraView.startRecording(
            File(videoRecordingFilePath),
            ContextCompat.getMainExecutor(this),
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(file: File) {
                    Toast.makeText(this@MainActivity, "Recording Saved", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "onVideoSaved $videoRecordingFilePath")
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Toast.makeText(this@MainActivity, "Recording Failed", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "onError $videoCaptureError $message")
                }
            })
    }

    private fun captureImage(imageCaptureFilePath: String) {
        binding.cameraView.takePicture(
            File(imageCaptureFilePath),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "Image Captured", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "onImageSaved $imageCaptureFilePath")
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Image Capture Failed", Toast.LENGTH_SHORT)
                        .show()
                    Log.e(TAG, "onError $exception")
                }
            })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RC_PERMISSION -> {
                var allPermissionsGranted = false
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false
                        break
                    } else {
                        allPermissionsGranted = true
                    }
                }
                if (allPermissionsGranted) startCameraSession() else permissionsNotGranted()
            }
        }
    }

}