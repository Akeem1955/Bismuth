# Bismuth

**[Test the App — Download APK & Media (Google Drive)](https://drive.google.com/drive/folders/1QE_tyxi28VJZDsxSsMBIR2wMTD37qJQc?usp=sharing)**

An offline-first Android learning studio powered by on-device LiteRT-LM models (`Gemma 4`, `Gemma 3n`, `Qwen 2.5`, `DeepSeek-R1 Distill Qwen`, and `Phi-4 Mini`).

## Core Features

- **Teach & Quick Learn**: Subject-organized interactive whiteboard lessons across 5 subjects (`Mathematics`, `Physics`, `Chemistry`, `Biology`, `English` — 10 topic modules) with visual simulations, step diagrams, audio walkthroughs, and check quizzes, plus instant **Quick Learn** topic explanations.
- **Docs**: On-device PDF textbook ingestion with page/chapter slicing into offline multiple-choice quizzes and study summaries.
- **Scribe**: Offline 16 kHz lecture recording (auto-segmented into 10-minute chunks) with neural transcription, 3-part study notes, and Q&A *(requires an audio-capable model)*.
- **Health**: Meal photo or text nutrition breakdown, daily meal diary tracking, and an offline WHO health topic library.
- **AI Brains**: Hardware RAM tier detection (`2 GB`–`8 GB+`), background model downloads, and local `.litertlm` file import.

## Build & Install

- **Requirements**: Android SDK (`minSdk 26`, `targetSdk 36`), JDK 17
- **Install debug APK to a connected device**:
  ```bash
  ./gradlew installDebug
  ```
