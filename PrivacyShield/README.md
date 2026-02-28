# 🔒 Privacy Shield — Local ID Card Censorer

> **100% offline. Zero internet permissions. All processing on-device.**

A privacy-first Android app that combines **real-time ID card detection & automatic censorship** with a **fully offline AI chatbot** — powered by YOLOv8n, ML Kit, and llama.cpp.

---

## Features

### Part 1 — Real-Time ID Card Protection
| Feature | Implementation |
|---|---|
| Card detection | YOLOv8n TFLite (320×320, 20–30 FPS) |
| OCR | ML Kit Text Recognition (bundled, offline) |
| Barcode/QR detection | ML Kit Barcode Scanning |
| Sensitive field classification | Regex patterns (ID number, DOB, name, address) |
| Blur modes | Gaussian (RenderScript GPU), Pixelation, Black rectangle |
| Safe Share export | Selective field masking by purpose |
| Privacy guarantee | No storage, no network, memory-only processing |

### Part 2 — Offline AI Chatbot
| Feature | Implementation |
|---|---|
| LLM runtime | llama.cpp (JNI, ARM NEON optimised) |
| Model | TinyLlama 1.1B Q4 or Gemma 2B Q4 (user-supplied GGUF) |
| UI | Jetpack Compose + streaming token output |
| Context management | 2048-token window, auto-trimming |
| Privacy | No logs, no network, memory-only |

---

## Architecture

```
UI Layer (Jetpack Compose)
    CameraScreen ──────────── ChatScreen
         │                        │
    CameraViewModel          ChatViewModel
         │                        │
    CameraManager             LlamaEngine
         │                        │
  FrameAnalyzer              LlamaJNI (JNI)
    ├── IdCardDetector             │
    ├── OcrEngine            llama_bridge.cpp
    └── SensitiveFieldClassifier   │
                                llama.cpp
    BlurEngine
```

---

## Setup

### Prerequisites
- Android Studio Hedgehog or newer
- NDK 25+ installed via SDK Manager
- CMake 3.22.1+
- Device with Android 8.0+ (API 26), 4–8 GB RAM

### 1. Clone with submodules
```bash
git clone --recursive https://github.com/youruser/PrivacyShield.git
cd PrivacyShield
```

If you already cloned without `--recursive`:
```bash
git submodule update --init --recursive
```

### 2. Add the YOLOv8n TFLite model

Export a YOLOv8n model trained on ID cards:
```bash
pip install ultralytics
yolo export model=yolov8n.pt format=tflite imgsz=320
```

Place the output at:
```
app/src/main/assets/models/yolov8n_id.tflite
```

### 3. Add the GGUF language model

Download TinyLlama Q4:
```bash
# From HuggingFace (TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF)
wget https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf
```

Push to device:
```bash
adb push tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf \
  /sdcard/Android/data/com.privacyshield/files/models/tinyllama-q4.gguf
```

Or copy it via Android file manager to the same path.

### 4. Build & Install
```bash
./gradlew installDebug
```

---

## Project Structure

```
PrivacyShield/
├── app/src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   ├── llama_bridge.h
│   │   ├── llama_bridge.cpp       ← JNI bridge
│   │   └── llama.cpp/             ← git submodule
│   ├── assets/models/
│   │   ├── yolov8n_id.tflite      ← YOU ADD THIS
│   │   └── (tinyllama pushed via adb)
│   └── java/com/privacyshield/
│       ├── camera/                ← CameraX pipeline
│       ├── detection/             ← YOLOv8n + OCR + classifier
│       ├── blur/                  ← BlurEngine (GPU + CPU)
│       ├── llm/                   ← llama.cpp Kotlin wrappers
│       ├── export/                ← Safe Share / PDF export
│       ├── security/              ← MemoryGuard
│       ├── navigation/            ← NavGraph
│       └── ui/                    ← Compose screens & ViewModels
```

---

## Performance Targets

| Metric | Target | How |
|---|---|---|
| Camera FPS | 20–30 | Frame dropping, 640×480 resolution |
| Token throughput | 10–20 tok/sec | ARM NEON, 4-bit quant, mmap |
| Cold start | < 5s | App start (excl. model load) |
| RAM on 6GB device | < 2GB total | mlock=false, context=2048 |

---

## Security Checklist

- ✅ No `INTERNET` permission in manifest
- ✅ `android:allowBackup="false"`
- ✅ All cloud/device backup excluded via `data_extraction_rules.xml`
- ✅ Camera frames closed and recycled after each analysis
- ✅ OCR text never persisted — classified and discarded in-frame
- ✅ Chat messages live only in `ViewModel` — cleared on reset
- ✅ GGUF model loaded via `mmap` (no full copy in heap)
- ✅ `MemoryGuard.secureClear()` for sensitive byte buffers
- ✅ ProGuard enabled in release builds

---

## Model Size Guide

| Model | Size | RAM needed | Quality |
|---|---|---|---|
| TinyLlama 1.1B Q4_K_M | ~680 MB | ~900 MB | Good for privacy Q&A |
| Gemma 2B Q4_K_M | ~1.5 GB | ~2 GB | Better reasoning |
| Phi-2 Q4_K_M | ~1.6 GB | ~2 GB | Strong instruction following |

---

## License

MIT — see [LICENSE](LICENSE)

llama.cpp is MIT licensed.
TensorFlow Lite is Apache 2.0 licensed.
ML Kit is subject to Google APIs Terms of Service.
