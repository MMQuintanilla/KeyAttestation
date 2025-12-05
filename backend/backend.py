from flask import Flask, request, jsonify
import base64
from cryptography import x509
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes

app = Flask(__name__)

# ---------- Helpers ----------

def load_cert_from_b64(b64_der: str) -> x509.Certificate:
    der_bytes = base64.b64decode(b64_der)
    return x509.load_der_x509_certificate(der_bytes)


def verify_cert_chain(certs: list[x509.Certificate]) -> bool:
    """
    Verify that each certificate is signed by the next one in the list.
    certs[0] = leaf (Android Keystore Key)
    certs[1] = intermediate
    ...
    certs[-1] = root (Google / StrongBox root)
    """
    for i in range(len(certs) - 1):
        cert = certs[i]
        issuer_cert = certs[i + 1]
        issuer_public_key = issuer_cert.public_key()

        try:
            issuer_public_key.verify(
                cert.signature,
                cert.tbs_certificate_bytes,
                padding.PKCS1v15(),
                cert.signature_hash_algorithm,
            )
        except Exception as e:
            print(f"[SERVER] Signature verification failed for cert {i}: {e}")
            return False

        print(f"[SERVER] Signature of cert {i} verified with issuer cert {i + 1}.")
    return True


# ---------- NEW: StrongBox detection ----------

def is_strongbox_chain(certs: list[x509.Certificate]) -> bool:
    """
    Simple heuristic: if any certificate's subject contains 'StrongBox',
    we treat the chain as StrongBox-backed.

    This matches what you already see in your logs:
      T=StrongBox
    """
    for idx, cert in enumerate(certs):
        subj = cert.subject.rfc4514_string()
        print(f"[SERVER] Subject[{idx}]: {subj}")
        if "StrongBox" in subj:
            print(f"[SERVER] StrongBox marker found in subject[{idx}].")
            return True
    return False


# ---------- Route ----------

@app.route("/attestation", methods=["POST"])
def handle_attestation():
    print("=== Received attestation request ===")
    data = request.get_json(force=True)
    print(data)

    # Basic sanity: the Android app sends "challenge" and "certChain"
    challenge = data.get("challenge")
    cert_chain_b64 = data.get("certChain")

    if not challenge or not cert_chain_b64:
        return jsonify({
            "status": "error",
            "message": "Missing 'challenge' or 'certChain'"
        }), 400

    # Decode cert chain from base64 DER
    try:
        certs = [load_cert_from_b64(b64_cert) for b64_cert in cert_chain_b64]
    except Exception as e:
        print(f"[SERVER] Failed to decode certificate chain: {e}")
        return jsonify({
            "status": "error",
            "message": "Invalid certificate chain (base64/DER decode failed)"
        }), 400

    # Log subjects / issuers like you already saw
    for i, c in enumerate(certs):
        subj = c.subject.rfc4514_string()
        iss = c.issuer.rfc4514_string()
        print(f"[SERVER] Cert {i} subject: {subj}")
        print(f"[SERVER] Cert {i} issuer : {iss}")

    # Verify chain signatures
    if not verify_cert_chain(certs):
        print("[SERVER] Attestation chain verification FAILED.")
        return jsonify({
            "status": "error",
            "message": "Attestation chain verification failed"
        }), 400

    print("[SERVER] Attestation chain verified successfully.")

    # ---------- NEW: enforce StrongBox ----------
    if not is_strongbox_chain(certs):
        print("[SERVER] Attestation is NOT StrongBox-backed – rejecting.")
        return jsonify({
            "status": "error",
            "message": "Attestation is not StrongBox-backed"
        }), 400

    print("[SERVER] StrongBox attestation confirmed.")

    # You could also check the challenge here (right now you use MARTA_TEST)
    # Example:
    # if challenge != "MARTA_TEST":
    #     return jsonify({"status": "error", "message": "Invalid challenge"}), 400

    return jsonify({
        "status": "ok",
        "message": "Attestation verified on server",
        "securityLevel": "StrongBox"   # nice extra for your app logs
    }), 200


if __name__ == "__main__":
    # Bind to 0.0.0.0 so the emulator / real device can reach it
    app.run(host="0.0.0.0", port=5000, debug=True)
