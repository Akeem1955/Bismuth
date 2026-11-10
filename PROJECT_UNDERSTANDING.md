# Bismuth — Project Understanding & Architecture Blueprint

> **Live Document**: This file serves as the single source of truth for project vision, architectural decisions, feature specifications, and progress milestones for **Bismuth**. It is updated continuously throughout development.
>
> **Detailed execution plan:** see [PROJECT_PLAN.md](PROJECT_PLAN.md).

---

## 1. Executive Summary & Hackathon Context

* **Event:** WALE LAB HACKATHON SERIES
* **Track:** AI for “Zero-Bandwidth” & Localized Learning
* **Theme:** *AI for the Next Generation: Reimagining How Students Learn, Work & Grow*
* **Core Problem Statement:**  
  Most educational AI tools assume perpetual high-speed internet, high-end smartphones, and English-centric instruction. When data is exhausted or connectivity drops, conventional cloud-based tutors fail completely. Students in bandwidth-constrained regions (secondary school through university) face expensive data tariffs, intermittent blackouts, low-spec devices, and difficult abstractions taught via dense, rote textbooks.
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
* **Offline PDF Extraction:** Local text extraction from textbooks, lecture notes, and question papers stored on device.
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

## 7. Teaching Skills Ecosystem (Agreed Direction — 2026-11-10)

> **Principle:** We are not building five educational chatbots. We are building **one general-purpose AI tutor** with **five extensible subject ecosystems**, delivered as downloadable, hierarchical Agent Skills.

### 7.1. Initial Subjects
English · Mathematics · Chemistry · Physics · Biology

**Not tied to any exam board or class level.** No WAEC/JAMB/SS1–SS3 framing. *Physics is Physics.* A secondary school student and a university student use the same subject library and simply pick the chapter/topic they need.

> **Rejected (2026-11-10):** exam-board or grade-level curricula (WAEC, NECO, JAMB, SS1–SS3). This was an earlier assistant assumption, not a user requirement.

### 7.2. Skill Hierarchy
```
Subject Skill   (coordinator: teaching philosophy, curriculum index, chapter directory)
 └── Chapter Skill   (organizes topics, chapter objectives, prerequisites)
      └── Topic Skill   (the actual lesson: instructions + resources + activities + assessment)
```
Example: `Mathematics` → `Algebra` → `Linear Equations`.

* **Subject skill** = coordinator. **Chapter skills** = organize their topics. **Topic skills** = deliver the actual teaching experience.
* **Depth/level is expressed through the hierarchy itself** (which chapters and topics exist). There is no separate level layer — the student navigates to what they need.

### 7.3. Three-Layer Package Separation (must be preserved)
| Layer | Responsibility |
| :--- | :--- |
| **Skill instructions** | Tell the LLM *how* to teach (objectives, explanation sequence, misconceptions, difficulty progression) |
| **Learning resources** | Verified, curated content: text, images, diagrams, short offline videos |
| **Interactive components** | App-rendered activities: label-the-diagram, simulations, knowledge checks, manipulable diagrams |

The LLM is the **tutor**; it does not generate the video, diagrams or simulations.

### 7.3.1. Skills Are the Orchestrators (core principle)
**The skills drive the tutor.** A skill is not passive content the LLM reads — it is a **high-quality orchestrator** that defines the lesson flow: what to explain, in what order, when to show a resource, when to launch an interactive component, when to check understanding, and how to respond to mistakes. The small on-device model follows the skill's orchestration rather than improvising pedagogy on its own.

Consequence: **skill quality = teaching quality.** Authoring and validating skills is the core product work.

### 7.3.2. Orchestration Style — Recommended: Hybrid "Guided Steps" (pending user approval)
**Decision proposed 2026-11-10:** Topic skills define a **lesson as an ordered set of steps with branches** (fixed key moments), and the model talks freely *inside* each step.

* **Skill (authored)** defines each step: goal, what to show/launch (resource or interactive component), the check-for-understanding, the correct answer, known misconceptions → which remedial step to branch to.
* **App runtime (deterministic code)** tracks which step the student is on, moves between steps, grades answers from the skill's answer key, and gives the model **only the current step's instructions** (not the whole lesson).
* **Model (Gemma)** explains in natural language, answers side questions, gives hints, and explains *why* an answer was wrong — but cannot skip, reorder or invent the lesson structure.

**Rationale (research, 2026-11-10):**
1. Recent LLM-tutoring work reports that free-form tutors drift, over-explain and give away answers; stronger designs pair a structured/state-driven lesson flow with the LLM handling phrasing and hints.
2. Small on-device models (~1–4B) lose track of long, multi-rule prompts; reliable patterns inject only step-specific instructions and keep control flow in code.
3. Agent Skills spec (agentskills.io) uses progressive disclosure (metadata → `SKILL.md` body → referenced files on demand), which maps directly to subject → chapter → topic → current step.
4. Answer-key grading limits misleading AI output (track criterion: accuracy).

*Caveat:* findings were gathered via web search summaries; primary papers were not individually read.

### 7.4. Hierarchical On-Demand Loading (anti context-overload)
1. Student picks subject → load only the subject's lightweight curriculum index.
2. Student picks chapter → load chapter skill.
3. Student picks topic → load only that topic's instructions + resource manifest.
4. Lesson ends → topic context unloaded.

### 7.5. Distribution
* **Authoring PC (Google Cloud SDK installed)** → prepare, curate, validate, package skills.
* **Google Cloud Storage** → hosts subject / chapter / topic packages.
* **Bismuth Skill Manager (on device)** → browse, download, install, update, remove.
* **Local Android storage** → all instructions, media, interactive assets.
* **Gemma + Skills** → teaching, reasoning, assessment — **fully offline** after download. GCS is only touched for download/update.

---

## 8. Open Alignment Decisions (Planning Mode)

1. **Hardware Tier Detection:** Automatic (RAM/SoC benchmark) vs user-selectable.
2. **Visualizer Board Touch Interactivity:** Gesture specs (pan/zoom, tap-to-solve, step scrubbers).
3. **Skills Ecosystem — pending clarification (see Deficit Log, 2026-11-10):** skill file format / orchestration structure, download granularity, GCS access model, interactive component strategy (native registry vs WebView bundles), media budgets, content authoring/licensing, LLM↔component tool contract, assessment grading, hackathon demo scope.
