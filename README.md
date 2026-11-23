# MoviesPro - Streaming Platform & Media Suite 🎬🍿

> High-performance Android TV & Mobile streaming client engineered with Jetpack Compose, ExoPlayer, and custom stream extractors.

[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Mobile-green.svg)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

---

## 🌟 Overview

**MoviesPro** is a modern media streaming application optimized for large screens (Android TV / Google TV) and handheld mobile devices. Built with modern Android development standards, it features high-definition playback, intelligent stream ranking, multi-server extraction, and Netflix-grade UI design.

---

## 🏗️ Architecture & Features

```
APK-Moviespro/
├── android-app/
│   ├── app/              # TV client with D-Pad navigation & Leanback support
│   ├── app-mobile/       # Mobile client with swipe gestures & responsive layout
│   └── core/             # Shared repositories, extractors, and ExoPlayer engine
└── scripts/              # Automated build & packaging scripts
```

### Key Highlights
- **Jetpack Compose TV**: Custom cards, focus indicators, and smooth horizontal carousels with zero frame drops.
- **ExoPlayer Integration**: Custom player controls, buffering overlays, subtitle parser, and automatic playback resumption.
- **Robust Multi-Source Extractors**: Resilient scraper engine with fallback mechanisms to ensure 99.9% playback availability.
- **Clean Architecture & MVI/MVVM**: Strict separation of concerns for maintainability and automated testability.

---

## 🚀 Building & Running

### Requirements
- Android Studio Ladybug or newer
- JDK 17 (JBR)
- Android SDK 34+

```bash
cd android-app
./gradlew assembleRelease
```

---

## 👤 Author
**Kevin Palacios Pozo** - Software Engineer  
- GitHub: [@Sciclox](https://github.com/Sciclox)
