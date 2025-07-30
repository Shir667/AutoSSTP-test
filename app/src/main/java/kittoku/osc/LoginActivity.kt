package kittoku.osc

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log // Log ekle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kittoku.osc.activity.MainActivity
import kittoku.osc.databinding.ActivityLoginBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.view.View

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val prefs by lazy { getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
    private val userCredentialsUrl = "https://drive.google.com/uc?export=download&id=1hIGzC5OpPIbyAatBSNCR_ieKsB6WX7ax"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Yükleme ekranını göster
        binding.loadingView.visibility = View.VISIBLE // binding ile erişim

        val deviceId = prefs.getString("DEVICE_ID", null) ?: generateRandomID(9).also {
            prefs.edit().putString("DEVICE_ID", it).apply()
        }

        binding.tvDeviceId.text = "ID: $deviceId"
        binding.tvDeviceId.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ID", deviceId))
            Toast.makeText(this, "ID kopyalandı: $deviceId", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogin.setOnClickListener {
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                binding.loadingView.visibility = View.VISIBLE
                validateCredentials(user, pass, deviceId)
            } else {
                Toast.makeText(this, "Kullanıcı adı ve şifre boş olamaz!", Toast.LENGTH_SHORT).show()
            }
        }

        // İlk yükleme için asenkron işlem
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(userCredentialsUrl).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val lines = response.body?.string()?.split("\n")?.map { it.trim() } ?: emptyList()
                    // Önbellek (isteğe bağlı)
                }
            } catch (e: Exception) {
                Log.e("LOGIN_ACTIVITY", "Initial load error: ${e.message}", e)
            } finally {
                runOnUiThread { binding.loadingView.visibility = View.GONE }
            }
        }
    }

    private fun validateCredentials(username: String, password: String, deviceId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(userCredentialsUrl).build()
                val response = client.newCall(request).execute()

                Log.d("LOGIN_ACTIVITY", "HTTP response: ${response.code}")
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Kullanıcı bilgileri yüklenemedi!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val lines = response.body?.string()?.split("\n")?.map { it.trim() } ?: emptyList()
                val users = mutableListOf<Map<String, String>>()
                var current = mutableMapOf<String, String>()
                for (line in lines) {
                    if (line.isBlank()) {
                        if (current.isNotEmpty()) {
                            users.add(current)
                            current = mutableMapOf()
                        }
                        continue
                    }
                    val kv = line.split(":", limit = 2)
                    if (kv.size == 2) {
                        current[kv[0].trim()] = kv[1].trim()
                    }
                }
                if (current.isNotEmpty()) users.add(current)

                val found = users.firstOrNull {
                    it["Name"] == username && it["Password"] == password && it["ID"] == deviceId
                }

                if (found == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Kimlik bilgileri uyuşmuyor!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val expire = found["Expire data"]?.let {
                    LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                }
                val expired = expire != null && LocalDate.now().isAfter(expire)

                withContext(Dispatchers.Main) {
                    binding.loadingView.visibility = View.GONE
                    if (expired) {
                        Toast.makeText(this@LoginActivity, "Kullanım süreniz doldu!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@LoginActivity, "Giriş başarılı", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                }

            } catch (e: Exception) {
                Log.e("LOGIN_ACTIVITY", "Validation error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.loadingView.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateRandomID(length: Int): String =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".let { chars ->
            (1..length).map { chars.random() }.joinToString("")
        }
}