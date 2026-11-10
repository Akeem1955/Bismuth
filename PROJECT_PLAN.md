# Bismuth — Detailed Project Plan

> Companion to [PROJECT_UNDERSTANDING.md](PROJECT_UNDERSTANDING.md).  
> **Status legend:** ✅ Agreed · 🟡 Proposed (awaiting approval) · ❓ Open decision  
> Last updated: 2026-11-10
>
> ⚠️ **Functional Requirements Specification received (2026-11-10)** — see PROJECT_UNDERSTANDING.md §7A. Sections 8–10 below will be revised once the FRS clarifications are answered.

---

## 0. Scope Summary

| Item | Status |
| :--- | :--- |
| App name **Bismuth**; `Genie/` is reference only | ✅ |
| Features carried from Genie: **Teaching, Document, Scribe, Health** (revamped) | ✅ |
| **Teaching + Document** = base features for every device | ✅ |
| **Scribe + Health** vision capabilities gated by model/phone capability | ✅ |
| Two AI tiers: **Gemma 4** (powerful phones) · **Gemma 3n or lighter** (low-end) | ✅ |
| Subjects: **English, Mathematics, Chemistry, Physics, Biology** | ✅ |
| No exam board / class level. Physics is Physics; any student picks what they need | ✅ |
| Skill hierarchy: **Subject (coordinator) → Chapter (organizes) → Topic (teaches)** | ✅ |
| Three layers per skill: **instructions · curated resources · interactive components** | ✅ |
| **Skills orchestrate the tutor**; skill quality = teaching quality | ✅ |
| Skills distributed via **Google Cloud Storage**, fully offline after download | ✅ |
| Visualizer board revamped to be more interactive and engaging | ✅ |
| Rive (character) + Lottie (micro-FX) as animation tools | ✅ |
| Orchestration style: **Hybrid guided steps** | 🟡 |

---

## 1. System Architecture

```
┌──────────────────────────── AUTHORING (Dev PC) ────────────────────────────┐
│  Write skills → Validate (schema, assets, answer keys) → Package → Upload  │
│                         gcloud storage cp  ──────────────►  GCS bucket     │
└────────────────────────────────────────────────────────────────────────────┘
                                         │ (download/update only)
┌──────────────────────────── BISMUTH (Android) ─────────────────────────────┐
│                                                                            │
│  Skill Manager ── catalog, download, verify, install, update, remove       │
│        │                                                                   │
│        ▼                                                                   │
│  Local Skill Store (files + Room index)                                    │
│        │                                                                   │
│        ▼                                                                   │
│  Skill Runtime ── loader (progressive) · lesson step engine · grader       │
│        │                  │                                                │
│        ▼                  ▼                                                │
│  Context Builder ──► AI Engine (LiteRT-LM: Gemma 4 / Gemma 3n)             │
│        │                  │  (tool calls)                                  │
│        ▼                  ▼                                                │
│  Teaching UI ── Rive tutor · revamped board · media player ·               │
│                 interactive component registry · Lottie FX                 │
│                                                                            │
│  Tier Manager ── device capability → model choice → feature gating         │
│  Document · Scribe · Health modules (share AI Engine + Tier Manager)       │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Proposed Code Structure (`app/src/main/java/com/bismuth/app/`) 🟡

| Package | Responsibility |
| :--- | :--- |
| `engine/` | LiteRT-LM wrapper (ported/refactored from Genie `GenieEngine`), streaming, tool-call parsing, model download |
| `tier/` | Device capability detection, model selection, feature gate API |
| `skills/catalog/` | Remote catalog fetch, Skill Manager logic |
| `skills/install/` | Download, checksum verify, unpack, version/update, delete |
| `skills/model/` | Kotlin data classes for subject / chapter / topic / lesson steps |
| `skills/runtime/` | Progressive loader, lesson step engine, answer grader, context builder |
| `teaching/ui/` | Lesson screen, subject/chapter/topic browser |
| `teaching/board/` | Revamped interactive visualizer board |
| `teaching/components/` | Interactive component registry (MCQ, label-diagram, etc.) |
| `teaching/media/` | Offline image viewer, video player |
| `ui/animation/` | `RiveCharacterView`, `LottieEffectView` (exist) |
| `document/` | PDF import, extraction, summaries, quizzes |
| `scribe/` | Audio recording, transcription, notes; vision capture (gated) |
| `health/` | Offline health library, food analyzer (vision gated) + text fallback |
| `data/` | Room database: installed skills, progress, documents, notes |

---

## 3. Skill Package Format 🟡

Based on the Agent Skills convention (`SKILL.md` with YAML frontmatter + markdown body, extra files loaded on demand), extended with machine-readable files the app runtime needs.

```
mathematics/                          ← Subject skill
├── SKILL.md                          ← coordinator instructions + teaching philosophy
├── manifest.json                     ← id, version, chapter list, sizes, checksums
└── chapters/
    └── algebra/                      ← Chapter skill
        ├── SKILL.md                  ← chapter objectives, topic order, prerequisites
        ├── manifest.json
        └── topics/
            └── linear-equations/     ← Topic skill
                ├── SKILL.md          ← tutor guidance (tone, misconceptions, rules)
                ├── lesson.json       ← ordered steps + branches (the orchestration)
                ├── assessment.json   ← practice questions, answer keys, worked solutions
                ├── manifest.json     ← assets, sizes, checksums, required components
                └── assets/
                    ├── images/
                    ├── video/
                    └── board/        ← board scenes used by steps
