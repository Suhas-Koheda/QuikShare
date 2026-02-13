# QuickShare (Secure Peer-to-Peer Photo Sharing)

QuickShare is a secure, privacy-focused Android application designed to facilitate peer-to-peer photo sharing over a secure SSH tunnel. It allows you to select photos from your device and share them instantly with anyone via a generated QR code or link, without uploading your data to a third-party cloud server. Your device acts as the server.

---

## 📑 Index

1.  [How It Works](#how-it-works)
2.  [Security Architecture](#security-architecture)
3.  [Analytics & Transparency](#analytics--transparency)
4.  [Features](#features)
5.  [Limitations](#limitations)
6.  [Technical Details](#technical-details)

---

## 🚀 How It Works

1.  **Select Photos**: Choose one or multiple photos from your Android gallery using the secure system media picker.
2.  **Start Session**: The app starts a local lightweight Ktor (CIO) HTTP server on your device.
3.  **Secure Tunnel**: It establishes a reverse SSH tunnel to `localhost.run` (or similar services), exposing your local server to a secure public URL (HTTPS).
4.  **Share**: A QR code is generated containing the unique URL and an ephemeral session token.
5.  **Access**: The recipient scans the QR code or opens the link to view and download the photos directly from your device.
6.  **Stop**: When you stop the session, the tunnel closes, the server shuts down, and the link becomes invalid immediately.

---

## 🔒 Security Architecture (Defense in Depth)

QuickShare is built with a "Privacy First" mindset, implementing multiple layers of security to protect your data:

### 1. Ephemeral Secure Tunnels
*   All traffic is routed through an **SSH-encrypted tunnel** from your device to the gateway.
*   The gateway provides an **SSL/TLS (HTTPS)** endpoint, ensuring end-to-end encryption between the recipient's browser and the tunnel server.

### 2. Mandatory Session Tokens
*   Every sharing session generates a **unique, random 8-character UUID fragment** (`sessionToken`).
*   **Zero-Trust Routing**: The Ktor server intercepts every request (`/`, `/photos`, `/thumbnail`, `/download`). If the `?token=` parameter is missing or incorrect, it returns a `401 Unauthorized` status.
*   Token validation is applied at the code level for every single byte served.

### 3. Direct Memory Streaming
*   **No Disk Persistence**: Shared files are never written to any intermediate cloud storage or cache.
*   **Zero-Copy Principles**: Files are streamed directly from your device's `ContentResolver` to the network response. This prevents data leaks on the hosting device and ensures "what you share is only what they see."

### 4. Sandboxed File Access
*   QuickShare uses the modern **Android Photo Picker**. It does **not** require broad "Read External Storage" permissions. It only has access to the specific URIs you explicitly select.

### 5. Automatic Lifecycle Management
*   **Foreground Service**: Running as a foreground service ensures Android doesn't kill the process mid-transfer while also notifying the user that a sharing session is active.
*   **Hard Shutdown**: When the service is stopped, the server engine is destroyed, the port is released, and the session token is cleared from memory.

---

## 📊 Analytics & Transparency

To improve the user experience and monitor service health, QuickShare integrates **PostHog** for privacy-conscious analytics. 

**What we track:**
*   **Session Metrics**: When a session starts/stops and how many photos are being shared.
*   **Interaction Events**: Copying links, removing photos from the selection, and successful URL assignments.
*   **Error Reporting**: Anonymized error logs (e.g., tunnel connection failures or server startup issues) to help us debug.

**What we NEVER track:**
*   Your photos, filenames, or metadata.
*   QR code content or session tokens.
*   IP addresses of recipients.
*   Personal identifiable information (PII).

---

## ✨ Features

*   **Modern UI**: Sleek, dark-themed Material Design interface built with Jetpack Compose.
*   **Instant Sharing**: No account creation, login, or configuration required.
*   **Batch Selection**: Share up to 50 photos simultaneously.
*   **Native Share Sheets**: Share the access link or the QR code image directly through any app.
*   **Zero-Install Receiver**: Recipients only need a modern web browser to view the gallery.
*   **Live Session Timer**: Monitor exactly how long your device has been "live."

---

## ⚠️ Limitations

1.  **Network Dependent**: Since the device hosts the files, it *must* have an active internet connection.
2.  **Upload Speed**: Transfer speeds are limited by your device's upload bandwidth.
3.  **Public Tunnel Trust**: Traffic passes through the `localhost.run` relay. While encrypted, you are relying on their infrastructure for routing.
4.  **Battery Usage**: Running a server and an SSH tunnel is resource-intensive. It is recommended to use QuickShare while charging for long sessions.

---

## 🛠 Technical Details

*   **Language**: Kotlin (100%).
*   **UI Framework**: Jetpack Compose (Material 3).
*   **Server**: [Ktor](https://ktor.io/) with the CIO (Coroutine I/O) engine.
*   **Tunneling**: [JSch](http://www.jcraft.com/jsch/) (Java Secure Channel) for SSH2 protocol implementation.
*   **Analytics**: [PostHog Android SDK](https://posthog.com/docs/libraries/android).
*   **QR Encoding**: [ZXing](https://github.com/zxing/zxing) for high-performance QR generation.

---

*Built with ❤️ for privacy by the QuickShare Team.*
