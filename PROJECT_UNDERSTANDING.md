# Bismuth — Project Understanding & Architecture Blueprint

> **Live Document**: This file serves as the single source of truth for project vision, architectural decisions, feature specifications, and progress milestones for **Bismuth**. It is updated continuously throughout development.

---

## 1. Executive Summary & Hackathon Context

* **Event:** WALE LAB HACKATHON SERIES
* **Track:** AI for “Zero-Bandwidth” & Localized Learning
* **Theme:** *AI for the Next Generation: Reimagining How Students Learn, Work & Grow*
* **Core Problem Statement:**  
  Most educational AI tools assume perpetual high-speed internet, high-end smartphones, and English-centric instruction. When data is exhausted or connectivity drops, conventional cloud-based tutors fail completely. Students in bandwidth-constrained regions (e.g., secondary/university students in Sub-Saharan Africa preparing for exams like WAEC/JAMB) face expensive data tariffs, intermittent blackouts, low-spec devices, and difficult STEM abstractions taught via dense, rote textbooks.
* **The Bismuth Solution:**  
  An **Offline-First, Zero-Bandwidth Interactive AI Micro-Tutor** running quantized Small Language Models (SLMs) directly on the edge. Bismuth combines voice perception, an interactive animated Rive tutor, and curriculum-grounded Socratic lessons—operating in complete **Airplane Mode** with zero ongoing data consumption.

---

## 2. Core Architecture & Feature Segmentation

Bismuth inherits and revamps four core subsystems inspired by the `Genie` prototype, partitioned across device capability tiers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                          BISMUTH APPLICATION                           │
├────────────────────────────────────────────────────────────────────────┤
│                       BASE TIER (ALL DEVICES)                          │
│                                                                        │
│  [1. TEACHING ENGINE]                 [2. DOCUMENT ENGINE]             │
│   • Revamped Interactive Visualizer    • Offline PDF Textbook Ingestion │
│   • Socratic Step-by-Step Lessons      • Section Summaries & Key Notes  │
│   • Rive Character State Machine       • Auto-Generated Practice Quizzes│
│   • Tap-to-Reveal / Diagram Scrubber   • Flashcard Decks                │
├────────────────────────────────────────────────────────────────────────┤
│                  CAPABILITY-GATED TIERS (VISION / NPU)                 │
│                                                                        │
│  [3. SCRIBE ENGINE]                   [4. HEALTH ENGINE]               │
│   • Offline Audio Transcription        • Localized WHO Health Library   │
│   • Lecture to Structured Study Notes  • Offline Topic Search           │
│   • Vision Tier: Chalkboard / Slide    • Vision Tier: Food Photo Calorie│
│     Photo Ingestion + Audio Merge        & Nutrient Scanner             │
│                                        • Low-Tier Fallback: Food Catalog│
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Subsystem Breakdown

### 3.1. Teaching Engine *(Base Tier — Default)*
* **Visualizer Board Revamp:** Moving beyond static shapes to an active, engaging pedagogical canvas:
  * **Interactive Canvas:** Pan, zoom, tap diagram nodes to expand concept explanations, and scrub forward/backward through step-by-step proofs.
  * **Active Learning Pauses:** The board prompts the student mid-lesson to select the next formula or label before progressing.
* **Animated Rive Character Companion:**
  * Runs hardware-accelerated 2D vector animations (`.riv`) via C++ rendering at 60fps with a tiny memory footprint.
  * State-machine driven expressions: reacts emotionally to user comprehension (*Idle*, *Thinking*, *Listening*, *Excited*).
  * Points to board items and celebrates correct student interactions with Lottie micro-FX.

### 3.2. Document Engine *(Base Tier — Default)*
* **Offline PDF Extraction:** Local text extraction from curriculum textbooks, syllabi, and past exam question papers stored on device.
* **Study Distillation:** Quantized on-device LLM summarizes dense textbook pages into simplified bullet points, real-world analogies, and self-test quizzes without calling external APIs.

