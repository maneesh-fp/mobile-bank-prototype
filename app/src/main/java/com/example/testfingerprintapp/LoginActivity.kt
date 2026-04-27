package com.example.testfingerprintapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.testfingerprintapp.databinding.ActivityLoginBinding
import com.fingerprintjs.android.fpjs_pro.Configuration
import com.fingerprintjs.android.fpjs_pro.FingerprintException
import com.fingerprintjs.android.fpjs_pro.FingerprintJS
import com.fingerprintjs.android.fpjs_pro.FingerprintJSFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var fpClient: FingerprintJS
    private val httpClient = OkHttpClient()
    private var activeTab = Tab.CUSTOMER_ID

    private enum class Tab { CUSTOMER_ID, MOBILE }

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initFingerprintClient()
        setupTabSwitcher()
        setupClickListeners()
    }

    private fun initFingerprintClient() {
        fpClient = FingerprintJSFactory(applicationContext)
            .createInstance(
                Configuration(
                    apiKey = BuildConfig.FINGERPRINT_API_KEY,
                    region = Configuration.Region.AP
                )
            )
    }

    // ── Tab switcher ──────────────────────────────────────────────────────────

    private fun setupTabSwitcher() {
        binding.tabCustomerId.setOnClickListener { selectTab(Tab.CUSTOMER_ID) }
        binding.tabMobile.setOnClickListener { selectTab(Tab.MOBILE) }
    }

    private fun selectTab(tab: Tab) {
        activeTab = tab
        val isCustomerId = tab == Tab.CUSTOMER_ID

        binding.tabCustomerId.apply {
            setBackgroundResource(if (isCustomerId) R.drawable.bg_tab_active else android.R.color.transparent)
            setTextColor(getColor(if (isCustomerId) R.color.sb_text_primary else R.color.sb_text_secondary))
            setTypeface(null, if (isCustomerId) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        binding.tabMobile.apply {
            setBackgroundResource(if (!isCustomerId) R.drawable.bg_tab_active else android.R.color.transparent)
            setTextColor(getColor(if (!isCustomerId) R.color.sb_text_primary else R.color.sb_text_secondary))
            setTypeface(null, if (!isCustomerId) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

        binding.tilCustomerId.hint = getString(
            if (isCustomerId) R.string.label_customer_id else R.string.tab_mobile
        )
        binding.etCustomerId.inputType = if (isCustomerId)
            android.text.InputType.TYPE_CLASS_TEXT
        else
            android.text.InputType.TYPE_CLASS_PHONE

        binding.tilCustomerId.error = null
        binding.tilPassword.error = null
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSignIn.setOnClickListener { onSignInClicked() }
        binding.btnOtp.setOnClickListener { }
        binding.tvForgotPassword.setOnClickListener { }
    }

    // ── Sign-in flow ──────────────────────────────────────────────────────────

    private fun onSignInClicked() {
        if (!validateInputs()) return
        val credential = binding.etCustomerId.text?.toString()?.trim() ?: return
        val password = binding.etPassword.text?.toString() ?: return
        setLoadingState(true)

        lifecycleScope.launch {
            try {
                // Step 1: Fingerprint device identification
                val fpResponse = withContext(Dispatchers.IO) {
                    fpClient.getVisitorId(
                        linkedId = credential,
                        tags = mapOf("flow" to "login"),
                        timeoutMillis = 10_000
                    )
                }
                Log.d(TAG, "requestId=${fpResponse.requestId}  sealedResult=${fpResponse.sealedResult?.take(40)}…")

                // Step 2: POST /api/login with credentials + fingerprint data
                val visitorId = withContext(Dispatchers.IO) {
                    postLogin(credential, password, fpResponse.sealedResult, fpResponse.requestId)
                }

                navigateToDashboard(credential, visitorId)

            } catch (e: FingerprintException) {
                Log.e(TAG, "Fingerprint error: ${e.error.description}")
                showSnackbar("Could not verify device: ${e.error.description ?: "Unknown error"}")
            } catch (e: Exception) {
                Log.e(TAG, "Login error: ${e.message}")
                showSnackbar(e.message ?: "Login failed. Please try again.")
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun postLogin(
        credential: String,
        password: String,
        sealedResult: String?,
        requestId: String
    ): String {
        val body = JSONObject().apply {
            put("credential", credential)
            put("password", password)
            put("requestId", requestId)
            if (sealedResult != null) put("sealedResult", sealedResult)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL}/api/login")
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from server")

        Log.d(TAG, "POST /api/login ${response.code}: $responseBody")

        val json = JSONObject(responseBody)
        if (!response.isSuccessful || !json.optBoolean("success")) {
            throw Exception(json.optString("error", "Login failed"))
        }

        return json.optString("visitorId", "")
    }

    private fun navigateToDashboard(credential: String, visitorId: String) {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra(DashboardActivity.EXTRA_CREDENTIAL, credential)
            putExtra(DashboardActivity.EXTRA_VISITOR_ID, visitorId)
        }
        startActivity(intent)
        finish()
    }

    // ── Input validation ──────────────────────────────────────────────────────

    private fun validateInputs(): Boolean {
        var valid = true

        val identifier = binding.etCustomerId.text?.toString()?.trim()
        if (identifier.isNullOrEmpty()) {
            binding.tilCustomerId.error = when (activeTab) {
                Tab.CUSTOMER_ID -> "Please enter your Customer ID"
                Tab.MOBILE -> "Please enter your mobile number"
            }
            valid = false
        } else {
            binding.tilCustomerId.error = null
        }

        val password = binding.etPassword.text?.toString()
        if (password.isNullOrEmpty()) {
            binding.tilPassword.error = "Please enter your password"
            valid = false
        } else {
            binding.tilPassword.error = null
        }

        return valid
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun setLoadingState(loading: Boolean) {
        binding.btnSignIn.isEnabled = !loading
        binding.btnSignIn.text = if (loading)
            getString(R.string.btn_sign_in_loading)
        else
            getString(R.string.btn_sign_in)
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
