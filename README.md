# Android StrongBox Key Attestation PoC

This repository contains a small proof of concept for **hardware-backed key attestation** on Android using **StrongBox** and a **Flask backend**.

The Android app:
- Generates an RSA key in the Android Keystore (StrongBox if available)
- Requests an attestation certificate chain with a custom challenge
- Encodes the chain as Base64 and sends it as JSON to a local backend

The backend:
- Exposes a simple HTTP endpoint (`/attest`)
- Receives the JSON payload from the app
- Logs the attestation data (and later can verify it against Google’s root)

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
