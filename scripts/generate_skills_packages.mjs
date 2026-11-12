import { execFileSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const WORKSPACE_ROOT = path.resolve(__dirname, "..");
const DATA_JSON_PATH = path.join(__dirname, "skills_data.json");

const BUCKET_NAME = "bismuth-skills-tunjiax-wallet-482614";
const GCS_BASE_URL = `https://storage.googleapis.com/${BUCKET_NAME}/v1`;
const OUTPUT_DIR = path.join(WORKSPACE_ROOT, "skills-packages", "v1");
const ASSETS_SKILLS_DIR = path.join(WORKSPACE_ROOT, "app", "src", "main", "assets", "skills");
const BUNDLED_TOPIC_IDS = new Set(["math_linear_equations", "phys_newton_second_law"]);

const subjectsData = JSON.parse(fs.readFileSync(DATA_JSON_PATH, "utf-8"));

function sha256Bytes(buf) {
  return crypto.createHash("sha256").update(buf).digest("hex");
}

function renderSubjectSkillMd(subj) {
  return `---
name: ${subj.subjectId}-coordinator
description: Subject coordinator skill for ${subj.title} in Bismuth Offline AI Tutor.
subject_id: ${subj.subjectId}
version: "1.1.0"
---

# ${subj.title} — Subject Coordinator Skill

## Tagline
${subj.tagline}

## Teaching Philosophy
${subj.coordinatorPhilosophy}

## Chapters
- **${subj.chapter.title}** (\`${subj.chapter.chapterId}\`): ${subj.chapter.summary}
`;
}

function renderChapterSkillMd(subj, chap) {
  const objs = chap.chapterObjectives.map((o) => `- ${o}`).join("\n");
  const topics = chap.topics.map((t) => `- **${t.title}** (\`${t.topicId}\`): ${t.summary}`).join("\n");
  return `---
name: ${chap.chapterId}-chapter
description: Chapter organizer skill for ${chap.title} (${subj.title}).
subject_id: ${subj.subjectId}
chapter_id: ${chap.chapterId}
version: "1.1.0"
---

# ${chap.title} — Chapter Skill

## Summary
${chap.summary}

## Chapter Objectives
${objs}

## Topics in This Chapter
${topics}
`;
}

function renderTopicSkillMd(subj, chap, topic) {
  const instr = topic.instructions;
  const objs = instr.learningObjectives.map((o, i) => `${i + 1}. ${o}`).join("\n");
  const seq = instr.explanationSequence.map((s) => `- ${s}`).join("\n");
  const misc = instr.commonMisconceptions.map((m) => `- ${m}`).join("\n");
  const stepMediaLines = topic.whiteboardScene.steps
    .map(
      (st) =>
        `- **Step ${st.stepNumber} (${st.title})**: Curated Diagram \`assets/images/step_${st.stepNumber}.png\` + Walkthrough Video \`assets/video/walkthrough.mp4\` (Phase ${st.stepNumber})`
    )
    .join("\n");

  return `---
name: ${topic.topicId}
description: ${topic.summary}
subject_id: ${subj.subjectId}
chapter_id: ${chap.chapterId}
topic_id: ${topic.topicId}
version: "1.1.0"
---

# ${topic.title}

## Socratic System Directive
${instr.socraticSystemPrompt}

## Learning Objectives
${objs}

## Step-by-Step Orchestration Sequence
${seq}

## Teacher-Curated Offline Media Alignment
${stepMediaLines}
- **Interactive Whiteboard Scene**: \`assets/board/scene.json\`

## Common Misconceptions to Watch & Remediate
${misc}
`;
}

fs.rmSync(OUTPUT_DIR, { recursive: true, force: true });
fs.mkdirSync(OUTPUT_DIR, { recursive: true });

fs.rmSync(ASSETS_SKILLS_DIR, { recursive: true, force: true });
fs.mkdirSync(path.join(ASSETS_SKILLS_DIR, "topics"), { recursive: true });

// Step 1: Compile and run GenerateSkillsMedia.java to generate the 30 PNG diagrams + 10 MP4 videos
const jdkBin = "C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot\\bin";
const javacExe = path.join(jdkBin, "javac.exe");
const javaExe = path.join(jdkBin, "java.exe");
const mediaJavaPath = path.join(__dirname, "GenerateSkillsMedia.java");

execFileSync(javacExe, ["-encoding", "UTF-8", mediaJavaPath], { stdio: "inherit" });
execFileSync(javaExe, ["-cp", __dirname, "GenerateSkillsMedia", OUTPUT_DIR], { stdio: "inherit" });
fs.rmSync(path.join(__dirname, "GenerateSkillsMedia.class"), { force: true });

const catalogSubjects = [];

for (const subj of subjectsData) {
  const subjId = subj.subjectId;
  const chap = subj.chapter;
  const chapId = chap.chapterId;

  const subjDir = path.join(OUTPUT_DIR, "subjects", subjId);
  const chapDir = path.join(subjDir, "chapters", chapId);
  fs.mkdirSync(chapDir, { recursive: true });

  fs.writeFileSync(path.join(subjDir, "SKILL.md"), renderSubjectSkillMd(subj), "utf-8");
  fs.writeFileSync(path.join(chapDir, "SKILL.md"), renderChapterSkillMd(subj, chap), "utf-8");

  const catalogTopics = [];

  for (const topic of chap.topics) {
    const topicId = topic.topicId;
    const topicDir = path.join(chapDir, "topics", topicId);
    const boardDir = path.join(topicDir, "assets", "board");
    const imgDir = path.join(topicDir, "assets", "images");
    const vidDir = path.join(topicDir, "assets", "video");
    fs.mkdirSync(boardDir, { recursive: true });
    fs.mkdirSync(imgDir, { recursive: true });
    fs.mkdirSync(vidDir, { recursive: true });

    const relPrefix = `subjects/${subjId}/chapters/${chapId}/topics/${topicId}`;

    // Enrich steps with aligned media references
    const enrichedSteps = topic.whiteboardScene.steps.map((st) => ({
      ...st,
      diagramResourceId: `img_step_${st.stepNumber}`,
      diagramRelativePath: `assets/images/step_${st.stepNumber}.png`,
      videoResourceId: "vid_walkthrough",
      videoRelativePath: "assets/video/walkthrough.mp4",
    }));

    const enrichedScene = {
      ...topic.whiteboardScene,
      steps: enrichedSteps,
    };

    const sceneBytes = Buffer.from(JSON.stringify(enrichedScene, null, 2), "utf-8");
    fs.writeFileSync(path.join(boardDir, "scene.json"), sceneBytes);

    // Read generated PNGs & MP4 to compute sizes and SHA-256 hashes
    const mediaResources = [];
    const mediaManifestEntries = [];

    for (const st of enrichedSteps) {
      const relImgPath = `assets/images/step_${st.stepNumber}.png`;
      const imgBytes = fs.readFileSync(path.join(topicDir, relImgPath));
      const imgHash = sha256Bytes(imgBytes);
      const imgUrl = `${GCS_BASE_URL}/${relPrefix}/${relImgPath}`;

      mediaResources.push({
        id: `img_step_${st.stepNumber}`,
        title: `Step ${st.stepNumber} Visual Diagram: ${st.title}`,
        type: "diagram_image",
        summary: `Teacher-curated diagram aligned with Step ${st.stepNumber} (${st.title}).`,
        bodyText: st.narration,
        stepId: st.stepId,
        relativePath: relImgPath,
        url: imgUrl,
        sha256: imgHash,
        sizeBytes: imgBytes.length,
      });

      mediaManifestEntries.push({
        relativePath: relImgPath,
        url: imgUrl,
        sha256: imgHash,
        sizeBytes: imgBytes.length,
        mimeType: "image/png",
      });
    }

    const relVidPath = "assets/video/walkthrough.mp4";
    const vidBytes = fs.readFileSync(path.join(topicDir, relVidPath));
    const vidHash = sha256Bytes(vidBytes);
    const vidUrl = `${GCS_BASE_URL}/${relPrefix}/${relVidPath}`;

    mediaResources.push({
      id: "vid_walkthrough",
      title: `Animated Concept Walkthrough: ${topic.title}`,
      type: "instructional_video",
      summary: `12-second teacher-curated animated walkthrough covering Steps 1–3 of ${topic.title}.`,
      bodyText: enrichedSteps.map((s) => `Step ${s.stepNumber} (${s.title}): ${s.narration}`).join("\n\n"),
      stepId: "all",
      relativePath: relVidPath,
      url: vidUrl,
      sha256: vidHash,
      sizeBytes: vidBytes.length,
    });

    mediaManifestEntries.push({
      relativePath: relVidPath,
      url: vidUrl,
      sha256: vidHash,
      sizeBytes: vidBytes.length,
      mimeType: "video/mp4",
    });

    const relScenePath = "assets/board/scene.json";
    const sceneHash = sha256Bytes(sceneBytes);
    const sceneUrl = `${GCS_BASE_URL}/${relPrefix}/${relScenePath}`;
    mediaManifestEntries.push({
      relativePath: relScenePath,
      url: sceneUrl,
      sha256: sceneHash,
      sizeBytes: sceneBytes.length,
      mimeType: "application/json",
    });

    const baseResources = topic.resources.map((r) => ({
      ...r,
      stepId: "all",
      relativePath: "",
      url: "",
      sha256: "",
      sizeBytes: 0,
    }));

    const allResources = [...baseResources, ...mediaResources];

    const skillMdStr = renderTopicSkillMd(subj, chap, topic);
    const skillMdBytes = Buffer.from(skillMdStr, "utf-8");
    fs.writeFileSync(path.join(topicDir, "SKILL.md"), skillMdBytes);

    const lessonPayload = {
      topicId,
      subjectId: subjId,
      chapterId: chapId,
      title: topic.title,
      summary: topic.summary,
      instructions: topic.instructions,
      resources: allResources,
      whiteboardScene: enrichedScene,
    };
    const lessonBytes = Buffer.from(JSON.stringify(lessonPayload, null, 2), "utf-8");
    fs.writeFileSync(path.join(topicDir, "lesson.json"), lessonBytes);

    const assessmentPayload = {
      topicId,
      questions: topic.assessment,
    };
    const assessmentBytes = Buffer.from(JSON.stringify(assessmentPayload, null, 2), "utf-8");
    fs.writeFileSync(path.join(topicDir, "assessment.json"), assessmentBytes);

    const bundlePayload = {
      topicId,
      subjectId: subjId,
      subjectTitle: subj.title,
      chapterId: chapId,
      chapterTitle: chap.title,
      title: topic.title,
      summary: topic.summary,
      version: "1.1.0",
      skillMarkdown: skillMdStr,
      instructions: topic.instructions,
      resources: allResources,
      whiteboardScene: enrichedScene,
      assessment: topic.assessment,
      mediaFiles: mediaManifestEntries,
    };
    const bundleBytes = Buffer.from(JSON.stringify(bundlePayload, null, 2), "utf-8");
    fs.writeFileSync(path.join(topicDir, "bundle.json"), bundleBytes);

    const manifestFilesMap = {
      "SKILL.md": {
        sizeBytes: skillMdBytes.length,
        sha256: sha256Bytes(skillMdBytes),
        url: `${GCS_BASE_URL}/${relPrefix}/SKILL.md`,
      },
      "lesson.json": {
        sizeBytes: lessonBytes.length,
        sha256: sha256Bytes(lessonBytes),
        url: `${GCS_BASE_URL}/${relPrefix}/lesson.json`,
      },
      "assessment.json": {
        sizeBytes: assessmentBytes.length,
        sha256: sha256Bytes(assessmentBytes),
        url: `${GCS_BASE_URL}/${relPrefix}/assessment.json`,
      },
      "bundle.json": {
        sizeBytes: bundleBytes.length,
        sha256: sha256Bytes(bundleBytes),
        url: `${GCS_BASE_URL}/${relPrefix}/bundle.json`,
      },
    };

    for (const mf of mediaManifestEntries) {
      manifestFilesMap[mf.relativePath] = {
        sizeBytes: mf.sizeBytes,
        sha256: mf.sha256,
        url: mf.url,
        mimeType: mf.mimeType,
      };
    }

    const manifestPayload = {
      topicId,
      subjectId: subjId,
      chapterId: chapId,
      version: "1.1.0",
      files: manifestFilesMap,
    };
    const manifestBytes = Buffer.from(JSON.stringify(manifestPayload, null, 2), "utf-8");
    fs.writeFileSync(path.join(topicDir, "manifest.json"), manifestBytes);

    const totalMediaBytes = mediaManifestEntries.reduce((acc, m) => acc + m.sizeBytes, 0);
    const totalPackageBytes = bundleBytes.length + totalMediaBytes;

    const isBundled = BUNDLED_TOPIC_IDS.has(topicId);
    if (isBundled) {
      const assetTopicDir = path.join(ASSETS_SKILLS_DIR, "topics", topicId);
      fs.cpSync(topicDir, assetTopicDir, { recursive: true });
    }

    catalogTopics.push({
      topicId,
      subjectId: subjId,
      chapterId: chapId,
      title: topic.title,
      summary: topic.summary,
      version: "1.1.0",
      bundledInApk: isBundled,
      packageSizeBytes: totalPackageBytes,
      bundleSha256: sha256Bytes(bundleBytes),
      bundleUrl: `${GCS_BASE_URL}/${relPrefix}/bundle.json`,
      skillMdUrl: `${GCS_BASE_URL}/${relPrefix}/SKILL.md`,
      lessonUrl: `${GCS_BASE_URL}/${relPrefix}/lesson.json`,
      assessmentUrl: `${GCS_BASE_URL}/${relPrefix}/assessment.json`,
      manifestUrl: `${GCS_BASE_URL}/${relPrefix}/manifest.json`,
      mediaFiles: mediaManifestEntries,
    });
  }

  const chapManifest = {
    chapterId: chapId,
    subjectId: subjId,
    title: chap.title,
    summary: chap.summary,
    chapterObjectives: chap.chapterObjectives,
    skillMdUrl: `${GCS_BASE_URL}/subjects/${subjId}/chapters/${chapId}/SKILL.md`,
    topics: catalogTopics,
  };
  fs.writeFileSync(
    path.join(chapDir, "manifest.json"),
    Buffer.from(JSON.stringify(chapManifest, null, 2), "utf-8")
  );

  const subjManifest = {
    subjectId: subjId,
    title: subj.title,
    iconEmoji: subj.iconEmoji,
    accentHex: subj.accentHex,
    tagline: subj.tagline,
    coordinatorPhilosophy: subj.coordinatorPhilosophy,
    skillMdUrl: `${GCS_BASE_URL}/subjects/${subjId}/SKILL.md`,
    chapters: [chapManifest],
  };
  fs.writeFileSync(
    path.join(subjDir, "manifest.json"),
    Buffer.from(JSON.stringify(subjManifest, null, 2), "utf-8")
  );

  catalogSubjects.push(subjManifest);
}

const catalogRoot = {
  schemaVersion: "1.1.0",
  bucketName: BUCKET_NAME,
  baseUrl: GCS_BASE_URL,
  totalSubjects: catalogSubjects.length,
  totalTopics: catalogSubjects.reduce(
    (acc, s) => acc + s.chapters.reduce((cAcc, c) => cAcc + c.topics.length, 0),
    0
  ),
  bundledTopicIds: Array.from(BUNDLED_TOPIC_IDS).sort(),
  subjects: catalogSubjects,
};

const catalogBytes = Buffer.from(JSON.stringify(catalogRoot, null, 2), "utf-8");
fs.writeFileSync(path.join(OUTPUT_DIR, "catalog.json"), catalogBytes);
fs.writeFileSync(path.join(ASSETS_SKILLS_DIR, "catalog.json"), catalogBytes);

console.log(`Generated ${catalogRoot.totalSubjects} subjects and ${catalogRoot.totalTopics} complete topic modules with curated PNG diagrams & MP4 videos.`);
console.log(`GCS staging directory: ${OUTPUT_DIR}`);
console.log(`APK bundled assets directory: ${ASSETS_SKILLS_DIR}`);