```

### 3.1. `lesson.json` step (illustrative) 🟡
```json
{
  "id": "step-3",
  "goal": "Distinguish distance from displacement",
  "show": { "component": "track_sim", "params": { "moves": [100, -100] } },
  "tutor_note": "Ask the student to predict before revealing.",
  "check": {
    "type": "numeric",
    "prompt": "A car drives 100 m east then 100 m west. Displacement?",
    "answer": 0, "unit": "m", "tolerance": 0
  },
  "on_correct": "step-4",
  "misconceptions": [
    { "match": { "value": 200 }, "label": "distance-vs-displacement", "goto": "step-3b" }
  ],
  "on_wrong_default": "hint-then-retry"
}
```

### 3.2. Requirements on the format
* A topic must be playable with **only its own folder + parent `SKILL.md` summaries** loaded.
* Every check must have a deterministic answer key (or a rubric flagged for model-explained feedback only).
* Every asset referenced must exist and be checksummed (validator enforces).
* Each topic declares the **components** it needs and the **minimum tier** (e.g., vision) if any.

---

## 4. Skill Runtime (Hybrid Guided Steps) 🟡

### 4.1. Progressive loading
| Moment | Loaded into model context |
| :--- | :--- |
| App start | Nothing from skills |
| Subject opened | Subject `SKILL.md` summary + chapter list |
| Chapter opened | Chapter `SKILL.md` summary + topic list |
| Topic started | Topic `SKILL.md` guidance |
| Each step | **Only current step** (goal, tutor note, check, misconceptions) + compact state |
| Topic ended | Topic context unloaded; progress saved |

### 4.2. Step engine (deterministic code)
1. Enter step → auto-show its resource/component → model introduces it.
2. Student interacts / answers → **grader** checks against answer key.
3. Correct → Rive celebrates + Lottie FX → `on_correct`.
4. Wrong → match misconception → branch, or hint → retry.
5. Side questions → model answers within topic scope, then returns to step.

### 4.3. Model tool contract (initial) 🟡
| Tool | Purpose |
| :--- | :--- |
| `say(text, emotion)` | Speak/display text; drives Rive state |
| `show_resource(id)` | Show an image/video/board scene **from the current step's allowed list** |
| `give_hint(level)` | Reveal authored hint (if present) or model hint |
| `explain_mistake(text)` | Explain why an answer was wrong (after grader verdict) |

The model **cannot** advance, skip, or grade — the runtime does.

### 4.4. Context budget
* Small fixed system prompt (persona + rules).
* Current-step block + rolling compact state (not full transcript).
* Target budgets to be measured on real devices per tier (❓).

---

## 5. Teaching Experience (UI)

| Element | Description |
| :--- | :--- |
| **Library browser** | Subject → chapter → topic, showing installed / downloadable / size |
| **Lesson screen** | Rive tutor + active area (board / component / media) + chat/voice input |
| **Revamped board** | Interactive: pan/zoom, tap elements for explanation, step scrubber, prediction pauses (❓ final gesture set) |
| **Component registry** | Native Compose components driven by skill data (MCQ, numeric, label-diagram, drag-match, simulations…) (❓ native vs WebView) |
| **Media** | Offline image viewer, local video player |
| **Rive tutor** | States: idle, explaining, listening, thinking, celebrating, encouraging |
| **Lottie FX** | Correct answers, topic completion, streaks |
| **Voice** | Offline speech in (Vosk) and out (Android TTS) |

---

## 6. Skill Manager & Distribution

### 6.1. Authoring pipeline (Dev PC) 🟡
1. Author skill folders in a content repo.
2. Run **validator** (schema, asset existence, checksums, answer keys, size budget).
3. Run **packager** → zip per installable unit + update `catalog.json`.
4. Upload with `gcloud storage cp` to the bucket.

### 6.2. On-device
* Fetch `catalog.json` → show available subjects/chapters/topics with sizes.
* Download (WorkManager, resumable) → verify checksum → unpack → index in Room.
* Update when catalog version is newer; delete to free space.
* Everything usable offline after install.

### 6.3. Open
* Download unit: subject / chapter / topic (❓)
* Bucket access: public HTTPS / signed URLs / Firebase Storage; bucket name (❓)
* Separate "lite" (no video) downloads (❓)

---

## 7. AI Engine & Tier Manager

| Task | Notes |
| :--- | :--- |
| Port LiteRT-LM engine from Genie | Refactor `GenieEngine` + download worker into `engine/` |
| Model catalog | Gemma 4 (high tier), Gemma 3n or lighter (low tier) — **exact model files, sizes and LiteRT-LM support to be verified before implementation** |
| Tier detection | Automatic benchmark vs user choice (❓) |
| Feature gating API | `tier.can(Feature.VISION)` used by Scribe/Health UI |
| Model hosting | Same GCS bucket or official source (❓) |

---

## 8. Document Feature (base, all devices)

| Task | Notes |
| :--- | :--- |
| Import PDF from device storage | Storage Access Framework |
| Offline text extraction | Library choice to verify (Genie flow as reference) |
| Chunking + local index | Room + FTS for retrieval |
| Summaries, key notes, quizzes | Model works on retrieved chunks only (small context) |
| Link to skills | ❓ e.g., "teach me this section" using matching topic skill |

---

## 9. Scribe Feature (tier-gated parts)

| Capability | Low tier | High tier |
| :--- | :--- | :--- |
| Record audio | ✅ | ✅ |
| Offline transcription | ✅ (Vosk) | ✅ |
| Structured study notes from transcript | ✅ | ✅ |
| Photo of board/slide merged with notes | ❌ | ✅ (vision) |

❓ Final modes (lecture notes only, or keep Genie's medical SOAP mode).

---

## 10. Health Feature (tier-gated parts)

| Capability | Low tier | High tier |
| :--- | :--- | :--- |
| Offline health topics library (Genie WHO JSON as reference) | ✅ | ✅ |
| Food analysis by photo | ❌ | ✅ (vision) |
| Food analysis by text/catalog | ❓ fallback | ✅ |

---

## 11. Content Plan

| Task | Notes |
| :--- | :--- |
| Define chapter/topic tree for each of the 5 subjects | Level-agnostic; depth expressed by which chapters/topics exist |
| Author first topic skills end-to-end | ❓ demo depth (e.g., 1 deep topic per subject vs 1 full subject) |
| Source curated resources | ❓ own content / open-licensed (with attribution) / AI-drafted + human-reviewed |
| Review checklist per topic | Accuracy, answer keys, misconceptions, asset licensing, size |

---

## 12. Phased Roadmap

| Phase | Deliverable | Depends on |
| :--- | :--- | :--- |
| **P0 Foundations** ✅ | Repo, Gradle, Compose, Rive, Lottie compile | — |
| **P1 Skill Format Spec** | Final schema for `SKILL.md`, `lesson.json`, `assessment.json`, `manifest.json`, `catalog.json` + one hand-written sample topic | Approve §3–4 |
| **P2 Authoring Toolkit** | Validator + packager scripts, GCS upload procedure | P1, bucket decision |
| **P3 AI Engine + Tier Manager** | LiteRT-LM running both tiers on device, gating API | Model verification |
| **P4 Skill Runtime** | Loader, step engine, grader, context builder, tool contract | P1, P3 |
| **P5 Teaching UI** | Library browser, lesson screen, Rive tutor, media, first components, revamped board | P4 |
| **P6 Skill Manager** | Catalog, download, verify, install, update, delete | P2 |
| **P7 First Content** | First topic skills across subjects | P1, P5 |
| **P8 Document** | PDF import → summaries/quizzes offline | P3 |
| **P9 Scribe** | Audio → notes; vision capture on high tier | P3 |
| **P10 Health** | Offline library; vision food analysis + fallback | P3 |
| **P11 Validation & Demo** | Airplane-mode demo, low-end device test, small student test | All |

---

## 13. Evaluation & Student Testing (track criteria)

* **Zero-bandwidth proof:** full lesson in airplane mode on a low-end device.
* **Accuracy:** answer-key grading; reviewed content; model limited to current step.
* **Privacy:** all learning data stays on device.
* **Effectiveness:** small pre/post quiz with a few students on one topic (❓ who/where).
* **Performance:** time-to-first-token, RAM, battery, download size per tier.

---

## 14. Risks

| Risk | Mitigation |
| :--- | :--- |
| Low-end model ignores instructions | Step-only context, short prompts, runtime controls flow |
| Model hallucination in explanations | Curated resources in context, answer-key grading, scope limits |
| Package sizes too large for data-limited users | Size budgets, per-unit downloads, optional media |
| Gemma variant not supported/too big on LiteRT-LM | Verify early (P3) before building around it |
| Content creation is slow | Prioritize depth on few topics for demo; reusable components |
| Interactive component variety limited | Component registry designed for reuse across subjects |

---

## 15. Open Decisions (consolidated)

1. 🟡 Approve hybrid guided-steps orchestration (§4).
2. 🟡 Approve skill package format (§3).
3. ❓ Download unit: subject / chapter / topic.
4. ❓ GCS access model + bucket name/project.
5. ❓ Interactive components: native registry / WebView bundles / both.
6. ❓ Revamped board: final interaction set; standalone mode or a component inside lessons.
7. ❓ Media size budgets; optional "lite" downloads.
8. ❓ Content sourcing and licensing.
9. ❓ Hackathon demo depth and deadline.
10. ❓ Tier detection: automatic vs user choice; model hosting location.
11. ❓ Scribe modes; Health low-tier food fallback.
12. ❓ Local progress tracking scope.
