# Android StrongBox Key Attestation PoC

This repository contains a small proof of concept for **hardware-backed key attestation** on Android using **StrongBox** and a **Flask backend**.

The goal is to experiment with the ideas from:

- [Verify hardware-backed key pairs with key attestation (Android Developers)](https://developer.android.com/privacy-and-security/security-key-attestation)
- gematik PoC and requirements for secure device binding / Remote PIN entry

and prepare a basis for later work on **Remote PIN entry for eHBA**.

---

## Project structure

```text
app/            # Android app (Jetpack Compose)
backend/        # Flask server that verifies the attestation chain
