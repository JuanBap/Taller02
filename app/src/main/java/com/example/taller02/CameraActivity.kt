package com.example.taller02

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller02.databinding.ActivityCameraBinding
import com.example.taller02.databinding.ActivityContactsBinding
import java.io.File

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    private lateinit var uri : Uri

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(), ActivityResultCallback {
            loadImage(it!!)
        }
    )

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
        ActivityResultCallback {
            if(it){
                loadImage(uri)
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gallery.setOnClickListener{
            galleryLauncher.launch("image/*")
        }

        binding.camera.setOnClickListener{
            val file = File(getFilesDir(),"picFromCamera")
            uri = FileProvider.getUriForFile(baseContext,baseContext.packageName+".fileprovider",file)
            cameraLauncher.launch(uri)
        }
    }

    private fun loadImage(uri: Uri) {
        val imageStream = getContentResolver().openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(imageStream)
        binding.imageDisplay.setImageBitmap(bitmap)

    }
}