# VerifyID Android SDK

The **VerifyID Android SDK** allows you to easily integrate KYC (Know Your Customer) verification workflows into your Android apps. Capture document images and selfies, send them securely to the VerifyID API, and display verification results in a modern Compose UI.

---

## Features

- 📸 In-app camera capture for document front, back, and selfie
- 🔒 Secure API key integration
- 📤 Sends images as base64 to your configured endpoint
- 🎨 Customizable, responsive UI (Jetpack Compose)
- ✅ Ready for production & white-labelling

---

## Installation

1. **Add the SDK to your project**
    - Copy the SDK module (`com.verifyid_sdk_android`) into your project, or add as a submodule.

2. **Dependencies**
    - Make sure your `build.gradle` includes (or the SDK’s `libs.versions.toml`):

   implementation("androidx.camera:camera-camera2:1.4.2")
   implementation("androidx.camera:camera-lifecycle:1.4.2")
   implementation("androidx.camera:camera-view:1.4.2")
   implementation("androidx.exifinterface:exifinterface:1.4.1")
   implementation("androidx.compose.material3:material3:1.2.0")
   implementation("com.squareup.okhttp3:okhttp:5.1.0")

    - See `build.gradle` for all dependencies.

3. **Permissions**

    - Add camera and storage permissions to your `AndroidManifest.xml`:

    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>

---

## Usage

### 1. Launch the KYC Wizard

From any `Activity` or `Fragment`, start the KYC process with your API key:

KycWizardActivity.launch(context = this, apiKey = "your_x_api_key")

- Replace `"your_x_api_key"` with the API key provided by VerifyID.

### 2. API Integration

- The SDK handles capturing images, converting them to base64, and sending them to:
  POST https://api.verifyid.io/kyc/full_verification

- The API key is sent in the header: `x-api-key`
- The request body is JSON:

      {
        "front_image": "base64string",
        "back_image": "base64string",
        "selfie_image": "base64string",
        "threshold": 0.6
      }

### 3. Handling Results

- Success and error responses are handled automatically in the UI.
- The raw API response is displayed on the final screen for review/debug.

---

## Customization

- Button colors, messages, and UI can be modified in `KycWizardActivity.kt`.
- All logic is Jetpack Compose-based and ready for white-labelling.

---

## Troubleshooting

- If you get a blank result or crash after submission, check that:
    - Camera permissions are granted.
    - Your API key is correct and active.
    - Images are being captured correctly (see `selfie_b64.txt` in the Downloads folder for debugging).

---

## Support

For technical support, contact [support@verifyid.io](mailto:support@verifyid.io).

---

## Quick Start

1. **Add to your project:**
    - Clone/download this repo.
    - Copy the `com.verifyid_sdk_android` folder into your Android project.

2. **Launch SDK from your app:**

```kotlin
KycWizardActivity.launch(context = this, apiKey = "YOUR_X_API_KEY")
```

---

© 2025 VerifyID.io