### 3.3. Scribe Engine *(Tier-Gated)*
* **Audio Lecture Capture:** Uses on-device speech recognition (`Vosk`) and LiteRT-LM to transcribe classroom recordings into clean study notes, action items, and glossary terms.
* **Multimodal Vision Chalkboard Mode (High-Tier):** Allows students with capable cameras and vision models to photograph the classroom chalkboard/presentation slide; Bismuth combines the visual diagram text with the spoken lecture audio into a unified lesson document.

### 3.4. Health Engine *(Tier-Gated)*
* **Health Topics Knowledge Base:** Pre-indexed offline database of WHO health fact sheets and localized preventative health guides queryable with zero latency.
* **Food Nutrition Analyzer:**
  * **High-End Tier (Vision):** Real-time image recognition of meals to estimate calories, macronutrients, and vitamin balance.
  * **Low-End Tier (Text/Catalog Fallback):** Text search and local recipe picker for students on basic devices without on-device vision models.

---

## 4. Hardware & AI Model Tiering Strategy

| Tier | Target Devices | Target Model Engine | Available Capabilities |
| :--- | :--- | :--- | :--- |
| **High-End Tier** | Flagship / Upper-Mid (8GB+ RAM, dedicated NPU/GPU) | **Gemma 4 / Multimodal Vision SLM** (LiteRT-LM) | Full Multimodal: Chalkboard Scribe, Food Image Scanner, Audio Scribe, Interactive Teaching, PDF Document Studio |
| **Low-End Tier** | Budget / Older Android (3GB–6GB RAM, standard CPU) | **Gemma 3n / SmolLM / Quantized 1B-2B INT4** | Core Zero-Bandwidth: Text-based Interactive Teaching, PDF Document Studio, Audio Scribe Notes, Text Health Catalog |

---

## 5. Technology Stack & Verified Baseline

* **Target Platform:** Android (minSdk 26, targetSdk 36, compileSdk 36)
* **Language & Compiler:** Kotlin 2.2.0, Java 17, Android Gradle Plugin 8.11.1, Gradle 8.13
* **UI Toolkit:** Jetpack Compose (BOM `2024.09.00`), Material 3
* **Character & UI Animation:**
  * `app.rive:rive-android:11.15.0` (Native 2D vector state-machine avatar)
  * `com.airbnb.android:lottie-compose:6.7.1` (Celebration & micro-interaction FX)
* **Edge AI Engine:**
  * `LiteRT-LM` (`com.google.ai.edge.litertlm:litertlm-android:0.10.0`)
  * `Vosk Android` (`com.alphacephei:vosk-android:0.3.47` for offline ASR)
* **Local Storage & Fast Retrieval:**
  * AndroidX Room SQLite (`2.7.1`) + FTS5 full-text indexing for curriculum search

---

## 6. Current Implementation Status

* [x] **Repository Initialized & Synced:** Linked to GitHub (`https://github.com/Akeem1955/Bismuth.git`) on branch `main`.
* [x] **Reference Cloned & Isolated:** Cloned `Genie` into local workspace for feature revamping, ignored in `.gitignore`.
* [x] **Gradle Build System Established:** Configured version catalogs (`libs.versions.toml`), root, and app build scripts.
* [x] **Animation Tooling Verified:** Integrated and compiled `RiveCharacterView` and `LottieEffectView` composables without build errors.
* [x] **Interactive Demo Shell Built:** Created `MainActivity.kt` with dynamic character state switching and micro-FX triggers.
* [ ] **Visualizer Board Architecture:** Designing the touch-interactive, animated canvas engine.
* [ ] **Tier Manager & Engine Bridge:** Implementing hardware detection and model capability gating.
* [ ] **Document & Teaching Logic Migration:** Revamping Genie's `Teaching` and `Document` profiles into Bismuth's modern architecture.

---

## 7. Open Alignment Decisions (Planning Mode)

1. **Hardware Tier Detection:** Confirming whether device tiering should be automatic (RAM/SoC benchmark) or user-selectable via Settings.
2. **Visualizer Board Touch Interactivity:** Confirming specific gesture specs (pan/zoom, tap-to-solve, step scrubbers).
3. **Curriculum Starter Pack:** Deciding which subjects/levels to bundle as initial offline demo content (e.g., WAEC Senior Secondary Physics / Mathematics).
