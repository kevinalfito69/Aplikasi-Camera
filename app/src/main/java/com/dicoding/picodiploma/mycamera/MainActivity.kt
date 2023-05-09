package com.dicoding.picodiploma.mycamera

import android.Manifest
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dicoding.picodiploma.mycamera.api.ApiConfig
import com.dicoding.picodiploma.mycamera.api.FileUploadResponse
import com.dicoding.picodiploma.mycamera.databinding.ActivityMainBinding
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var getFile: File? = null

    companion object {
        const val CAMERA_X_RESULT = 200
        private const val MAXIMAL_SIZE = 1000000
        private val REQUIRED_PERMISSION = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSION = 10
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (!allPermissionGranted()) {
                Toast.makeText(this, "Tidak mendapatkan permission", Toast.LENGTH_SHORT).show()
                finish()
            }

        }

    }

    private fun allPermissionGranted() = REQUIRED_PERMISSION.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cameraXButton.setOnClickListener { startCameraX() }
        binding.cameraButton.setOnClickListener { startTakePhoto() }
        binding.galleryButton.setOnClickListener { startGallery() }
        binding.uploadButton.setOnClickListener { uploadImage() }

        if (!allPermissionGranted()) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSION,
                REQUEST_CODE_PERMISSION,
            )
        }
    }

    private lateinit var currentPhotoPath: String
    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            val myFile = File(currentPhotoPath)
            myFile.let { file ->
                getFile = file
                binding.previewImageView.setImageBitmap(BitmapFactory.decodeFile(file.path))

            }


        }
    }


    private fun startGallery() {
        val intent = Intent()
        intent.action = ACTION_GET_CONTENT
        intent.type = "image/*"
        val chooser = Intent.createChooser(intent, "Choose a Picture")
        launcherIntentGallery.launch(chooser)
    }

    private fun startTakePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(packageManager)
        createTempFile(application).also {
            val photoURI: Uri = FileProvider.getUriForFile(
                this@MainActivity,
                "com.dicoding.picodiploma.mycamera",
                it
            )
            currentPhotoPath = it.absolutePath
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            launcherIntentCamera.launch(intent)
        }
    }

    private fun startCameraX() {
        val intent = Intent(this, CameraActivity::class.java)
        launcherIntentCameraX.launch(intent)
    }

    private val launcherIntentCameraX = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == CAMERA_X_RESULT) {
            val myFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.data?.getSerializableExtra("picture", File::class.java)

            } else {
                @Suppress("DEPRECATION")
                it.data?.getSerializableExtra("picture")
            } as? File


            myFile?.let { file ->
                getFile = file
                binding.previewImageView.setImageBitmap(BitmapFactory.decodeFile(file.path))
            }

        }
    }
    private val launcherIntentGallery = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectImg = result.data?.data as Uri
            selectImg.let { uri ->
                val myFile = uriToFile(uri, this@MainActivity)
                getFile = myFile
                binding.previewImageView.setImageURI(uri)

            }
        }

    }

    private fun uploadImage() {
        if (getFile != null) {
            val file = reduceFileImage(getFile as File)
            val description = "Picture form Upload".toRequestBody("text/plain".toMediaType())
            val requestImageFile = file.asRequestBody("image/jpeg".toMediaType())
            val imageMultipart: MultipartBody.Part = MultipartBody.Part.createFormData(
                "photo",
                file.name,
                requestImageFile
            )
            val apiService = ApiConfig().getApiService()
            val uploadImageRequest = apiService.uploadImage(imageMultipart, description)
            uploadImageRequest.enqueue(object :
                Callback<FileUploadResponse> {
                override fun onResponse(
                    call: Call<FileUploadResponse>,
                    response: Response<FileUploadResponse>
                ) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody != null && !responseBody.error) {
                            Toast.makeText(
                                this@MainActivity,
                                responseBody.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, response.message(), Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                override fun onFailure(call: Call<FileUploadResponse>, t: Throwable) {
                    Toast.makeText(this@MainActivity, t.message, Toast.LENGTH_SHORT).show()
                }

            })
        } else {
            Toast.makeText(this@MainActivity, "Masukan gambar terlebih dahulu", Toast.LENGTH_SHORT)
                .show()
        }
    }
    private fun reduceFileImage(file: File):File{
        val bitmap = BitmapFactory.decodeFile(file.path)
        var compressQuality = 100
        var streamLength: Int
        do{
            val bmpStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, bmpStream)
            val bmpPicByteArray = bmpStream.toByteArray()
            streamLength = bmpPicByteArray.size
            compressQuality -= 5
        }while (streamLength > MAXIMAL_SIZE)
        bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, FileOutputStream(file))


        return file
    }


}