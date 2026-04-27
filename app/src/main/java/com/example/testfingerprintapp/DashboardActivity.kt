package com.example.testfingerprintapp

import android.content.Intent
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import com.example.testfingerprintapp.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    companion object {
        const val EXTRA_CREDENTIAL = "extra_credential"
        const val EXTRA_VISITOR_ID = "extra_visitor_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val credential = intent.getStringExtra(EXTRA_CREDENTIAL) ?: "User"
        val visitorId  = intent.getStringExtra(EXTRA_VISITOR_ID) ?: ""

        setupHeader(credential)
        setupGreeting(credential, visitorId)
        setupLogout()
    }

    private fun setupHeader(credential: String) {
        val initial = credential.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        binding.tvAvatar.text = initial
        binding.tvHeaderUsername.text = credential
    }

    private fun setupGreeting(credential: String, visitorId: String) {
        val greeting = if (visitorId.isNotEmpty())
            "Welcome back, $credential 👋"
        else
            "Welcome back, $credential 👋"

        binding.tvGreeting.text = greeting

        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val sub = buildString {
            append("Last login: $time")
            if (visitorId.isNotEmpty()) append("  ·  Visitor ID: $visitorId")
            append("  ·  Session secured with device fingerprinting")
        }
        binding.tvGreetingSub.text = sub
    }

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
