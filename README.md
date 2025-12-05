# Android StrongBox / TEE Key Attestation — Proof of Concept

This repository contains a working proof of concept for **hardware–backed key attestation** on Android, combined with a simple **Flask backend** for certificate-chain verification.

The project demonstrates:

- How to generate an attested key using the Android Keystore  
- How to extract the certificate chain (TEE or StrongBox)  
- How to encode and send attestation data to a backend  
- How to validate the chain on the server side (simplified PoC)

This PoC is intentionally minimal and meant for learning and experimentation.

---

## Project Structure

```text
.
├── app/            # Android Studio project (Compose UI + attestation logic)
├── backend/        # Flask server for receiving attestation JSON
├── gradle/         # Gradle wrapper
├── build.gradle.kts
├── gradle.properties
└── README.md


---

## Android App

### Key Attestation Generation

The app:

1. Creates a **new RSA key in the Android Keystore**
2. Requests attestation (`setAttestationChallenge`)
3. Attempts StrongBox if available  
4. Prints the entire certificate chain in Logcat

### Example Logcat Output

Certificate 0:
Attestation Leaf Certificate
Certificate 1:
Android Attestation Sub-CA
Certificate 2:
Google Root Certificate


---

## Attestation JSON Sent to Backend

The app encodes the certificate chain into Base64 and sends:

```json
{
  "challenge": "MARTA_TEST",
  "cert_chain": [
    "MIICvjCCAaYCCQD...base64...",
    "MIIDTDCCAjSgAwIBAgIU...base64...",
    "MIICJDCCAa6gAwIBA...root..."
  ]
}

## Example Run

When pressing **“Run Attestation”** in the app, the following happens:

### Android Logcat (simplified)

```text
D/ATTEST: StrongBox requested.
D/ATTEST: Attestation challenge set.
D/ATTEST: Key pair generated for alias 'marta_key_test'.
D/ATTEST: Certificate 0: subject=CN=Android Keystore Key, issuer=..., T=StrongBox
D/ATTEST: Certificate 1: subject=..., T=StrongBox, issuer=..., T=StrongBox
D/ATTEST: Certificate 2: subject=..., T=StrongBox, issuer=...
D/ATTEST: Certificate 3: subject=..., issuer=...
D/ATTEST: Sending attestation JSON to server:
          {"challenge":"MARTA_TEST","certChain":["MIIE...","MIIE...","MIIF...","MIIF..."]}
D/ATTEST: Server response code: 200
D/ATTEST: Server response body: {
            "message": "Attestation verified on server",
            "securityLevel": "StrongBox",
            "status": "ok"
          }

### Flask Backend Output
=== Received attestation request ===
{'challenge': 'MARTA_TEST', 'certChain': ['MIIE...', 'MIIE...', 'MIIF...', 'MIIF...']}
[SERVER] Cert 0 subject: CN=Android Keystore Key
[SERVER] Cert 0 issuer : ... StrongBox
[SERVER] Cert 1 subject: ... StrongBox
[SERVER] Cert 1 issuer : ... StrongBox
[SERVER] Cert 2 subject: ... StrongBox
[SERVER] Cert 2 issuer : ...
[SERVER] Cert 3 subject: ...
[SERVER] Cert 3 issuer : ...
[SERVER] Signature of cert 0 verified with issuer cert 1.
[SERVER] Signature of cert 1 verified with issuer cert 2.
[SERVER] Signature of cert 2 verified with issuer cert 3.
[SERVER] Attestation chain verified successfully.
[SERVER] StrongBox marker found in subject[1].
[SERVER] StrongBox attestation confirmed.

```

### Networking / Ports (WSL, Device, Emulator)

The backend listens on port 5000.
Depending on where you run Android (real device vs emulator) and how you run Flask (WSL vs native Windows), the URL inside the app must be adjusted, otherwise you’ll get errors like:

java.net.ConnectException: Failed to connect to /192.168.x.x:5000

1. Flask in WSL + Physical Android Device (what this PoC uses)

When you run python backend.py inside WSL, Flask shows something like:

 * Running on http://127.0.0.1:5000
 * Running on http://172.24.53.136:5000

Alternative: adb reverse (device / emulator)
You can also forward the port with adb: ```adb reverse tcp:5000 tcp:5000```
