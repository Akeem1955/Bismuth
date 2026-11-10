package com.bismuth.app.skills

import android.content.Context
import com.bismuth.app.teaching.BoardArrow
import com.bismuth.app.teaching.BoardObject
import com.bismuth.app.teaching.BoardPoint
import com.bismuth.app.teaching.BoardSize
import com.bismuth.app.teaching.BoardStep
import com.bismuth.app.teaching.BoardStyle
import com.bismuth.app.teaching.StepInlineQuiz
import com.bismuth.app.teaching.TeachingBoardScene

/**
 * Layer 1: Skill Instructions (tells the LLM how to orchestrate the lesson).
 */
data class TopicSkillInstructions(
    val skillId: String,
    val learningObjectives: List<String>,
    val explanationSequence: List<String>,
    val commonMisconceptions: List<String>,
    val socraticSystemPrompt: String,
)

/**
 * Layer 2: Curated Learning Resources (verified educational content & visual references).
 */
data class CuratedResource(
    val id: String,
    val title: String,
    val type: String, // "formula_sheet", "diagram", "worked_example", "offline_animation"
    val summary: String,
    val bodyText: String,
)

/**
 * Complete Topic Skill Package combining all 3 layers:
 * 1. Skill Instructions
 * 2. Curated Learning Resources
 * 3. Interactive Components (Whiteboard Scene + Tap-to-Explain Nodes + Inline Step Quizzes)
 */
data class TopicSkillPackage(
    val topicId: String,
    val subjectId: String,
    val chapterId: String,
    val title: String,
    val summary: String,
    val packageSizeKb: Int,
    val gcsObjectPath: String,
    val bundledByDefault: Boolean = true,
    val instructions: TopicSkillInstructions,
    val resources: List<CuratedResource>,
    val whiteboardScene: TeachingBoardScene,
)

data class ChapterSkillNode(
    val chapterId: String,
    val subjectId: String,
    val title: String,
    val summary: String,
    val chapterObjectives: List<String>,
    val topics: List<TopicSkillPackage>,
)

data class SubjectSkillCoordinator(
    val subjectId: String,
    val title: String,
    val iconEmoji: String,
    val accentHex: String,
    val tagline: String,
    val coordinatorPhilosophy: String,
    val gcsBucketUrl: String,
    val chapters: List<ChapterSkillNode>,
)

object SkillInstallPrefs {
    private const val PREFS_NAME = "bismuth_skill_manager"

    fun isTopicInstalled(context: Context, topic: TopicSkillPackage): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(topic.topicId)) {
            return topic.bundledByDefault
        }
        return prefs.getBoolean(topic.topicId, topic.bundledByDefault)
    }

    fun setTopicInstalled(context: Context, topicId: String, installed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(topicId, installed)
            .apply()
    }
}

object SubjectSkillCatalog {

    const val GCS_BUCKET_BASE = "https://storage.googleapis.com/bismuth-skills-hub/v1"

    val ALL_SUBJECTS: List<SubjectSkillCoordinator> by lazy {
        listOf(
            buildMathematicsSubject(),
            buildPhysicsSubject(),
            buildChemistrySubject(),
            buildBiologySubject(),
            buildEnglishSubject(),
        )
    }

    fun getSubject(subjectId: String): SubjectSkillCoordinator {
        return ALL_SUBJECTS.firstOrNull { it.subjectId == subjectId } ?: ALL_SUBJECTS.first()
    }

