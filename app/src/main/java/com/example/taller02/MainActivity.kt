package com.example.taller02

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.taller02.databinding.ActivityMainBinding
import org.w3c.dom.Attr

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonContacts.setOnClickListener{
            startActivity(Intent(baseContext, ContactsActivity::class.java))
        }

        binding.buttonCamera.setOnClickListener{
            startActivity(Intent(baseContext, CameraActivity::class.java))
        }

        binding.buttonCamera.setOnClickListener{
            startActivity(Intent(baseContext, CameraActivity::class.java))
        }

    }
}