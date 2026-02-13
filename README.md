# QuickShare (Secure Peer-to-Peer Photo Sharing)

QuickShare is a secure, privacy-focused Android application designed to facilitate peer-to-peer photo sharing over a secure SSH tunnel. It allows you to select photos from your device and share them instantly with anyone via a generated QR code or link, without uploading your data to a third-party cloud server. Your device acts as the server.

---

## 📑 Index

1.  [How It Works](#how-it-works)
2.  [Security Architecture](#security-architecture)
3.  [Features](#features)
4.  [Limitations](#limitations)
5.  [Technical details](#technical-details)

---

## 🚀 How It Works

1.  **Select Photos**: Choose one or multiple photos from your Android gallery.
2.  **Start Session**: The app starts a local lightweight HTTP server on your device.
3.  **Secure Tunnel**: It establishes a reverse SSH tunnel to `localhost.run` (or similar services), exposing your local server to a secure public URL (HTTPS).
4.  **Share**: A QR code is generated containing the unique URL and a session token.
5.  **Access**: The recipient scans the QR code or opens the link to view and download the photos directly from your device.
6.  **Stop**: When you stop the session, the tunnel closes, and the link becomes invalid immediately.

---

## 🔒 Security Architecture

QuickShare prioritizes your privacy and security:

*   **End-to-End Encryption (HTTPS)**: The tunnel provider (localhost.run) provisions an SSL certificate, ensuring that the connection between the recipient and the tunnel server is encrypted. The SSH tunnel itself encrypts traffic from your device to the tunnel server.
*   **Session Tokens**: Every sharing session generates a **random, ephemeral 8-character token** (UUID fragment). This token is required to access any content.
    *   `https://random-id.lhr.life/?token=a1b2c3d4`
    *   Requests without this token are rejected with `401 Unauthorized`.
*   **No Cloud Storage**: Photos are **never** uploaded to a cloud database. They are streamed directly from your device's storage to the recipient's browser. Once the session ends, the data is inaccessible.
*   **Read-Only Access**: The local server only exposes the specific files you selected. It does not have access to your entire gallery or file system.
*   **Ephemeral Nature**: URLs are temporary. As soon as you click "Stop Sharing" or close the app, the detailed route is destroyed.

---

## ✨ Features

*   **Modern UI**: Sleek, dark-themed Material Design interface.
*   **Instant Sharing**: No account creation or login required.
*   **Batch Selection**: Share multiple photos at once.
*   **QR Code Generation**: Easy mobile-to-mobile sharing.
*   **Zero-Install Receiver**: Recipients only need a web browser.
*   **Live Analytics**: See how long the session has been active.

---

## ⚠️ Limitations

1.  **Network Dependant**: Since the device hosts the files, it *must* have an active internet connection. If your phone loses signal, the download stops.
2.  **Speed**: Transfer speeds limited by your device's upload bandwidth and the tunnel provider's latency.
3.  **Background Process**: Android might kill the background process if the app is minimized for too long to save battery. Keep the app open for best performance during large transfers.
4.  **Public Tunnel Trust**: Traffic passes through the `localhost.run` relay. While encrypted (SSH/HTTPS), you are relying on the tunnel provider's infrastructure for routing.

---

## 🛠 Technical Details

*   **Language**: Kotlin Multiplatform (Android target active).
*   **UI Framework**: Jetpack Compose.
*   **Server**: Ktor (Embedded Netty/CIO server).
*   **Tunneling**: JSch (Java Secure Channel) for SSH port forwarding.
*   **QR Code**: ZXing library for generation.

---

*Built with ❤️ for privacy.*
