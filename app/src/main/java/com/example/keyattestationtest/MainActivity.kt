package com.example.keyattestationtest

import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AttestationScreen()
            }
        }
    }
}

@Composable
fun AttestationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            "Tap the button to generate a StrongBox-backed key " +
                    "and send its attestation chain to the backend (Logcat tag: ATTEST)."
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Run everything on a background thread
                Thread {
                    generateAttestedKeyAndSend()
                }.start()
            }
        ) {
            Text("Run Attestation")
        }
    }
}

/**
 * Generates a StrongBox-backed key (if available) with an attestation challenge,
 * logs the chain, and sends it to the backend.
 */
fun generateAttestedKeyAndSend(): Array<Certificate>? {
    return try {
        val alias = "marta_key_test"

        // Create KeyPairGenerator for AndroidKeyStore
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )

        // Build key spec
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256)

        // ---- STRONGBOX SUPPORT ----
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                Log.d("ATTEST", "StrongBox requested.")
            } catch (e: Exception) {
                // If StrongBox is not available, we fall back to TEE
                Log.w("ATTEST", "StrongBox not available, falling back to TEE.", e)
            }
        } else {
            Log.w("ATTEST", "StrongBox not supported on this Android version.")
        }

        // ---- ATTESTATION CHALLENGE ----
        val challengeString = "MARTA_TEST"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val challenge = challengeString.toByteArray()
            builder.setAttestationChallenge(challenge)
            Log.d("ATTEST", "Attestation challenge set.")
        } else {
            Log.w("ATTEST", "Attestation challenge not supported on this device.")
        }

        val spec = builder.build()

        // Generate the key pair
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
        Log.d("ATTEST", "Key pair generated for alias '$alias'.")

        // Load the certificate chain from the Android Keystore
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val chain = keyStore.getCertificateChain(alias)

        if (chain == null) {
            Log.e("ATTEST", "Certificate chain is null.")
            return null
        }

        // Log a compact summary of the chain
        chain.forEachIndexed { index, cert ->
            val x509 = cert as? X509Certificate
            if (x509 != null) {
                Log.d(
                    "ATTEST",
                    "Certificate $index: subject=${x509.subjectX500Principal}, " +
                            "issuer=${x509.issuerX500Principal}"
                )
            } else {
                Log.d(
                    "ATTEST",
                    "Certificate $index: type=${cert.type}"
                )
            }
        }

        // Send the chain to the backend
        sendAttestationToServer(chain, challengeString)

        chain
    } catch (e: Exception) {
        Log.e("ATTEST", "Error generating key attestation", e)
        null
    }
}

/**
 * Sends the attestation data to the backend via HTTP POST (JSON).
 */
fun sendAttestationToServer(chain: Array<Certificate>, challenge: String) {
    try {
        // Emulator -> host mapping
        val url = URL("http://127.0.0.1:5000/attestation")

        // Build JSON
        val json = JSONObject().apply {
            put("challenge", challenge)

            val certArray = JSONArray()
            chain.forEach { cert ->
                val x509 = cert as X509Certificate
                val encoded = Base64.encodeToString(
                    x509.encoded,
                    Base64.NO_WRAP
                )
                certArray.put(encoded)
            }
            put("certChain", certArray)
        }

        Log.d("ATTEST", "Sending attestation JSON to server: $json")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
        }

        // Write body
        conn.outputStream.use { os ->
            val body = json.toString().toByteArray(Charsets.UTF_8)
            os.write(body)
        }

        // Read response
        val responseCode = conn.responseCode
        val responseText = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }

        Log.d("ATTEST", "Server response code: $responseCode")
        Log.d("ATTEST", "Server response body: $responseText")

        conn.disconnect()
    } catch (e: Exception) {
        Log.e("ATTEST", "Error sending attestation to server", e)
    }
}
