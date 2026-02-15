package com.nfc.reader.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.nfc.reader.MainActivity
import com.nfc.reader.R
import com.nfc.reader.databinding.ActivitySplashBinding

/**
 * Premium Splash Screen for NFC PRO
 * Displays animated logo with slow rotation
 */
class SplashActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySplashBinding
    private val splashDuration = 2500L // 2.5 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Start animations
        startAnimations()
        
        // Navigate to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            // Add fade transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, splashDuration)
    }
    
    private fun startAnimations() {
        // Slow rotation animation for logo
        val rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_slow)
        binding.splashLogo.startAnimation(rotateAnimation)
        
        // Fade in animation for text
        val fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_scale)
        binding.splashAppName.startAnimation(fadeInAnimation)
        binding.splashTagline.startAnimation(fadeInAnimation)
    }
}