    /**
     * Builds the compact, on-demand context window payload for the active Topic & Step only,
     * preventing LLM context overload.
     */
    fun buildStepScopedPrompt(
        subject: SubjectSkillCoordinator,
        chapter: ChapterSkillNode,
        topic: TopicSkillPackage,
        step: BoardStep,
        studentQuestion: String,
    ): String {
        return buildString {
            appendLine("[ACTIVE SKILL ORCHESTRATOR: ${subject.title} > ${chapter.title} > ${topic.title}]")
            appendLine("Coordinator Rule: ${topic.instructions.socraticSystemPrompt}")
            appendLine("Current Step (${step.stepNumber}): ${step.title}")
            appendLine("Step Narration: ${step.narration}")
            appendLine("Common Misconceptions to Watch: ${topic.instructions.commonMisconceptions.joinToString("; ")}")
            appendLine()
            appendLine("Student Question: $studentQuestion")
            appendLine("Respond concisely in 2-4 sentences grounded strictly in this step.")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. MATHEMATICS
    // ═══════════════════════════════════════════════════════════════════════════
    private fun buildMathematicsSubject(): SubjectSkillCoordinator {
        val circlesScene = TeachingBoardScene(
            sceneId = "math_geo_circles",
            subjectId = "mathematics",
            chapterTitle = "Geometry",
            topicTitle = "Circles: Theorems, Arcs & Sectors",
            objects = listOf(
                BoardObject(
                    objectId = "node_center",
                    objectType = "circle",
                    label = "Center (O) & Radius (r)",
                    subLabel = "C = 2πr | A = πr²",
                    explanation = "Every point on the circumference is equidistant from Center O by length r. Doubling the radius quadruples the total area (A = πr²).",
                    keyFormulaOrRule = "Circumference = 2πr, Area = πr²",
                    position = BoardPoint(80f, 70f),
                    size = BoardSize(240f, 110f),
                    style = BoardStyle(fillColor = "#1E293B", strokeColor = "#38BDF8", accentColor = "#38BDF8"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "node_chord_tangent",
                    objectType = "box",
                    label = "Chord vs. Tangent",
                    subLabel = "Radius ⊥ Tangent at contact",
                    explanation = "A chord connects two points on the circle. A tangent touches the circle at exactly one point (P) and is always perpendicular (90°) to the radius OP.",
                    keyFormulaOrRule = "∠OPT = 90° (Radius ⊥ Tangent)",
                    position = BoardPoint(400f, 70f),
                    size = BoardSize(250f, 110f),
                    style = BoardStyle(fillColor = "#1E293B", strokeColor = "#A855F7", accentColor = "#A855F7"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "node_inscribed_angle",
                    objectType = "card",
                    label = "Inscribed Angle Theorem",
                    subLabel = "Central Angle = 2 × Inscribed",
                    explanation = "The angle subtended by an arc at the center (2θ) is always double the angle subtended by the same arc at any point on the remaining circumference (θ).",
                    keyFormulaOrRule = "θ_center = 2 × θ_circumference",
                    position = BoardPoint(80f, 240f),
                    size = BoardSize(260f, 115f),
                    style = BoardStyle(fillColor = "#0F172A", strokeColor = "#10B981", accentColor = "#10B981"),
                    stepId = "step_2",
                ),
                BoardObject(
                    objectId = "node_semicircle",
                    objectType = "box",
                    label = "Thales' Semicircle Rule",
                    subLabel = "Angle in Semicircle = 90°",
                    explanation = "Because a diameter forms a 180° central angle, any inscribed angle subtended by a diameter is half of 180°, which is always a right angle (90°).",
                    keyFormulaOrRule = "Diameter subtends 90° on circumference",
                    position = BoardPoint(400f, 240f),
                    size = BoardSize(250f, 115f),
                    style = BoardStyle(fillColor = "#0F172A", strokeColor = "#F59E0B", accentColor = "#F59E0B"),
                    stepId = "step_2",
                ),
                BoardObject(
                    objectId = "node_arc_sector",
                    objectType = "card",
                    label = "Arc Length & Sector Area",
                    subLabel = "Fraction (θ/360°) of Circle",
                    explanation = "A sector with central angle θ (in degrees) is simply the fraction (θ / 360°) of the entire circle's circumference or area. In radians: L = rθ and A = ½r²θ.",
                    keyFormulaOrRule = "L = (θ/360°)·2πr | A = (θ/360°)·πr²",
                    position = BoardPoint(230f, 410f),
                    size = BoardSize(300f, 115f),
                    style = BoardStyle(fillColor = "#172554", strokeColor = "#00E5FF", accentColor = "#00E5FF"),
                    stepId = "step_3",
                ),
            ),
            arrows = listOf(
                BoardArrow("node_center", "node_chord_tangent", "90° at P", "step_1"),
                BoardArrow("node_center", "node_inscribed_angle", "2θ vs θ", "step_2"),
                BoardArrow("node_inscribed_angle", "node_semicircle", "2θ = 180°", "step_2"),
                BoardArrow("node_inscribed_angle", "node_arc_sector", "Arc fraction", "step_3"),
            ),
            steps = listOf(
                BoardStep(
                    stepId = "step_1",
                    stepNumber = 1,
                    title = "Radius, Chords & Tangent Perpendicularity",
                    narration = "Tap either node on the board to inspect its geometric proof. A tangent touches a circle at one point and forms a strict 90° right angle with the radius drawn to that point.",
                    tutorTip = "Tap 'Chord vs. Tangent' on the board to see why ∠OPT = 90°.",
                    focusObjectIds = listOf("node_center", "node_chord_tangent"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "What angle does a tangent line make with the radius at the point of contact?",
                        basicOptions = listOf("45°", "60°", "90°", "180°"),
                        basicCorrectIndex = 2,
                        basicExplanation = "A radius drawn to the point of tangency is always perpendicular (90°) to the tangent line.",
                        advancedQuestion = "A circle has radius r = 5 cm. A tangent segment PT is drawn from external point P such that OP = 13 cm. What is the length of tangent PT?",
                        advancedOptions = listOf("8 cm", "12 cm", "14 cm", "18 cm"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "Since ∠OPT = 90°, triangle OPT is right-angled at T: PT = √(OP² - r²) = √(13² - 5²) = √(169 - 25) = 12 cm.",
                    ),
                ),
                BoardStep(
                    stepId = "step_2",
                    stepNumber = 2,
                    title = "Inscribed Angle & Thales' Semicircle Theorem",
                    narration = "The central angle subtended by an arc is always twice the inscribed angle on the circumference. When the chord is a diameter (180°), the angle on the semicircle is 90°.",
                    tutorTip = "Tap 'Inscribed Angle Theorem' or 'Thales' Semicircle Rule' for worked formulas.",
                    focusObjectIds = listOf("node_inscribed_angle", "node_semicircle"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "If an arc subtends a central angle of 80° at O, what is the inscribed angle on the circumference?",
                        basicOptions = listOf("20°", "40°", "80°", "160°"),
                        basicCorrectIndex = 1,
                        basicExplanation = "The inscribed angle is half of the central angle subtended by the same arc: 80° ÷ 2 = 40°.",
                        advancedQuestion = "AB is a diameter of a circle with center O, and C is a point on the circumference. If ∠CAB = 37°, what is ∠CBA?",
                        advancedOptions = listOf("37°", "43°", "53°", "90°"),
                        advancedCorrectIndex = 2,
                        advancedExplanation = "By Thales' theorem, ∠ACB = 90°. Therefore in right triangle ABC, ∠CBA = 90° - 37° = 53°.",
                    ),
                ),
                BoardStep(
                    stepId = "step_3",
                    stepNumber = 3,
                    title = "Arc Length & Sector Area",
                    narration = "An arc or sector is a proportional slice (θ / 360°) of the full circle. In university calculus and physics, measuring θ in radians simplifies this to L = rθ and Area = ½r²θ.",
                    tutorTip = "Tap 'Arc Length & Sector Area' to compare degree and radian formulas.",
                    focusObjectIds = listOf("node_arc_sector"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "What fraction of a full circle's area is a sector with central angle 90°?",
                        basicOptions = listOf("1/2", "1/3", "1/4", "1/6"),
                        basicCorrectIndex = 2,
                        basicExplanation = "90° / 360° = 1/4 of the circle's area.",
                        advancedQuestion = "A sector of radius r = 6 m subtends an angle of θ = π/3 radians. What is the exact area of the sector?",
                        advancedOptions = listOf("2π m²", "3π m²", "6π m²", "12π m²"),
                        advancedCorrectIndex = 2,
                        advancedExplanation = "Using A = ½r²θ: A = ½ × (6²) × (π/3) = 18 × (π/3) = 6π m².",
                    ),
                ),
            ),
        )

        val linearEqScene = TeachingBoardScene(
            sceneId = "math_alg_linear",
            subjectId = "mathematics",
            chapterTitle = "Algebra",
            topicTitle = "Linear Equations & Slope-Intercept Systems",
            objects = listOf(
                BoardObject(
                    objectId = "alg_balance",
                    objectType = "box",
                    label = "Equality Balance Principle",
                    subLabel = "ax + b = c ⟹ ax = c - b",
                    explanation = "An equation is a balanced scale. Every inverse operation applied to the left-hand side must be identically applied to the right-hand side to preserve equivalence.",
                    keyFormulaOrRule = "x = (c - b) / a  (for a ≠ 0)",
                    position = BoardPoint(80f, 80f),
                    size = BoardSize(250f, 110f),
                    style = BoardStyle(fillColor = "#1E293B", strokeColor = "#38BDF8", accentColor = "#38BDF8"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "alg_slope",
                    objectType = "card",
                    label = "Slope-Intercept Form",
                    subLabel = "y = mx + c",
                    explanation = "m = Δy / Δx represents the rate of change (gradient), while c is the y-intercept where the line crosses x = 0.",
                    keyFormulaOrRule = "m = (y₂ - y₁) / (x₂ - x₁)",
                    position = BoardPoint(390f, 80f),
                    size = BoardSize(250f, 110f),
                    style = BoardStyle(fillColor = "#0F172A", strokeColor = "#10B981", accentColor = "#10B981"),
                    stepId = "step_2",
                ),
                BoardObject(
                    objectId = "alg_systems",
                    objectType = "card",
                    label = "Simultaneous Systems",
                    subLabel = "Intersection (x*, y*)",
                    explanation = "Two non-parallel lines intersect at a unique point (x*, y*) solved by substitution or elimination, or matrix inversion AX = B when det(A) ≠ 0.",
                    keyFormulaOrRule = "det(A) = a₁b₂ - a₂b₁ ≠ 0",
                    position = BoardPoint(230f, 260f),
                    size = BoardSize(280f, 115f),
                    style = BoardStyle(fillColor = "#172554", strokeColor = "#00E5FF", accentColor = "#00E5FF"),
                    stepId = "step_3",
                ),
            ),
            arrows = listOf(
                BoardArrow("alg_balance", "alg_slope", "Two variables", "step_2"),
                BoardArrow("alg_slope", "alg_systems", "Pair of lines", "step_3"),
            ),
            steps = listOf(
                BoardStep(
                    stepId = "step_1",
                    stepNumber = 1,
                    title = "Isolating Variables via Inverse Operations",
                    narration = "To solve ax + b = c, undo addition/subtraction first, then undo multiplication/division.",
                    focusObjectIds = listOf("alg_balance"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "Solve for x: 3x - 7 = 14",
                        basicOptions = listOf("x = 5", "x = 7", "x = 9", "x = 21"),
                        basicCorrectIndex = 1,
                        basicExplanation = "Add 7 to both sides: 3x = 21. Divide by 3: x = 7.",
                        advancedQuestion = "For what value of parameter k does the linear equation (k - 2)x = k² - 4 have infinitely many real solutions?",
                        advancedOptions = listOf("k = -2", "k = 0", "k = 2", "No real k"),
                        advancedCorrectIndex = 2,
                        advancedExplanation = "When k = 2, both sides become 0·x = 0, which holds for every real x.",
                    ),
                ),
                BoardStep(
                    stepId = "step_2",
                    stepNumber = 2,
                    title = "Gradient & Linear Graphs (y = mx + c)",
                    narration = "Every two-variable linear equation defines a straight line with constant gradient m = Δy/Δx.",
                    focusObjectIds = listOf("alg_slope"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "What is the gradient (slope) of the line y = -4x + 9?",
                        basicOptions = listOf("-4", "4", "9", "-9/4"),
                        basicCorrectIndex = 0,
                        basicExplanation = "In y = mx + c, the coefficient of x is the gradient m = -4.",
                        advancedQuestion = "Find the equation of the line perpendicular to 2x - 3y = 6 passing through (4, -1).",
                        advancedOptions = listOf("3x + 2y = 10", "2x + 3y = 5", "3x - 2y = 14", "y = -1.5x + 4"),
                        advancedCorrectIndex = 0,
                        advancedExplanation = "Original slope m₁ = 2/3, so perpendicular slope m₂ = -3/2. y + 1 = (-3/2)(x - 4) ⟹ 3x + 2y = 10.",
                    ),
                ),
            ),
        )

        return SubjectSkillCoordinator(
            subjectId = "mathematics",
            title = "Mathematics",
            iconEmoji = "📐",
            accentHex = "#38BDF8",
            tagline = "Algebra, Geometry, Statistics & Calculus",
            coordinatorPhilosophy = "Build intuition visually before formal symbolic manipulation; verify every transformation on the board.",
            gcsBucketUrl = "$GCS_BUCKET_BASE/mathematics/catalog.json",
            chapters = listOf(
                ChapterSkillNode(
                    chapterId = "math_ch_geometry",
                    subjectId = "mathematics",
                    title = "Chapter 1: Geometry",
                    summary = "Angles, lines, triangles, and circle theorems with interactive coordinate proofs.",
                    chapterObjectives = listOf(
                        "Prove and apply tangent-radius perpendicularity",
                        "Relate central and inscribed angles on a circle",
                        "Compute arc lengths and sector areas in degrees and radians",
                    ),
                    topics = listOf(
                        TopicSkillPackage(
                            topicId = "math_topic_circles",
                            subjectId = "mathematics",
                            chapterId = "math_ch_geometry",
                            title = "Circles: Theorems, Arcs & Sectors",
                            summary = "Interactive circle geometry from chords and tangents to radian sector calculus.",
                            packageSizeKb = 420,
                            gcsObjectPath = "mathematics/geometry/circles.skill.zip",
                            bundledByDefault = true,
                            instructions = TopicSkillInstructions(
                                skillId = "math.geometry.circles",
                                learningObjectives = listOf(
                                    "Identify radius, chord, diameter, secant, and tangent",
                                    "Apply the Inscribed Angle and Thales' Semicircle theorems",
                                    "Calculate arc length and sector area in both degrees and radians",
                                ),
                                explanationSequence = listOf(
                                    "Step 1: Center, radius, and tangent perpendicularity",
                                    "Step 2: Central vs. inscribed angle relationship (2θ vs θ)",
                                    "Step 3: Proportional arc length and sector area",
                                ),
                                commonMisconceptions = listOf(
                                    "Confusing circumference (2πr) with area (πr²)",
                                    "Assuming any chord subtends 90° (only a diameter does)",
                                    "Using degrees inside radian formula L = rθ",
                                ),
                                socraticSystemPrompt = "Guide the student using geometric symmetry and visual proportions on the circle diagram.",
                            ),
                            resources = listOf(
                                CuratedResource(
                                    id = "res_circle_theorems",
                                    title = "Circle Theorems Reference Sheet",
                                    type = "formula_sheet",
                                    summary = "Core Euclidean & analytic circle identities",
                                    bodyText = "• Equation of circle centered at (h,k): (x - h)² + (y - k)² = r²\n• Tangent to radius: ∠OPT = 90°\n• Central angle = 2 × Inscribed angle\n• Cyclic quadrilateral opposite angles sum to 180°",
                                ),
                            ),
                            whiteboardScene = circlesScene,
                        ),
                    ),
                ),
                ChapterSkillNode(
                    chapterId = "math_ch_algebra",
                    subjectId = "mathematics",
                    title = "Chapter 2: Algebra",
                    summary = "Algebraic expressions, linear equations, simultaneous systems, and quadratic equations.",
                    chapterObjectives = listOf(
                        "Solve single-variable and parameterized linear equations",
                        "Interpret slope and intercepts geometrically",
                    ),
                    topics = listOf(
                        TopicSkillPackage(
                            topicId = "math_topic_linear_eq",
                            subjectId = "mathematics",
                            chapterId = "math_ch_algebra",
                            title = "Linear Equations & Systems",
                            summary = "Balance principles, slope-intercept geometry, and simultaneous equations.",
                            packageSizeKb = 380,
                            gcsObjectPath = "mathematics/algebra/linear-equations.skill.zip",
                            bundledByDefault = true,
                            instructions = TopicSkillInstructions(
                                skillId = "math.algebra.linear_equations",
                                learningObjectives = listOf("Isolate variables accurately", "Graph y = mx + c", "Solve 2x2 linear systems"),
                                explanationSequence = listOf("Balance operations", "Slope-intercept form", "Simultaneous intersection"),
                                commonMisconceptions = listOf("Sign errors when transposing terms", "Inverting Δx/Δy instead of Δy/Δx"),
                                socraticSystemPrompt = "Use the balance scale analogy and geometric line intersection.",
                            ),
                            resources = listOf(
                                CuratedResource(
                                    id = "res_linear_summary",
                                    title = "Linear Systems & Determinants",
                                    type = "formula_sheet",
                                    summary = "Slope, intercept, and Cramer's rule",
                                    bodyText = "• Slope m = (y₂ - y₁)/(x₂ - x₁)\n• Point-slope: y - y₁ = m(x - x₁)\n• System determinant: D = a₁b₂ - a₂b₁",
                                )
                            ),
                            whiteboardScene = linearEqScene,
                        ),
                    ),
                ),
            ),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. PHYSICS
    // ═══════════════════════════════════════════════════════════════════════════
    private fun buildPhysicsSubject(): SubjectSkillCoordinator {
        val kinematicsScene = TeachingBoardScene(
            sceneId = "phys_mech_kinematics",
            subjectId = "physics",
            chapterTitle = "Mechanics",
            topicTitle = "Kinematics & Projectile Motion",
            objects = listOf(
                BoardObject(
                    objectId = "phys_vectors",
                    objectType = "box",
                    label = "Displacement vs. Distance",
                    subLabel = "Vector Δx vs Scalar d",
                    explanation = "Distance is total path length traveled (scalar). Displacement Δx = x_f - x_i is the straight-line vector from start to finish. A round trip has zero displacement!",
                    keyFormulaOrRule = "v_avg = Δx / Δt",
                    position = BoardPoint(70f, 70f),
                    size = BoardSize(250f, 110f),
                    style = BoardStyle(fillColor = "#1E293B", strokeColor = "#00E5FF", accentColor = "#00E5FF"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "phys_suvat",
                    objectType = "card",
                    label = "Constant Acceleration (SUVAT)",
                    subLabel = "v = u + at | s = ut + ½at²",
                    explanation = "Under constant acceleration a, velocity changes linearly with time (v = u + at) and position changes quadratically (s = ut + ½at², v² = u² + 2as).",
                    keyFormulaOrRule = "v² = u² + 2as",
                    position = BoardPoint(390f, 70f),
                    size = BoardSize(260f, 110f),
                    style = BoardStyle(fillColor = "#0F172A", strokeColor = "#10B981", accentColor = "#10B981"),
                    stepId = "step_2",
                ),
                BoardObject(
                    objectId = "phys_projectile",
                    objectType = "card",
                    label = "2D Projectile Independence",
                    subLabel = "x(t) = (v₀cosθ)t | y(t) = (v₀sinθ)t - ½gt²",
                    explanation = "Horizontal motion has zero acceleration (v_x = v₀cosθ is constant), while vertical motion accelerates downward at g = 9.8 m/s². At the highest point, v_y = 0 but v_x = v₀cosθ!",
                    keyFormulaOrRule = "Range R = (v₀² sin 2θ) / g",
                    position = BoardPoint(210f, 250f),
                    size = BoardSize(310f, 120f),
                    style = BoardStyle(fillColor = "#172554", strokeColor = "#F59E0B", accentColor = "#F59E0B"),
                    stepId = "step_3",
                ),
            ),
            arrows = listOf(
                BoardArrow("phys_vectors", "phys_suvat", "dv/dt = a", "step_2"),
                BoardArrow("phys_suvat", "phys_projectile", "2D Orthogonal Split", "step_3"),
            ),
            steps = listOf(
                BoardStep(
                    stepId = "step_1",
                    stepNumber = 1,
                    title = "Scalars vs. Vectors: Distance and Displacement",
                    narration = "Tap 'Displacement vs. Distance' on the board. Velocity depends on net displacement vector Δx, not total footsteps.",
                    focusObjectIds = listOf("phys_vectors"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "A runner completes one full 400 m lap around a circular track and returns to the starting line. What is their net displacement?",
                        basicOptions = listOf("0 m", "200 m", "400 m", "800 m"),
                        basicCorrectIndex = 0,
                        basicExplanation = "Because start and end positions are identical, Δx = x_f - x_i = 0 m.",
                        advancedQuestion = "A particle moves along x(t) = 3t² - 12t + 5 (meters). At what time t is its instantaneous velocity zero?",
                        advancedOptions = listOf("t = 1 s", "t = 2 s", "t = 3 s", "t = 4 s"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "v(t) = dx/dt = 6t - 12 = 0 ⟹ t = 2 s.",
                    ),
                ),
                BoardStep(
                    stepId = "step_2",
                    stepNumber = 2,
                    title = "Equations of Uniformly Accelerated Motion",
                    narration = "When acceleration is constant, the area under the v-t graph gives displacement s = ut + ½at².",
                    focusObjectIds = listOf("phys_suvat"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "A car starts from rest (u = 0) and accelerates at 4 m/s² for 5 seconds. What is its final velocity?",
                        basicOptions = listOf("9 m/s", "15 m/s", "20 m/s", "50 m/s"),
                        basicCorrectIndex = 2,
                        basicExplanation = "v = u + at = 0 + (4 m/s²)(5 s) = 20 m/s.",
                        advancedQuestion = "A braking train decelerates uniformly from 30 m/s to 10 m/s over a distance of 160 m. What is the magnitude of its deceleration?",
                        advancedOptions = listOf("1.5 m/s²", "2.5 m/s²", "3.0 m/s²", "5.0 m/s²"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "Using v² = u² + 2as: 100 = 900 + 2(a)(160) ⟹ -800 = 320a ⟹ a = -2.5 m/s².",
                    ),
                ),
                BoardStep(
                    stepId = "step_3",
                    stepNumber = 3,
                    title = "Projectile Motion: Orthogonal Independence",
                    narration = "Gravity acts only vertically! Horizontal velocity v_x = v₀cosθ remains constant throughout flight.",
                    focusObjectIds = listOf("phys_projectile"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "At the very top (apex) of a projectile's trajectory, what is its vertical velocity v_y?",
                        basicOptions = listOf("0 m/s", "9.8 m/s", "v₀", "Maximum"),
                        basicCorrectIndex = 0,
                        basicExplanation = "At the highest point, the projectile momentarily stops rising before falling, so v_y = 0.",
                        advancedQuestion = "For a projectile launched on level ground at speed v₀, which launch angle θ maximizes horizontal range R (neglecting air drag)?",
                        advancedOptions = listOf("30°", "45°", "60°", "75°"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "Since R = (v₀² sin 2θ)/g, R is maximized when sin(2θ) = 1 ⟹ 2θ = 90° ⟹ θ = 45°.",
                    ),
                ),
            ),
        )

        return SubjectSkillCoordinator(
            subjectId = "physics",
            title = "Physics",
            iconEmoji = "⚡",
            accentHex = "#F59E0B",
            tagline = "Mechanics, Electricity, Waves & Optics",
            coordinatorPhilosophy = "Physics is Physics across every level: decompose phenomena into conservation laws, vectors, and rates of change.",
            gcsBucketUrl = "$GCS_BUCKET_BASE/physics/catalog.json",
            chapters = listOf(
                ChapterSkillNode(
                    chapterId = "phys_ch_mechanics",
                    subjectId = "physics",
                    title = "Chapter 1: Mechanics & Kinematics",
                    summary = "Vectors, SUVAT equations, projectile motion, and Newton's laws of motion.",
                    chapterObjectives = listOf("Distinguish vector displacement from scalar distance", "Resolve 2D projectile motion into independent axes"),
                    topics = listOf(
                        TopicSkillPackage(
                            topicId = "phys_topic_kinematics",
                            subjectId = "physics",
                            chapterId = "phys_ch_mechanics",
                            title = "Kinematics & Projectile Motion",
                            summary = "1D & 2D motion, SUVAT equations, and parabolic trajectories.",
                            packageSizeKb = 510,
                            gcsObjectPath = "physics/mechanics/kinematics.skill.zip",
                            bundledByDefault = true,
                            instructions = TopicSkillInstructions(
                                skillId = "physics.mechanics.kinematics",
                                learningObjectives = listOf("Apply SUVAT equations", "Decompose initial velocity into v₀cosθ and v₀sinθ"),
                                explanationSequence = listOf("Vectors vs scalars", "1D constant acceleration", "2D projectile decomposition"),
                                commonMisconceptions = listOf("Thinking total velocity is zero at projectile apex (v_x is still v₀cosθ)", "Mixing signs of upward u and downward g"),
                                socraticSystemPrompt = "Always ask the student to separate horizontal and vertical components first.",
                            ),
                            resources = listOf(
                                CuratedResource(
                                    id = "res_phys_suvat",
                                    title = "Kinematics & Projectile Equations",
                                    type = "formula_sheet",
                                    summary = "Vector kinematics & calculus derivatives",
                                    bodyText = "• v = dx/dt, a = dv/dt\n• v = u + at\n• s = ut + ½at²\n• v² = u² + 2as\n• Projectile Time of Flight T = (2v₀sinθ)/g\n• Max Height H = (v₀²sin²θ)/(2g)",
                                )
                            ),
                            whiteboardScene = kinematicsScene,
                        ),
                    ),
                ),
            ),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 3. BIOLOGY
    // ═══════════════════════════════════════════════════════════════════════════
    private fun buildBiologySubject(): SubjectSkillCoordinator {
        val photosynthesisScene = TeachingBoardScene(
            sceneId = "bio_cell_photosynthesis",
            subjectId = "biology",
            chapterTitle = "Cell Biology & Bioenergetics",
            topicTitle = "Photosynthesis: Light & Calvin Reactions",
            objects = listOf(
                BoardObject(
                    objectId = "bio_chloroplast",
                    objectType = "box",
                    label = "Chloroplast Inputs",
                    subLabel = "6CO₂ + 6H₂O + Light Energy",
                    explanation = "Chlorophyll pigments inside thylakoid membranes absorb red and blue wavelengths of sunlight to drive endergonic carbon fixation.",
                    keyFormulaOrRule = "6CO₂ + 6H₂O → C₆H₁₂O₆ + 6O₂",
                    position = BoardPoint(70f, 70f),
                    size = BoardSize(250f, 110f),
                    style = BoardStyle(fillColor = "#064E3B", strokeColor = "#10B981", accentColor = "#10B981"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "bio_light_rxn",
                    objectType = "card",
                    label = "Light-Dependent Stage",
                    subLabel = "Thylakoid: Photolysis → O₂ + ATP + NADPH",
                    explanation = "Light splits water (2H₂O → 4H⁺ + 4e⁻ + O₂). All oxygen gas released by plants comes from water (H₂O), not from CO₂! Electrons flow through PSII and PSI to generate ATP and NADPH.",
                    keyFormulaOrRule = "2H₂O → O₂ + 4H⁺ + 4e⁻",
                    position = BoardPoint(390f, 70f),
                    size = BoardSize(260f, 115f),
                    style = BoardStyle(fillColor = "#1E293B", strokeColor = "#F59E0B", accentColor = "#F59E0B"),
                    stepId = "step_2",
                ),
                BoardObject(
                    objectId = "bio_calvin",
                    objectType = "card",
                    label = "Calvin Cycle (Stroma)",
                    subLabel = "RuBisCO fixes CO₂ → G3P → Glucose",
                    explanation = "In the chloroplast stroma, enzyme RuBisCO fixes CO₂ onto RuBP (5C), using ATP and NADPH from the light stage to reduce 3-PGA into G3P sugars.",
                    keyFormulaOrRule = "3CO₂ + 9ATP + 6NADPH → G3P",
                    position = BoardPoint(220f, 250f),
                    size = BoardSize(290f, 115f),
                    style = BoardStyle(fillColor = "#172554", strokeColor = "#38BDF8", accentColor = "#38BDF8"),
                    stepId = "step_3",
                ),
            ),
            arrows = listOf(
                BoardArrow("bio_chloroplast", "bio_light_rxn", "Photons + H₂O", "step_2"),
                BoardArrow("bio_light_rxn", "bio_calvin", "ATP + NADPH", "step_3"),
            ),
            steps = listOf(
                BoardStep(
                    stepId = "step_1",
                    stepNumber = 1,
                    title = "Overall Equation & Chloroplast Compartments",
                    narration = "Photosynthesis converts solar photon energy into chemical bond energy inside the chloroplast: Thylakoids (light reactions) and Stroma (Calvin cycle).",
                    focusObjectIds = listOf("bio_chloroplast"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "Which organelle in plant cells is the site of photosynthesis?",
                        basicOptions = listOf("Mitochondrion", "Chloroplast", "Ribosome", "Golgi apparatus"),
                        basicCorrectIndex = 1,
                        basicExplanation = "Chloroplasts contain chlorophyll in thylakoid membranes to capture light.",
                        advancedQuestion = "Why do plant leaves appear green under white light?",
                        advancedOptions = listOf(
                            "Chlorophyll absorbs green light most strongly",
                            "Chlorophyll absorbs red and blue wavelengths and reflects/transmits green",
                            "Carotenoids emit green fluorescence",
                            "Stroma enzymes reflect infrared",
                        ),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "Chlorophyll a and b have absorption peaks in the blue (~430–450 nm) and red (~640–660 nm) regions, reflecting green light.",
                    ),
                ),
                BoardStep(
                    stepId = "step_2",
                    stepNumber = 2,
                    title = "Thylakoid Light Reactions & Water Photolysis",
                    narration = "In Photosystem II, light splits water molecules (photolysis), releasing O₂ gas and pumping protons to synthesize ATP and NADPH.",
                    focusObjectIds = listOf("bio_light_rxn"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "Where does the oxygen gas (O₂) released during photosynthesis come from?",
                        basicOptions = listOf("Carbon dioxide (CO₂)", "Splitting of water (H₂O)", "Glucose", "Soil nitrates"),
                        basicCorrectIndex = 1,
                        basicExplanation = "Photolysis of water (2H₂O → 4H⁺ + 4e⁻ + O₂) at Photosystem II is the sole source of evolved O₂.",
                        advancedQuestion = "What are the two high-energy carrier molecules produced by the light-dependent reactions to power the Calvin cycle?",
                        advancedOptions = listOf("ATP and NADH", "ATP and NADPH", "ADP and NADP⁺", "FADH₂ and Pyruvate"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "Non-cyclic photophosphorylation produces ATP (via chemiosmosis) and NADPH (via ferredoxin-NADP⁺ reductase).",
                    ),
                ),
                BoardStep(
                    stepId = "step_3",
                    stepNumber = 3,
                    title = "The Calvin Cycle: Carbon Fixation in the Stroma",
                    narration = "Using ATP and NADPH from the thylakoids, the enzyme RuBisCO fixes CO₂ into 3-carbon sugar phosphates (G3P) in the stroma.",
                    focusObjectIds = listOf("bio_calvin"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "In which part of the chloroplast does the Calvin cycle take place?",
                        basicOptions = listOf("Outer membrane", "Stroma", "Cell wall", "Vacuole"),
                        basicCorrectIndex = 1,
                        basicExplanation = "The soluble enzymes of carbon fixation (including RuBisCO) reside in the chloroplast stroma.",
                        advancedQuestion = "Which enzyme catalyzes the carboxylation of ribulose-1,5-bisphosphate (RuBP) with CO₂?",
                        advancedOptions = listOf("ATP Synthase", "RuBisCO", "Hexokinase", "Catalase"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "Ribulose-1,5-bisphosphate carboxylase-oxygenase (RuBisCO) fixes CO₂ onto the 5-carbon RuBP.",
                    ),
                ),
            ),
        )

        return SubjectSkillCoordinator(
            subjectId = "biology",
            title = "Biology",
            iconEmoji = "🌿",
            accentHex = "#10B981",
            tagline = "Cell Biology, Genetics, Ecology & Physiology",
            coordinatorPhilosophy = "Connect molecular pathways to cellular structure and organismal function.",
            gcsBucketUrl = "$GCS_BUCKET_BASE/biology/catalog.json",
            chapters = listOf(
                ChapterSkillNode(
                    chapterId = "bio_ch_cells",
                    subjectId = "biology",
                    title = "Chapter 1: Cell Biology & Bioenergetics",
                    summary = "Organelles, membrane transport, photosynthesis, and cellular respiration.",
                    chapterObjectives = listOf("Trace electron and carbon flow in photosynthesis", "Distinguish thylakoid and stroma reactions"),
                    topics = listOf(
                        TopicSkillPackage(
                            topicId = "bio_topic_photosynthesis",
                            subjectId = "biology",
                            chapterId = "bio_ch_cells",
                            title = "Photosynthesis: Light & Calvin Cycle",
                            summary = "Complete multimedia topic package on photolysis, electron transport, and carbon fixation.",
                            packageSizeKb = 640,
                            gcsObjectPath = "biology/cell-biology/photosynthesis.skill.zip",
                            bundledByDefault = true,
                            instructions = TopicSkillInstructions(
                                skillId = "biology.cells.photosynthesis",
                                learningObjectives = listOf("Write the balanced equation", "Explain photolysis of H₂O", "Trace ATP & NADPH into the Calvin cycle"),
                                explanationSequence = listOf("Chloroplast compartments", "Light-dependent reactions", "Calvin cycle carbon fixation"),
                                commonMisconceptions = listOf("Believing O₂ comes from CO₂ instead of H₂O", "Thinking the Calvin cycle only runs in the dark"),
                                socraticSystemPrompt = "Emphasize how the Light Reactions supply ATP and NADPH to the Calvin Cycle.",
                            ),
                            resources = listOf(
                                CuratedResource(
                                    id = "res_bio_photo",
                                    title = "Photosynthesis Pathway Summary",
                                    type = "diagram",
                                    summary = "Thylakoid Z-scheme & Stroma Calvin cycle",
                                    bodyText = "• Net Reaction: 6CO₂ + 6H₂O + Light → C₆H₁₂O₆ + 6O₂\n• Light Stage (Thylakoid): H₂O split, O₂ released, ATP & NADPH formed\n• Calvin Cycle (Stroma): CO₂ fixed by RuBisCO using ATP & NADPH into G3P",
                                )
                            ),
                            whiteboardScene = photosynthesisScene,
                        ),
                    ),
                ),
            ),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 4. CHEMISTRY
    // ═══════════════════════════════════════════════════════════════════════════
    private fun buildChemistrySubject(): SubjectSkillCoordinator {
        val bondingScene = TeachingBoardScene(
            sceneId = "chem_bonding",
            subjectId = "chemistry",
            chapterTitle = "Atoms & Chemical Bonding",
            topicTitle = "Ionic, Covalent & Metallic Bonding",
            objects = listOf(
                BoardObject(
                    objectId = "chem_octet",
                    objectType = "box",
                    label = "Valence Shell & Electronegativity",
                    subLabel = "ΔEN governs electron transfer vs sharing",
                    explanation = "Atoms react to achieve a stable noble-gas valence configuration. The electronegativity difference (ΔEN) between two atoms determines whether electrons are transferred (ionic) or shared (covalent).",
                    keyFormulaOrRule = "ΔEN > 1.7 → Ionic | ΔEN < 1.7 → Covalent",
                    position = BoardPoint(80f, 80f),
                    size = BoardSize(260f, 115f),
                    style = BoardStyle(fillColor = "#1E293B", strokeColor = "#A855F7", accentColor = "#A855F7"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "chem_ionic",
                    objectType = "card",
                    label = "Ionic Lattice (e.g. NaCl)",
                    subLabel = "Cation M⁺ + Anion X⁻ Electrostatic Lattice",
                    explanation = "Metals lose valence electrons to form cations (Na⁺); non-metals gain them to form anions (Cl⁻). Strong 3D Coulombic lattice enthalpy gives high melting points and molten conductivity.",
                    keyFormulaOrRule = "E_lattice ∝ (q⁺ · q⁻) / r₀",
                    position = BoardPoint(400f, 80f),
                    size = BoardSize(250f, 115f),
                    style = BoardStyle(fillColor = "#0F172A", strokeColor = "#F59E0B", accentColor = "#F59E0B"),
                    stepId = "step_2",
                ),
                BoardObject(
                    objectId = "chem_covalent",
                    objectType = "card",
                    label = "Covalent Orbital Overlap",
                    subLabel = "σ (head-on) & π (sideways) bonds",
                    explanation = "Non-metals share electron pairs via orbital overlap. Single bonds are σ bonds; double bonds consist of 1 σ + 1 π bond; triple bonds consist of 1 σ + 2 π bonds.",
                    keyFormulaOrRule = "Double bond = 1σ + 1π | Triple = 1σ + 2π",
                    position = BoardPoint(230f, 255f),
                    size = BoardSize(280f, 115f),
                    style = BoardStyle(fillColor = "#172554", strokeColor = "#00E5FF", accentColor = "#00E5FF"),
                    stepId = "step_2",
                ),
            ),
            arrows = listOf(
                BoardArrow("chem_octet", "chem_ionic", "Large ΔEN (Transfer)", "step_2"),
                BoardArrow("chem_octet", "chem_covalent", "Small ΔEN (Share)", "step_2"),
            ),
            steps = listOf(
                BoardStep(
                    stepId = "step_1",
                    stepNumber = 1,
                    title = "Valence Electrons & Electronegativity Difference",
                    narration = "Chemical bonding lowers potential energy by stabilizing valence orbitals. Tap 'Valence Shell & Electronegativity' to inspect ΔEN thresholds.",
                    focusObjectIds = listOf("chem_octet"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "Which electrons in an atom are primarily involved in chemical bonding?",
                        basicOptions = listOf("Core 1s electrons", "Outer valence electrons", "Neutrons", "Protons"),
                        basicCorrectIndex = 1,
                        basicExplanation = "Outer-shell valence electrons experience the lowest effective nuclear charge and participate in bonding.",
                        advancedQuestion = "Which trend correctly describes Pauling electronegativity across a period and down a group?",
                        advancedOptions = listOf(
                            "Increases across a period, decreases down a group",
                            "Decreases across a period, increases down a group",
                            "Remains constant across a period",
                            "Increases both across and down",
                        ),
                        advancedCorrectIndex = 0,
                        advancedExplanation = "Increased nuclear charge and smaller atomic radius increase electronegativity across a period; added shells shield the nucleus down a group.",
                    ),
                ),
                BoardStep(
                    stepId = "step_2",
                    stepNumber = 2,
                    title = "Ionic Lattices vs. Covalent σ/π Bonds",
                    narration = "Compare electrostatic lattice attraction (Na⁺Cl⁻) with directional orbital overlap in covalent molecules.",
                    focusObjectIds = listOf("chem_ionic", "chem_covalent"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "Why does solid NaCl NOT conduct electricity, whereas molten or aqueous NaCl does?",
                        basicOptions = listOf(
                            "Ions are locked in fixed lattice positions in the solid but mobile when molten/dissolved",
                            "Water creates free electrons",
                            "NaCl is covalent when solid",
                            "Sodium metal precipitates in water",
                        ),
                        basicCorrectIndex = 0,
                        basicExplanation = "Electrical conduction in electrolytes requires mobile charge carriers (free ions).",
                        advancedQuestion = "How many sigma (σ) and pi (π) bonds are present in ethene (C₂H₄)?",
                        advancedOptions = listOf("4 σ and 2 π", "5 σ and 1 π", "6 σ and 0 π", "3 σ and 3 π"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "Four C–H single bonds (4 σ) plus one C=C double bond (1 σ + 1 π) = 5 σ and 1 π.",
                    ),
                ),
            ),
        )

        return SubjectSkillCoordinator(
            subjectId = "chemistry",
            title = "Chemistry",
            iconEmoji = "🧪",
            accentHex = "#A855F7",
            tagline = "Atoms, Bonding, Reactions & Organic Chemistry",
            coordinatorPhilosophy = "Link macroscopic chemical behavior to subatomic electronic structure and thermodynamics.",
            gcsBucketUrl = "$GCS_BUCKET_BASE/chemistry/catalog.json",
            chapters = listOf(
                ChapterSkillNode(
                    chapterId = "chem_ch_bonding",
                    subjectId = "chemistry",
                    title = "Chapter 1: Atomic Structure & Bonding",
                    summary = "Electronegativity, ionic lattices, covalent hybridization, and intermolecular forces.",
                    chapterObjectives = listOf("Predict bond polarity from ΔEN", "Differentiate σ and π covalent bonds"),
                    topics = listOf(
                        TopicSkillPackage(
                            topicId = "chem_topic_bonding",
                            subjectId = "chemistry",
                            chapterId = "chem_ch_bonding",
                            title = "Ionic, Covalent & Orbital Bonding",
                            summary = "Interactive breakdown of electron transfer, lattice energy, and σ/π bonds.",
                            packageSizeKb = 460,
                            gcsObjectPath = "chemistry/bonding/chemical-bonding.skill.zip",
                            bundledByDefault = true,
                            instructions = TopicSkillInstructions(
                                skillId = "chemistry.bonding.overview",
                                learningObjectives = listOf("Distinguish ionic vs covalent bonding", "Count σ and π bonds"),
                                explanationSequence = listOf("Valence shells & ΔEN", "Ionic lattice vs covalent overlap"),
                                commonMisconceptions = listOf("Thinking ionic compounds exist as discrete NaCl molecules rather than 3D lattices"),
                                socraticSystemPrompt = "Ask the student to compare the electronegativities of the bonding elements.",
                            ),
                            resources = listOf(
                                CuratedResource(
                                    id = "res_chem_bonds",
                                    title = "Bonding & Hybridization Guide",
                                    type = "formula_sheet",
                                    summary = "Born-Lande lattice & VSEPR geometry",
                                    bodyText = "• Linear (2 domains): sp, 180°\n• Trigonal planar (3 domains): sp², 120°\n• Tetrahedral (4 domains): sp³, 109.5°",
                                )
                            ),
                            whiteboardScene = bondingScene,
                        ),
                    ),
                ),
            ),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 5. ENGLISH
    // ═══════════════════════════════════════════════════════════════════════════
    private fun buildEnglishSubject(): SubjectSkillCoordinator {
        val grammarScene = TeachingBoardScene(
            sceneId = "eng_grammar_clauses",
            subjectId = "english",
            chapterTitle = "Grammar & Syntax",
            topicTitle = "Sentence Structure, Clauses & Subject-Verb Concord",
            objects = listOf(
                BoardObject(
                    objectId = "eng_independent",
                    objectType = "box",
                    label = "Independent Clause",
                    subLabel = "Subject + Finite Verb (Complete Thought)",
                    explanation = "An independent (main) clause contains a subject and a finite predicate verb and can stand alone as a grammatically complete sentence.",
                    keyFormulaOrRule = "S + V (+ O/C) = Complete Sentence",
                    position = BoardPoint(80f, 80f),
                    size = BoardSize(250f, 110f),
                    style = BoardStyle(fillColor = "#1E293B", strokeColor = "#EC4899", accentColor = "#EC4899"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "eng_dependent",
                    objectType = "card",
                    label = "Subordinate Clause",
                    subLabel = "Starts with Subordinator (Although, Because, Which)",
                    explanation = "A subordinate (dependent) clause functions as a noun, adjective, or adverbial modifier and cannot stand alone without an independent clause.",
                    keyFormulaOrRule = "Subordinator + S + V → Modifies Main Clause",
                    position = BoardPoint(390f, 80f),
                    size = BoardSize(255f, 110f),
                    style = BoardStyle(fillColor = "#0F172A", strokeColor = "#38BDF8", accentColor = "#38BDF8"),
                    stepId = "step_1",
                ),
                BoardObject(
                    objectId = "eng_concord",
                    objectType = "card",
                    label = "Subject-Verb Concord",
                    subLabel = "Head Noun Governs Verb Number",
                    explanation = "Intervening prepositional phrases ('along with', 'as well as', 'of the students') never change the grammatical number of the true head subject!",
                    keyFormulaOrRule = "The box [of tools] IS heavy (Singular Head)",
                    position = BoardPoint(220f, 255f),
                    size = BoardSize(290f, 115f),
                    style = BoardStyle(fillColor = "#172554", strokeColor = "#10B981", accentColor = "#10B981"),
                    stepId = "step_2",
                ),
            ),
            arrows = listOf(
                BoardArrow("eng_dependent", "eng_independent", "Attaches to", "step_1"),
                BoardArrow("eng_independent", "eng_concord", "Head Noun Agreement", "step_2"),
            ),
            steps = listOf(
                BoardStep(
                    stepId = "step_1",
                    stepNumber = 1,
                    title = "Independent vs. Subordinate Clauses",
                    narration = "Every complex sentence pairs a self-contained Independent Clause with one or more Subordinate Clauses.",
                    focusObjectIds = listOf("eng_independent", "eng_dependent"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "In 'Although it rained heavily, the match continued', which part is the independent clause?",
                        basicOptions = listOf(
                            "Although it rained heavily",
                            "the match continued",
                            "rained heavily",
                            "Although the match",
                        ),
                        basicCorrectIndex = 1,
                        basicExplanation = "'The match continued' expresses a complete thought and can stand alone as a sentence.",
                        advancedQuestion = "What grammatical function does the subordinate clause perform in: 'The book that you lent me is insightful'?",
                        advancedOptions = listOf(
                            "Adverbial clause of concession",
                            "Restrictive relative (adjectival) clause modifying 'book'",
                            "Nominal clause acting as direct object",
                            "Independent coordinate clause",
                        ),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "'that you lent me' is a restrictive relative clause post-modifying the head noun 'book'.",
                    ),
                ),
                BoardStep(
                    stepId = "step_2",
                    stepNumber = 2,
                    title = "Proximity & Accompaniment Concord Rules",
                    narration = "Ignore intervening phrases like 'together with' or 'along with'—match the verb strictly to the head noun!",
                    focusObjectIds = listOf("eng_concord"),
                    inlineQuiz = StepInlineQuiz(
                        basicQuestion = "Choose the correct verb: 'The bouquet of red roses ___ on the table.'",
                        basicOptions = listOf("are", "is", "were", "have been"),
                        basicCorrectIndex = 1,
                        basicExplanation = "The singular head noun is 'bouquet' (not 'roses'), so it takes the singular verb 'is'.",
                        advancedQuestion = "Choose the grammatically standard form: 'Neither the professor nor the teaching assistants ___ present at the briefing.'",
                        advancedOptions = listOf("was", "were", "is", "has been"),
                        advancedCorrectIndex = 1,
                        advancedExplanation = "By the rule of proximity concord with 'neither...nor', the verb agrees with the nearer subject ('assistants' → plural 'were').",
                    ),
                ),
            ),
        )

        return SubjectSkillCoordinator(
            subjectId = "english",
            title = "English",
            iconEmoji = "📚",
            accentHex = "#EC4899",
            tagline = "Grammar, Comprehension, Vocabulary & Literature",
            coordinatorPhilosophy = "Analyze syntax structurally so clarity, concord, and rhetorical precision become effortless.",
            gcsBucketUrl = "$GCS_BUCKET_BASE/english/catalog.json",
            chapters = listOf(
                ChapterSkillNode(
                    chapterId = "eng_ch_grammar",
                    subjectId = "english",
                    title = "Chapter 1: Grammar & Sentence Syntax",
                    summary = "Clause hierarchies, subject-verb concord, tense aspect, and rhetorical precision.",
                    chapterObjectives = listOf("Distinguish independent and subordinate clauses", "Master head-noun and proximity concord"),
                    topics = listOf(
                        TopicSkillPackage(
                            topicId = "eng_topic_clauses_concord",
                            subjectId = "english",
                            chapterId = "eng_ch_grammar",
                            title = "Clauses & Subject-Verb Concord",
                            summary = "Interactive syntactic parsing of clauses and agreement traps.",
                            packageSizeKb = 350,
                            gcsObjectPath = "english/grammar/clauses-concord.skill.zip",
                            bundledByDefault = true,
                            instructions = TopicSkillInstructions(
                                skillId = "english.grammar.clauses_concord",
                                learningObjectives = listOf("Identify main vs subordinate clauses", "Resolve tricky subject-verb concord"),
                                explanationSequence = listOf("Clause anatomy", "Head-noun vs proximity concord"),
                                commonMisconceptions = listOf("Matching the verb to a noun inside a prepositional phrase instead of the head subject"),
                                socraticSystemPrompt = "Ask the student to bracket out prepositional phrases first to isolate the true subject.",
                            ),
                            resources = listOf(
                                CuratedResource(
                                    id = "res_eng_concord",
                                    title = "Concord & Clause Reference",
                                    type = "formula_sheet",
                                    summary = "Core rules of syntactic agreement",
                                    bodyText = "• X along with Y → verb agrees with X\n• Either X or Y / Neither X nor Y → verb agrees with nearer noun Y\n• Each / Every + singular noun → singular verb",
                                )
                            ),
                            whiteboardScene = grammarScene,
                        ),
                    ),
                ),
            ),
        )
    }
}
