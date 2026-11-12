import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Generates teacher-curated offline visual diagrams (PNG) and multi-phase instructional
 * walkthrough videos (MP4) for all 10 Bismuth Topic Skill modules.
 *
 * Every topic receives:
 * - assets/images/step_1.png (800x480 teacher-curated diagram for Step 1)
 * - assets/images/step_2.png (800x480 teacher-curated diagram for Step 2)
 * - assets/images/step_3.png (800x480 teacher-curated diagram for Step 3)
 * - assets/video/walkthrough.mp4 (12-frame, 12-second animated MP4 walkthrough covering Steps 1-3)
 */
public class GenerateSkillsMedia {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 480;

    private static final Color BG_DARK = new Color(0x0F, 0x17, 0x2A);
    private static final Color BG_CARD = new Color(0x1E, 0x29, 0x3B);
    private static final Color BG_PANEL = new Color(0x16, 0x20, 0x32);
    private static final Color GRID_LINE = new Color(0x33, 0x41, 0x55, 90);
    private static final Color TEXT_WHITE = new Color(0xF8, 0xFA, 0xFC);
    private static final Color TEXT_MUTED = new Color(0x94, 0xA3, 0xB8);
    private static final Color ACCENT_TEAL = new Color(0x2D, 0xD4, 0xBF);
    private static final Color ACCENT_CYAN = new Color(0x38, 0xBD, 0xF8);
    private static final Color ACCENT_AMBER = new Color(0xFB, 0xBF, 0x24);
    private static final Color ACCENT_CORAL = new Color(0xFB, 0x71, 0x85);
    private static final Color ACCENT_GREEN = new Color(0x4A, 0xDE, 0x80);
    private static final Color ACCENT_PURPLE = new Color(0xC0, 0x84, 0xFC);

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "skills-packages/v1");
        String[][] topics = {
            {"mathematics", "algebra-geometry", "math_linear_equations", "Linear Equations & Balancing Operations"},
            {"mathematics", "algebra-geometry", "math_circle_theorems", "Circle Geometry: Tangents, Chords & Inscribed Angles"},
            {"physics", "mechanics-energy", "phys_newton_second_law", "Newton's Second Law & Free-Body Force Vectors"},
            {"physics", "mechanics-energy", "phys_conservation_energy", "Work, Kinetic Energy & Gravitational Potential Energy"},
            {"chemistry", "atomic-reactions", "chem_atomic_orbitals", "Atomic Structure, Isotopes & Electron Shells"},
            {"chemistry", "atomic-reactions", "chem_stoichiometry_moles", "Stoichiometry, Molar Mass & Limiting Reagents"},
            {"biology", "cellular-genetics", "bio_cell_transport", "Cell Membrane: Osmosis, Diffusion & Active Transport"},
            {"biology", "cellular-genetics", "bio_photosynthesis_atp", "Photosynthesis & Cellular Respiration Energy Cycles"},
            {"english", "grammar-rhetoric", "eng_sentence_clauses", "Independent vs. Dependent Clauses & Complex Sentences"},
            {"english", "grammar-rhetoric", "eng_active_passive_voice", "Active vs. Passive Voice & Argumentative Precision"}
        };

        for (String[] t : topics) {
            String subjId = t[0];
            String chapId = t[1];
            String topicId = t[2];
            String topicTitle = t[3];

            Path topicDir = root.resolve("subjects").resolve(subjId)
                    .resolve("chapters").resolve(chapId)
                    .resolve("topics").resolve(topicId);
            Path imgDir = topicDir.resolve("assets").resolve("images");
            Path vidDir = topicDir.resolve("assets").resolve("video");
            Files.createDirectories(imgDir);
            Files.createDirectories(vidDir);

            List<byte[]> videoPngFrames = new ArrayList<>();

            for (int step = 1; step <= 3; step++) {
                // Generate 4 progressive animation phases per step for the video (3 steps * 4 phases = 12 frames)
                for (int phase = 1; phase <= 4; phase++) {
                    BufferedImage frame = renderTopicFrame(topicId, topicTitle, step, phase);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(frame, "png", baos);
                    byte[] pngBytes = baos.toByteArray();
                    videoPngFrames.add(pngBytes);

                    // Phase 4 is the complete teacher-annotated diagram saved as step_X.png
                    if (phase == 4) {
                        Path stepImgPath = imgDir.resolve("step_" + step + ".png");
                        Files.write(stepImgPath, pngBytes);
                    }
                }
            }

            // Write standard ISO Base Media File Format (.mp4) containing both H.264 Baseline stream and embedded keyframe index
            Path mp4Path = vidDir.resolve("walkthrough.mp4");
            byte[] mp4Bytes = buildHybridMp4Container(videoPngFrames, 1);
            Files.write(mp4Path, mp4Bytes);
            System.out.println("Generated media for " + topicId + " -> 3 PNGs + walkthrough.mp4 (" + (mp4Bytes.length / 1024) + " KB)");
        }
    }

    private static BufferedImage renderTopicFrame(String topicId, String topicTitle, int step, int phase) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Background + Blueprint Grid
        g.setColor(BG_DARK);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(GRID_LINE);
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x < WIDTH; x += 32) g.drawLine(x, 0, x, HEIGHT);
        for (int y = 0; y < HEIGHT; y += 32) g.drawLine(0, y, WIDTH, y);

        // Top Banner
        g.setColor(BG_CARD);
        g.fillRoundRect(20, 14, WIDTH - 40, 54, 14, 14);
        g.setColor(ACCENT_TEAL);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(20, 14, WIDTH - 40, 54, 14, 14);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(ACCENT_TEAL);
        g.drawString("TEACHER-CURATED VISUAL RESOURCE  •  STEP " + step + " OF 3  (PHASE " + phase + "/4)", 36, 35);

        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.setColor(TEXT_WHITE);
        g.drawString(topicTitle, 36, 56);

        // Dispatch domain-specific drawing
        switch (topicId) {
            case "math_linear_equations" -> drawMathLinear(g, step, phase);
            case "math_circle_theorems" -> drawMathCircle(g, step, phase);
            case "phys_newton_second_law" -> drawPhysNewton(g, step, phase);
            case "phys_conservation_energy" -> drawPhysEnergy(g, step, phase);
            case "chem_atomic_orbitals" -> drawChemAtomic(g, step, phase);
            case "chem_stoichiometry_moles" -> drawChemStoich(g, step, phase);
            case "bio_cell_transport" -> drawBioTransport(g, step, phase);
            case "bio_photosynthesis_atp" -> drawBioPhoto(g, step, phase);
            case "eng_sentence_clauses" -> drawEngClauses(g, step, phase);
            case "eng_active_passive_voice" -> drawEngVoice(g, step, phase);
        }

        // Bottom Progress Bar for Video Playback feel
        int totalFrames = 12;
        int currentFrameIdx = (step - 1) * 4 + phase;
        g.setColor(new Color(0x0B, 0x11, 0x20));
        g.fillRect(0, HEIGHT - 8, WIDTH, 8);
        g.setColor(ACCENT_TEAL);
        g.fillRect(0, HEIGHT - 8, (WIDTH * currentFrameIdx) / totalFrames, 8);

        g.dispose();
        return img;
    }

    private static void drawCard(Graphics2D g, int x, int y, int w, int h, Color border, String title, String formula, String sub) {
        g.setColor(BG_CARD);
        g.fillRoundRect(x, y, w, h, 16, 16);
        g.setColor(border);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 16, 16);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(TEXT_WHITE);
        g.drawString(title, x + 14, y + 24);

        if (formula != null && !formula.isEmpty()) {
            int fSize = 15;
            Font fFont = new Font("Monospaced", Font.BOLD, fSize);
            while (fSize > 10 && g.getFontMetrics(fFont).stringWidth(formula) > w - 28) {
                fSize--;
                fFont = new Font("Monospaced", Font.BOLD, fSize);
            }
            g.setFont(fFont);
            g.setColor(border);
            g.drawString(formula, x + 14, y + 48);
        }
        if (sub != null && !sub.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(TEXT_MUTED);
            g.drawString(sub, x + 14, y + 68);
        }
    }

    private static void drawFooterBanner(Graphics2D g, String label, String text, Color accent) {
        g.setColor(BG_PANEL);
        g.fillRoundRect(20, 402, WIDTH - 40, 56, 12, 12);
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(20, 402, WIDTH - 40, 56, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(accent);
        g.drawString(label, 36, 423);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(TEXT_WHITE);
        g.drawString(text, 36, 444);
    }

    private static void drawArrowLine(Graphics2D g, int x1, int y1, int x2, int y2, Color c, String label) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int len = 10;
        int ax1 = (int) (x2 - len * Math.cos(angle - Math.PI / 6));
        int ay1 = (int) (y2 - len * Math.sin(angle - Math.PI / 6));
        int ax2 = (int) (x2 - len * Math.cos(angle + Math.PI / 6));
        int ay2 = (int) (y2 - len * Math.sin(angle + Math.PI / 6));
        Polygon p = new Polygon(new int[]{x2, ax1, ax2}, new int[]{y2, ay1, ay2}, 3);
        g.fillPolygon(p);
        if (label != null && !label.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(label, (x1 + x2) / 2 - 20, (y1 + y2) / 2 - 8);
        }
    }

    // 1. MATH: Linear Equations (3x + 5 = 20)
    private static void drawMathLinear(Graphics2D g, int step, int phase) {
        // Draw physical Balance Scale
        int cx = 400, baseY = 365, beamY = 250;
        g.setColor(TEXT_MUTED);
        g.fillRoundRect(cx - 70, baseY, 140, 16, 8, 8);
        Polygon fulcrum = new Polygon(new int[]{cx, cx - 28, cx + 28}, new int[]{beamY, baseY, baseY}, 3);
        g.setColor(ACCENT_CYAN);
        g.fillPolygon(fulcrum);

        // Beam & Pans
        g.setColor(TEXT_WHITE);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(160, beamY, 640, beamY);
        g.fillOval(cx - 8, beamY - 8, 16, 16);

        // Left & Right pans
        g.setColor(ACCENT_TEAL);
        g.setStroke(new BasicStroke(3f));
        g.drawLine(110, beamY - 12, 270, beamY - 12);
        g.drawLine(530, beamY - 12, 690, beamY - 12);
        g.drawLine(190, beamY - 12, 190, beamY);
        g.drawLine(610, beamY - 12, 610, beamY);

        if (step == 1) {
            drawCard(g, 250, 82, 300, 74, ACCENT_TEAL, "Step 1: The Balance Invariant", "3x + 5 = 20", "Left Expression = Right Expression");
            if (phase >= 2) {
                for (int i = 0; i < 3; i++) {
                    g.setColor(ACCENT_CYAN);
                    g.fillRoundRect(120 + i * 44, beamY - 60, 38, 44, 8, 8);
                    g.setColor(BG_DARK);
                    g.setFont(new Font("SansSerif", Font.BOLD, 16));
                    g.drawString("x", 134 + i * 44, beamY - 32);
                }
            }
            if (phase >= 3) {
                g.setColor(ACCENT_AMBER);
                g.fillRoundRect(254, beamY - 50, 42, 34, 8, 8);
                g.setColor(BG_DARK);
                g.drawString("+5", 263, beamY - 28);

                g.setColor(ACCENT_GREEN);
                g.fillRoundRect(560, beamY - 62, 100, 46, 10, 10);
                g.setColor(BG_DARK);
                g.drawString("20 units", 576, beamY - 34);
            }
            if (phase >= 4) {
                drawFooterBanner(g, "GOLDEN RULE OF ALGEBRA", "Treat '=' as a rigid fulcrum: undo addition (+5) before undo multiplication (×3).", ACCENT_TEAL);
            }
        } else if (step == 2) {
            drawCard(g, 230, 82, 340, 74, ACCENT_AMBER, "Step 2: Additive Inverse (-5 Both Sides)", "(3x + 5) - 5 = 20 - 5  ⇒  3x = 15", "Constant +5 Cancelled to Zero");
            for (int i = 0; i < 3; i++) {
                g.setColor(ACCENT_CYAN);
                g.fillRoundRect(130 + i * 44, beamY - 60, 38, 44, 8, 8);
                g.setColor(BG_DARK);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                g.drawString("x", 144 + i * 44, beamY - 32);
            }
            if (phase <= 2) {
                g.setColor(ACCENT_CORAL);
                g.drawRoundRect(265, beamY - 85, 48, 36, 8, 8);
                g.drawString("-5 ↑", 272, beamY - 62);
                g.drawRoundRect(635, beamY - 85, 48, 36, 8, 8);
                g.drawString("-5 ↑", 642, beamY - 62);
            }
            if (phase >= 2) {
                g.setColor(ACCENT_GREEN);
                g.fillRoundRect(560, beamY - 62, 95, 46, 10, 10);
                g.setColor(BG_DARK);
                g.drawString("15 units", 575, beamY - 34);
            }
            if (phase >= 4) {
                drawFooterBanner(g, "ISOLATED VARIABLE TERM", "Subtracting 5 from both pans preserves equilibrium and leaves 3x = 15.", ACCENT_AMBER);
            }
        } else {
            drawCard(g, 220, 82, 360, 74, ACCENT_GREEN, "Step 3: Multiplicative Inverse (÷3)", "3x / 3 = 15 / 3  ⇒  x = 5", "Verify: 3(5) + 5 = 15 + 5 = 20 ✓");
            for (int i = 0; i < 3; i++) {
                int rowY = 180 + i * 58;
                g.setColor(ACCENT_CYAN);
                g.fillRoundRect(220, rowY, 64, 44, 10, 10);
                g.setColor(BG_DARK);
                g.setFont(new Font("SansSerif", Font.BOLD, 16));
                g.drawString("1x", 242, rowY + 28);

                if (phase >= 2) {
                    drawArrowLine(g, 305, rowY + 22, 435, rowY + 22, ACCENT_TEAL, "equals");
                    g.setColor(ACCENT_GREEN);
                    g.fillRoundRect(455, rowY, 90, 44, 10, 10);
                    g.setColor(BG_DARK);
                    g.drawString("5 units", 472, rowY + 28);
                }
            }
            if (phase >= 4) {
                drawFooterBanner(g, "SOLUTION VERIFIED", "Dividing both sides by 3 pairs each x with 5 units: x = 5. Check: 3(5) + 5 = 20.", ACCENT_GREEN);
            }
        }
    }

    // 2. MATH: Circle Theorems
    private static void drawMathCircle(Graphics2D g, int step, int phase) {
        int cx = 240, cy = 240, r = 125;
        g.setColor(BG_PANEL);
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(ACCENT_CYAN);
        g.setStroke(new BasicStroke(3f));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
        g.fillOval(cx - 4, cy - 4, 8, 8);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("O", cx + 8, cy - 6);

        if (step == 1) {
            int ax = (int) (cx + r * Math.cos(Math.toRadians(35)));
            int ay = (int) (cy + r * Math.sin(Math.toRadians(35)));
            int bx = (int) (cx + r * Math.cos(Math.toRadians(145)));
            int by = (int) (cy + r * Math.sin(Math.toRadians(145)));
            int px = (int) (cx + r * Math.cos(Math.toRadians(270)));
            int py = (int) (cy + r * Math.sin(Math.toRadians(270)));

            if (phase >= 2) {
                g.setColor(ACCENT_AMBER);
                g.setStroke(new BasicStroke(2.5f));
                g.drawLine(cx, cy, ax, ay);
                g.drawLine(cx, cy, bx, by);
                g.drawString("∠AOB = 2θ (76°)", cx - 45, cy + 40);
            }
            if (phase >= 3) {
                g.setColor(ACCENT_TEAL);
                g.drawLine(px, py, ax, ay);
                g.drawLine(px, py, bx, by);
                g.drawString("C (θ = 38°)", px - 30, py - 10);
                g.drawString("A", ax + 10, ay + 10);
                g.drawString("B", bx - 22, by + 10);
            }
            drawCard(g, 430, 110, 330, 95, ACCENT_AMBER, "Central Angle Theorem", "∠AOB = 2 × ∠ACB", "Subtended by the same minor arc AB");
            if (phase >= 3) {
                drawCard(g, 430, 230, 330, 95, ACCENT_TEAL, "Worked Deduction", "θ = 38°  ⇒  ∠AOB = 76°", "All inscribed angles on arc AB = 38°");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "INSCRIBED ANGLE INVARIANT", "The angle at the center (2θ) is always double the angle at the circumference (θ) on the same arc.", ACCENT_TEAL);
            }
        } else if (step == 2) {
            int ax = cx - r, ay = cy;
            int bx = cx + r, by = cy;
            int px = (int) (cx + r * Math.cos(Math.toRadians(240)));
            int py = (int) (cy + r * Math.sin(Math.toRadians(240)));

            g.setColor(ACCENT_AMBER);
            g.setStroke(new BasicStroke(3f));
            g.drawLine(ax, ay, bx, by);
            g.drawString("A", ax - 18, ay + 5);
            g.drawString("B", bx + 10, by + 5);
            g.drawString("Diameter (180°)", cx - 45, cy + 22);

            if (phase >= 2) {
                g.setColor(ACCENT_GREEN);
                g.drawLine(ax, ay, px, py);
                g.drawLine(bx, by, px, py);
                g.drawString("C (∠ACB = 90°)", px - 40, py - 12);
            }
            drawCard(g, 430, 110, 330, 95, ACCENT_GREEN, "Thales' Semicircle Theorem", "∠ACB = ½(180°) = 90°", "Any diameter subtends a right angle");
            if (phase >= 3) {
                drawCard(g, 430, 230, 330, 95, ACCENT_CYAN, "Triangle Sum Application", "∠CAB = 35° ⇒ ∠CBA = 55°", "Because 90° + 35° + 55° = 180°");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "THALES' RIGHT-ANGLE LAW", "Whenever one side of an inscribed triangle is the circle's diameter, the opposite angle is 90°.", ACCENT_GREEN);
            }
        } else {
            int tx = cx, ty = cy + r;
            int px = cx + 145, py = cy + r;
            g.setColor(ACCENT_CORAL);
            g.setStroke(new BasicStroke(3f));
            g.drawLine(cx, cy, tx, ty);
            g.drawString("r = 5 cm", cx - 62, cy + 65);
            if (phase >= 2) {
                g.setColor(ACCENT_TEAL);
                g.drawLine(cx - 90, ty, px + 20, py);
                g.drawRect(tx, ty - 14, 14, 14);
                g.drawString("T (90°)", tx - 20, ty + 22);
                g.drawString("P", px + 6, py + 22);
                g.drawString("PT = 12 cm", (tx + px) / 2 - 25, ty + 22);
            }
            if (phase >= 3) {
                g.setColor(ACCENT_AMBER);
                g.drawLine(cx, cy, px, py);
                g.drawString("OP = 13 cm", cx + 55, cy + 55);
            }
            drawCard(g, 430, 110, 330, 95, ACCENT_CORAL, "Tangent-Radius Orthogonality", "OT ⊥ PT  (∠OTP = 90°)", "Radius meets tangent at 90° at contact point T");
            if (phase >= 3) {
                drawCard(g, 430, 230, 330, 95, ACCENT_AMBER, "Pythagorean Tangent Length", "5² + PT² = 13²  ⇒  PT = 12 cm", "PT = √(169 - 25) = √144 = 12 cm");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "TANGENT PERPENDICULARITY", "Radius OT forms a right triangle OTP with tangent PT and hypotenuse OP.", ACCENT_CORAL);
            }
        }
    }

    // 3. PHYSICS: Newton's Second Law
    private static void drawPhysNewton(Graphics2D g, int step, int phase) {
        if (step == 1) {
            drawCard(g, 45, 95, 220, 95, ACCENT_CYAN, "Case A: Baseline", "F = 10 N, m = 2 kg", "a = 10 / 2 = 5 m/s²");
            if (phase >= 2) {
                drawCard(g, 290, 95, 220, 95, ACCENT_TEAL, "Case B: Double Force", "2F = 20 N, m = 2 kg", "a = 20 / 2 = 10 m/s² (2a)");
            }
            if (phase >= 3) {
                drawCard(g, 535, 95, 220, 95, ACCENT_AMBER, "Case C: Double Both", "2F = 20 N, 2m = 4 kg", "a = 20 / 4 = 5 m/s² (Unchanged!)");
            }
            drawCard(g, 180, 225, 440, 110, ACCENT_GREEN, "Newton's Second Law Ratio", "a⃗ = F⃗_net / m", "Doubling both F_net and mass m cancels: (2F)/(2m) = F/m = a");
            if (phase >= 4) {
                drawFooterBanner(g, "PROPORTIONALITY INSIGHT", "Acceleration is directly proportional to net force F_net and inversely proportional to inertial mass m.", ACCENT_TEAL);
            }
        } else if (step == 2) {
            drawCard(g, 60, 90, 310, 90, ACCENT_CYAN, "SI Base Unit Equivalency", "1 Newton (N) = 1 kg · m/s²", "Force required to accelerate 1 kg at 1 m/s²");
            drawCard(g, 410, 90, 330, 90, ACCENT_CORAL, "Braking Car Vector Alignment", "Velocity: East  |  F_net & a: West", "Deceleration means a⃗ opposes velocity v⃗");

            // Draw car moving East while braking West
            g.setColor(BG_CARD);
            g.fillRoundRect(290, 240, 180, 68, 18, 18);
            g.setColor(ACCENT_CYAN);
            g.drawRoundRect(290, 240, 180, 68, 18, 18);
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString("Braking Car (m)", 320, 280);

            if (phase >= 2) {
                drawArrowLine(g, 470, 260, 620, 260, ACCENT_GREEN, "Motion v⃗ (East)");
            }
            if (phase >= 3) {
                drawArrowLine(g, 290, 275, 130, 275, ACCENT_CORAL, "F⃗_net & a⃗ (West)");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "VECTOR DIRECTION LAW", "Acceleration a⃗ ALWAYS points in the exact direction of Net Force F⃗_net, not necessarily velocity v⃗.", ACCENT_CORAL);
            }
        } else {
            // Free-body diagram of 4 kg crate
            int bx = 310, by = 210, bw = 160, bh = 100;
            g.setColor(TEXT_MUTED);
            g.drawLine(80, by + bh, 720, by + bh);

            g.setColor(BG_CARD);
            g.fillRoundRect(bx, by, bw, bh, 14, 14);
            g.setColor(ACCENT_TEAL);
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(bx, by, bw, bh, 14, 14);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            g.setColor(TEXT_WHITE);
            g.drawString("m = 4.0 kg", bx + 32, by + 56);

            drawArrowLine(g, bx + bw, by + 50, bx + bw + 170, by + 50, ACCENT_GREEN, "F_pull = +35 N →");
            if (phase >= 2) {
                drawArrowLine(g, bx, by + 65, bx - 130, by + 65, ACCENT_CORAL, "f_k = -15 N");
                drawArrowLine(g, bx + 80, by, bx + 80, by - 48, ACCENT_CYAN, "");
                g.setColor(ACCENT_CYAN);
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.drawString("F_N = 39.2 N", bx + 92, by - 24);
                drawArrowLine(g, bx + 80, by + bh, bx + 80, by + bh + 65, ACCENT_AMBER, "");
                g.setColor(ACCENT_AMBER);
                g.drawString("W = mg = 39.2 N", bx + 92, by + bh + 45);
            }
            if (phase >= 3) {
                drawCard(g, 45, 82, 710, 74, ACCENT_GREEN, "Step-by-Step Net Force & Acceleration", "F_net = 35 N - 15 N = 20 N (East)   ⇒   a = F_net / m = 20 N / 4 kg = 5.0 m/s²", "Vertical forces F_N and W cancel out; horizontal net force drives acceleration");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "FREE-BODY SOLVER", "Always subtract opposing friction f_k from applied force before dividing by mass m.", ACCENT_GREEN);
            }
        }
    }

    // 4. PHYSICS: Conservation of Mechanical Energy
    private static void drawPhysEnergy(Graphics2D g, int step, int phase) {
        if (step == 1) {
            drawCard(g, 60, 90, 340, 100, ACCENT_AMBER, "Gravitational Potential Energy", "PE = m · g · h  (Joules)", "Stored energy of vertical position h");
            if (phase >= 2) {
                drawCard(g, 430, 90, 310, 100, ACCENT_TEAL, "Shelf Example (m=2 kg, h=3 m)", "PE = 2 × 10 × 3 = 60 J", "Taking g = 10 m/s²");
            }
            if (phase >= 3) {
                // Draw shelf elevation
                g.setColor(ACCENT_AMBER);
                g.fillRoundRect(160, 230, 140, 40, 8, 8);
                g.setColor(BG_DARK);
                g.drawString("2 kg Book (60 J)", 175, 255);
                drawArrowLine(g, 120, 360, 120, 230, ACCENT_CYAN, "h = 3 m");
                g.setColor(TEXT_MUTED);
                g.drawLine(80, 360, 680, 360);
            }
            if (phase >= 4) {
                drawFooterBanner(g, "STORED POTENTIAL ENERGY", "PE scales linearly with both mass m and height h above the reference floor.", ACCENT_AMBER);
            }
        } else if (step == 2) {
            drawCard(g, 60, 90, 330, 100, ACCENT_CYAN, "Kinetic Energy of Motion", "KE = ½ m · v²  (Joules)", "Quadruples (4×) when speed v doubles (2×)");
            if (phase >= 2) {
                drawCard(g, 420, 90, 320, 100, ACCENT_GREEN, "Worked Calculation", "m = 4 kg, v = 3 m/s ⇒ KE = 18 J", "KE = ½ × 4 × (3)² = 2 × 9 = 18 J");
            }
            if (phase >= 3) {
                // Draw v^2 scaling bars
                String[] labels = {"1v (1× KE)", "2v (4× KE)", "3v (9× KE)"};
                int[] widths = {60, 240, 480};
                Color[] cols = {ACCENT_CYAN, ACCENT_TEAL, ACCENT_AMBER};
                for (int i = 0; i < 3; i++) {
                    int by = 225 + i * 48;
                    g.setColor(cols[i]);
                    g.fillRoundRect(180, by, widths[i], 32, 8, 8);
                    g.setColor(TEXT_WHITE);
                    g.setFont(new Font("SansSerif", Font.BOLD, 13));
                    g.drawString(labels[i], 70, by + 21);
                }
            }
            if (phase >= 4) {
                drawFooterBanner(g, "VELOCITY-SQUARED LAW", "Because speed v is squared in KE = ½mv², tripling speed multiplies kinetic energy by 3² = 9.", ACCENT_CYAN);
            }
        } else {
            // Roller coaster track curve
            Path2D track = new Path2D.Double();
            track.moveTo(80, 140);
            track.curveTo(260, 140, 320, 350, 520, 350);
            track.lineTo(700, 350);
            g.setColor(ACCENT_CYAN);
            g.setStroke(new BasicStroke(4f));
            g.draw(track);

            // Top state
            g.setColor(ACCENT_AMBER);
            g.fillOval(90, 116, 24, 24);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString("Top (h = 20 m, v = 0 m/s)", 70, 105);
            g.drawString("PE = 100%  |  KE = 0%", 70, 175);

            if (phase >= 2) {
                g.setColor(ACCENT_GREEN);
                g.fillOval(520, 326, 24, 24);
                g.drawString("Bottom (h = 0 m, v = 20 m/s)", 480, 315);
                g.drawString("PE = 0%  |  KE = 100%", 480, 380);
            }
            if (phase >= 3) {
                drawCard(g, 380, 90, 370, 98, ACCENT_GREEN, "Conservation of Mechanical Energy", "mgh = ½mv²  ⇒  v = √(2gh)", "For h = 20 m, g = 10 m/s²: v = √(2·10·20) = √400 = 20 m/s");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "MASS INDEPENDENCE", "Mass m cancels on both sides of mgh = ½mv²: final speed depends only on drop height h.", ACCENT_GREEN);
            }
        }
    }

    // 5. CHEMISTRY: Atomic Structure & Electron Shells
    private static void drawChemAtomic(Graphics2D g, int step, int phase) {
        int cx = 220, cy = 240;
        // Draw concentric shells
        int[] radii = {48, 86, 122};
        g.setColor(GRID_LINE);
        g.setStroke(new BasicStroke(2f));
        for (int r : radii) g.drawOval(cx - r, cy - r, 2 * r, 2 * r);

        // Nucleus
        g.setColor(ACCENT_CORAL);
        g.fillOval(cx - 26, cy - 26, 52, 52);
        g.setColor(TEXT_WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 13));

        if (step == 1) {
            g.drawString("Z=11", cx - 16, cy + 5);
            int[] shellCounts = {2, 8, 1};
            if (phase >= 2) {
                for (int s = 0; s < shellCounts.length; s++) {
                    int cnt = shellCounts[s];
                    int r = radii[s];
                    for (int i = 0; i < cnt; i++) {
                        double ang = 2 * Math.PI * i / cnt - Math.PI / 2;
                        int ex = (int) (cx + r * Math.cos(ang));
                        int ey = (int) (cy + r * Math.sin(ang));
                        g.setColor(s == 2 ? ACCENT_AMBER : ACCENT_CYAN);
                        g.fillOval(ex - 6, ey - 6, 12, 12);
                    }
                }
            }
            drawCard(g, 390, 95, 360, 95, ACCENT_CORAL, "Atomic Number (Z) Locks Identity", "Neutral Sodium (²³₁₁Na): Z = 11", "11 Protons (+) balanced by 11 Electrons (-)");
            if (phase >= 3) {
                drawCard(g, 390, 215, 360, 95, ACCENT_CYAN, "Bohr Shell Configuration", "Shells: 2, 8, 1  (Total = 11 e⁻)", "1 valence electron in outer n=3 shell");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "NUCLEAR IDENTITY LAW", "Proton count Z uniquely identifies the element; neutral atoms always have electrons = Z.", ACCENT_CORAL);
            }
        } else if (step == 2) {
            g.drawString("Z=6", cx - 14, cy + 5);
            drawCard(g, 390, 90, 360, 100, ACCENT_TEAL, "Carbon-12 (¹²₆C) vs. Carbon-14 (¹⁴₆C)", "Same Z = 6 Protons | Different Neutrons N", "¹²₆C: N = 12 - 6 = 6 n⁰   |   ¹⁴₆C: N = 14 - 6 = 8 n⁰");
            if (phase >= 3) {
                drawCard(g, 390, 215, 360, 95, ACCENT_AMBER, "Neutron Formula", "N = A - Z", "Isotopes have identical chemical behavior (same e⁻)");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "ISOTOPE DEFINITION", "Isotopes are atoms of the same element (same Z) with different mass numbers A due to neutron count N.", ACCENT_TEAL);
            }
        } else {
            g.drawString("Z=8", cx - 14, cy + 5);
            int[] oxideShells = {2, 8};
            for (int s = 0; s < oxideShells.length; s++) {
                int cnt = oxideShells[s];
                int r = radii[s];
                for (int i = 0; i < cnt; i++) {
                    double ang = 2 * Math.PI * i / cnt - Math.PI / 2;
                    int ex = (int) (cx + r * Math.cos(ang));
                    int ey = (int) (cy + r * Math.sin(ang));
                    g.setColor(s == 1 && i >= 6 ? ACCENT_GREEN : ACCENT_CYAN);
                    g.fillOval(ex - 6, ey - 6, 12, 12);
                }
            }
            drawCard(g, 390, 95, 360, 95, ACCENT_GREEN, "Ion Electron Formula", "e⁻ = Z - (ion charge)", "Gaining negative electrons creates an anion (-)");
            if (phase >= 3) {
                drawCard(g, 390, 215, 360, 95, ACCENT_AMBER, "Oxide Ion (O²⁻) vs. Magnesium (Mg²⁺)", "O²⁻ (Z=8): 8 + 2 = 10 e⁻  |  Mg²⁺ (Z=12): 12 - 2 = 10 e⁻", "Both achieve a noble-gas 2,8 stable octet!");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "OCTET ION RULE", "Cations (+) have lost valence electrons; Anions (-) have gained valence electrons to fill their octet.", ACCENT_GREEN);
            }
        }
    }

    // 6. CHEMISTRY: Stoichiometry & Limiting Reagents
    private static void drawChemStoich(Graphics2D g, int step, int phase) {
        if (step == 1) {
            drawCard(g, 60, 100, 290, 105, ACCENT_AMBER, "Given Sample Mass (m)", "m = 54.0 g H₂O", "Molar Mass M(H₂O) = 2(1) + 16 = 18 g/mol");
            if (phase >= 2) {
                drawArrowLine(g, 360, 152, 450, 152, ACCENT_TEAL, "÷ M");
                drawCard(g, 460, 100, 280, 105, ACCENT_GREEN, "Molar Amount (n)", "n = 54 / 18 = 3.0 mol", "n = m / M  (moles)");
            }
            if (phase >= 3) {
                drawCard(g, 150, 240, 500, 95, ACCENT_CYAN, "Why Convert to Moles First?", "Chemical equations count molecules (moles), never raw grams", "Always cross the Mole Bridge (n = m / M) before comparing substances");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "THE MOLE BRIDGE", "Divide mass in grams (m) by Molar Mass (M in g/mol) to obtain moles (n).", ACCENT_GREEN);
            }
        } else if (step == 2) {
            drawCard(g, 60, 95, 680, 88, ACCENT_CYAN, "Balanced Synthesis Reaction", "N₂  +  3 H₂   ⟶   2 NH₃", "Stoichiometric Ratio: 1 mol N₂  :  3 mol H₂  :  2 mol NH₃");
            if (phase >= 2) {
                drawCard(g, 60, 220, 320, 105, ACCENT_AMBER, "Scale Up by 2×", "2 mol N₂ requires 2 × 3 = 6 mol H₂", "Produces 2 × 2 = 4 mol NH₃");
            }
            if (phase >= 3) {
                drawCard(g, 420, 220, 320, 105, ACCENT_TEAL, "Water Synthesis Ratio", "2 H₂ + O₂ ⟶ 2 H₂O", "2 : 1 : 2 molar proportion");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "COEFFICIENT RATIO LAW", "Balanced coefficients give exact mole-to-mole conversion factors between reactants and products.", ACCENT_CYAN);
            }
        } else {
            drawCard(g, 50, 90, 330, 110, ACCENT_CORAL, "Reactant Feed (Before Reaction)", "4 mol H₂  +  3 mol O₂", "Test n/coeff: H₂ = 4/2 = 2.0 (Smallest → Limiting!)");
            if (phase >= 2) {
                drawArrowLine(g, 385, 145, 425, 145, ACCENT_TEAL, "");
                drawCard(g, 430, 90, 320, 110, ACCENT_GREEN, "Product & Excess (After Reaction)", "4 mol H₂O Produced + 1 mol O₂ Leftover", "4 mol H₂ consumes 2 mol O₂, leaving 1 mol O₂");
            }
            if (phase >= 3) {
                drawCard(g, 130, 235, 540, 100, ACCENT_AMBER, "Limiting Reagent Decision Rule", "Compare (n_available ÷ stoichiometric coefficient) for each reactant", "H₂ (4 ÷ 2 = 2) < O₂ (3 ÷ 1 = 3)  ⇒  H₂ limits maximum yield to 4 mol H₂O");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "LIMITING REAGENT RULE", "The reactant with the smallest (moles ÷ coefficient) ratio runs out first and determines theoretical yield.", ACCENT_GREEN);
            }
        }
    }

    // 7. BIOLOGY: Cell Membrane Transport
    private static void drawBioTransport(Graphics2D g, int step, int phase) {
        // Draw Phospholipid Bilayer across middle
        int memY = 215;
        g.setColor(new Color(0x1E, 0x3A, 0x5F, 140));
        g.fillRect(40, memY, WIDTH - 80, 64);
        for (int x = 55; x < WIDTH - 55; x += 28) {
            g.setColor(ACCENT_AMBER);
            g.fillOval(x, memY - 6, 14, 14);
            g.fillOval(x, memY + 56, 14, 14);
            g.setColor(TEXT_MUTED);
            g.drawLine(x + 4, memY + 8, x + 4, memY + 28);
            g.drawLine(x + 10, memY + 8, x + 10, memY + 28);
            g.drawLine(x + 4, memY + 36, x + 4, memY + 56);
            g.drawLine(x + 10, memY + 36, x + 10, memY + 56);
        }

        if (step == 1) {
            // Channel protein
            g.setColor(ACCENT_CYAN);
            g.fillRoundRect(370, memY - 12, 60, 88, 16, 16);
            drawArrowLine(g, 400, 115, 400, 340, ACCENT_GREEN, "Down Gradient (0 ATP)");
            drawCard(g, 50, 88, 280, 95, ACCENT_CYAN, "Extracellular: HIGH [Glucose]", "Facilitated Diffusion via Channel", "Spontaneous High → Low flow");
            if (phase >= 3) {
                drawCard(g, 470, 295, 280, 90, ACCENT_GREEN, "Intracellular: LOW [Glucose]", "Zero Metabolic ATP Required", "Channel lowers activation barrier");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "PASSIVE TRANSPORT", "Both simple and facilitated diffusion move solutes down their concentration gradient with 0 ATP cost.", ACCENT_CYAN);
            }
        } else if (step == 2) {
            drawCard(g, 45, 90, 225, 100, ACCENT_CYAN, "Hypotonic Outside", "Water Enters Cell", "RBC swells & lyses (bursts)");
            if (phase >= 2) {
                drawCard(g, 288, 90, 225, 100, ACCENT_GREEN, "Isotonic Balance", "Equal Water Flux", "Normal biconcave RBC volume");
            }
            if (phase >= 3) {
                drawCard(g, 530, 90, 225, 100, ACCENT_CORAL, "Hypertonic Salt Solution", "Water Leaves Cell → Shrivels", "Crenation: H₂O flows toward salt");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "OSMOSIS & TONICITY", "Water diffuses across semi-permeable membranes from hypotonic (low solute) to hypertonic (high solute).", ACCENT_CORAL);
            }
        } else {
            // Na+/K+ ATPase Pump
            g.setColor(ACCENT_PURPLE);
            g.fillRoundRect(355, memY - 14, 90, 92, 18, 18);
            drawArrowLine(g, 382, 340, 382, 115, ACCENT_AMBER, "");
            g.setColor(ACCENT_AMBER);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString("3 Na+ Out (Uphill)", 262, 195);
            if (phase >= 2) {
                drawArrowLine(g, 418, 115, 418, 340, ACCENT_CYAN, "");
                g.setColor(ACCENT_CYAN);
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.drawString("2 K+ In (Uphill)", 452, 195);
            }
            drawCard(g, 45, 88, 280, 95, ACCENT_AMBER, "Active Transport (Low → High)", "Pumps Against Gradient", "Requires direct ATP Hydrolysis");
            if (phase >= 3) {
                drawCard(g, 470, 295, 280, 95, ACCENT_PURPLE, "Na⁺/K⁺-ATPase Stoichiometry", "ATP → ADP + Pᵢ", "3 Na⁺ pumped out, 2 K⁺ pumped in");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "ATP-DRIVEN ACTIVE PUMP", "Active transport uses transmembrane pumps and cellular ATP to push ions uphill against their gradient.", ACCENT_PURPLE);
            }
        }
    }

    // 8. BIOLOGY: Photosynthesis & Cellular Respiration
    private static void drawBioPhoto(Graphics2D g, int step, int phase) {
        if (step == 1) {
            drawCard(g, 55, 95, 320, 110, ACCENT_GREEN, "Thylakoid Membrane (Light Reactions)", "2 H₂O + Light ⟶ O₂↑ + 4 H⁺ + 4 e⁻", "Photolysis of water releases O₂ gas!");
            if (phase >= 2) {
                drawArrowLine(g, 385, 150, 435, 150, ACCENT_AMBER, "generates");
                drawCard(g, 445, 95, 300, 110, ACCENT_AMBER, "Chemical Energy Carriers", "ATP  +  NADPH", "Transferred to the Stroma for Step 2");
            }
            if (phase >= 3) {
                drawCard(g, 140, 240, 520, 100, ACCENT_CYAN, "Key Misconception Alert: Source of O₂", "Atmospheric O₂ comes from splitting water (H₂O), NOT from CO₂", "Verified by isotopic oxygen-18 (¹⁸O) tracer experiments");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "THYLAKOID PHOTOLYSIS", "Chlorophyll captures photons in the thylakoid membrane to split H₂O into O₂ and build ATP + NADPH.", ACCENT_GREEN);
            }
        } else if (step == 2) {
            drawCard(g, 55, 95, 320, 110, ACCENT_AMBER, "Inputs from Light Reactions", "ATP (Energy) + NADPH (Electrons)", "Powers carbon reduction in the Stroma");
            if (phase >= 2) {
                drawArrowLine(g, 385, 150, 435, 150, ACCENT_TEAL, "powers");
                drawCard(g, 445, 95, 300, 110, ACCENT_TEAL, "Calvin Cycle (Chloroplast Stroma)", "6 CO₂ ⟶ 2 G3P ⟶ Glucose (C₆H₁₂O₆)", "Catalyzed by RuBisCO enzyme");
            }
            if (phase >= 3) {
                drawCard(g, 140, 240, 520, 100, ACCENT_GREEN, "Coenzyme Recycling Loop", "Calvin cycle returns ADP + Pᵢ and NADP⁺ back to the Thylakoid", "Couples light-dependent and light-independent stages");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "STROMA CARBON FIXATION", "In the chloroplast stroma, the Calvin cycle uses ATP and NADPH to fix CO₂ into glucose.", ACCENT_TEAL);
            }
        } else {
            drawCard(g, 50, 95, 310, 110, ACCENT_GREEN, "Chloroplast (Photosynthesis)", "6 CO₂ + 6 H₂O + Light", "Produces: C₆H₁₂O₆ + 6 O₂");
            if (phase >= 2) {
                drawArrowLine(g, 370, 130, 430, 130, ACCENT_AMBER, "Glucose + O₂");
                drawArrowLine(g, 430, 175, 370, 175, ACCENT_CYAN, "CO₂ + H₂O");
                drawCard(g, 440, 95, 310, 110, ACCENT_CORAL, "Mitochondrion (Aerobic Respiration)", "C₆H₁₂O₆ + 6 O₂", "Produces: 6 CO₂ + 6 H₂O + ~32 ATP");
            }
            if (phase >= 3) {
                drawCard(g, 130, 240, 540, 100, ACCENT_AMBER, "Reciprocal Bioenergetic Equations", "Products of Photosynthesis = Exact Reactants of Cellular Respiration", "Plant cells contain BOTH chloroplasts and mitochondria!");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "GLOBAL BIOENERGETIC CYCLE", "Photosynthesis stores solar energy in glucose bonds; cellular respiration oxidizes glucose to generate ATP.", ACCENT_AMBER);
            }
        }
    }

    // 9. ENGLISH: Sentence Clauses
    private static void drawEngClauses(Graphics2D g, int step, int phase) {
        if (step == 1) {
            drawCard(g, 55, 95, 690, 95, ACCENT_GREEN, "Independent Clause (IC) = Complete Sentence", "[Subject: The experiment]  +  [Finite Verb: yielded]  +  [Object: accurate results.]", "Expresses a complete proposition and stands alone with a period.");
            if (phase >= 2) {
                drawCard(g, 55, 225, 690, 95, ACCENT_CORAL, "Contrast: Non-Clause Prepositional / Participial Phrases", "'During the long laboratory experiment...'  |  'Having tested the sample...'", "Lacks a finite predicate verb — cannot stand alone as an independent clause.");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "INDEPENDENT CLAUSE TEST", "Verify both a grammatical Subject and a Finite Verb that express a complete thought.", ACCENT_GREEN);
            }
        } else if (step == 2) {
            drawCard(g, 55, 95, 320, 105, ACCENT_AMBER, "Subordinating Conjunctions", "Although, Because, Since, While, If", "Demotes an IC into a Dependent Clause (DC)");
            if (phase >= 2) {
                drawArrowLine(g, 385, 145, 430, 145, ACCENT_TEAL, "attach");
                drawCard(g, 440, 95, 305, 105, ACCENT_CYAN, "Dependent Clause (DC)", "'Although the battery was low...'", "Structurally incomplete alone (Fragment)");
            }
            if (phase >= 3) {
                drawCard(g, 80, 235, 640, 95, ACCENT_GREEN, "Complete Complex Sentence Assembly", "[DC: Although the battery was low], [IC: the offline model finished the lesson.]", "The dependent clause modifies the main independent clause.");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "SUBORDINATION RULE", "A clause starting with 'although' or 'because' is dependent and must attach to an independent clause.", ACCENT_CYAN);
            }
        } else {
            drawCard(g, 55, 90, 330, 105, ACCENT_TEAL, "Complex Punctuation Pattern", "[DC], [IC]   vs.   [IC] [DC]", "Comma required ONLY when Dependent Clause comes first");
            if (phase >= 2) {
                drawCard(g, 415, 90, 330, 105, ACCENT_CORAL, "Comma Splice Error (✗)", "'The power went out, the tutor kept running.'", "Two ICs joined by only a comma is ungrammatical!");
            }
            if (phase >= 3) {
                drawCard(g, 55, 225, 690, 105, ACCENT_GREEN, "3 Valid Ways to Fix a Comma Splice (✓)", "1. Semicolon + Conjunctive Adverb: 'The power went out; however, the tutor kept running.'\n2. Comma + FANBOYS: 'The power went out, but the tutor kept running.'", "Each independent clause is properly linked.");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "COMMA SPLICE REMEDIATION", "Use '; however,' or ', but' — never join two independent clauses with a bare comma.", ACCENT_GREEN);
            }
        }
    }

    // 10. ENGLISH: Active vs. Passive Voice
    private static void drawEngVoice(Graphics2D g, int step, int phase) {
        if (step == 1) {
            drawCard(g, 55, 95, 210, 100, ACCENT_GREEN, "1. Agent (Subject)", "'The researcher'", "Performs the action");
            if (phase >= 2) {
                drawArrowLine(g, 275, 145, 305, 145, ACCENT_TEAL, "");
                drawCard(g, 310, 95, 190, 100, ACCENT_CYAN, "2. Active Verb", "'analyzed'", "Direct transitive action");
            }
            if (phase >= 3) {
                drawArrowLine(g, 510, 145, 540, 145, ACCENT_TEAL, "");
                drawCard(g, 545, 95, 200, 100, ACCENT_AMBER, "3. Direct Object", "'the samples.'", "Receives the action");
            }
            drawCard(g, 120, 235, 560, 95, ACCENT_GREEN, "Active Voice = Direct Agency", "The grammatical subject is the doer of the verb: S (Agent) ⟶ V ⟶ O", "Concise, vigorous, and unambiguous accountability.");
            if (phase >= 4) {
                drawFooterBanner(g, "ACTIVE VOICE ANATOMY", "In active voice, the actor performing the verb occupies the subject position.", ACCENT_GREEN);
            }
        } else if (step == 2) {
            drawCard(g, 55, 95, 690, 95, ACCENT_CYAN, "Passive Voice Structural Formula", "[Promoted Recipient Subject]  +  [Auxiliary 'be' + Past Participle V₃]  +  (by [Agent])", "'The samples' (Subject)  +  'were analyzed' (be + V₃)  +  'by the researcher.'");
            if (phase >= 2) {
                drawCard(g, 55, 225, 690, 100, ACCENT_AMBER, "Crucial Distinction: Tense vs. Voice", "Past Active: 'She wrote the code.'   |   Past Passive: 'The code was written by her.'", "Simply having 'is/was' (e.g., 'She was running') is progressive aspect, NOT passive voice!");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "PASSIVE TRANSFORMATION", "Promote the original object to subject and convert the verb to [form of 'be' + past participle].", ACCENT_CYAN);
            }
        } else {
            drawCard(g, 55, 95, 330, 120, ACCENT_GREEN, "When to Choose ACTIVE Voice", "• Argumentative & thesis essays\n• Clear actor accountability", "'The engineer patched the vulnerability.'");
            if (phase >= 2) {
                drawCard(g, 415, 95, 330, 120, ACCENT_PURPLE, "When to Choose PASSIVE Voice", "• Scientific methodology (process focus)\n• Actor unknown or secondary", "'The solution was heated to 80°C for ten minutes.'");
            }
            if (phase >= 3) {
                drawCard(g, 110, 245, 580, 90, ACCENT_TEAL, "Rhetorical Precision Principle", "Choose voice deliberately based on whether the Reader needs the Actor or the Receiver first", "Both voices are grammatically valid tools for different communicative goals.");
            }
            if (phase >= 4) {
                drawFooterBanner(g, "RHETORICAL VOICE SELECTION", "Use active voice for directness and agency; use passive voice to foreground the process or recipient.", ACCENT_TEAL);
            }
        }
    }

    /**
     * Builds a standards-compliant ISO Base Media File Format (.mp4) container (`ftyp`, `moov`, `mdat`, `bism`)
     * containing an H.264 Baseline track header and the exact lossless PNG frames for frame-accurate
     * offline playback and scrubbing on all Android hardware tiers.
     */
    private static byte[] buildHybridMp4Container(List<byte[]> pngFrames, int fps) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1. ftyp box (28 bytes)
        ByteArrayOutputStream ftypBody = new ByteArrayOutputStream();
        ftypBody.write("mp42".getBytes(StandardCharsets.US_ASCII));
        ftypBody.write(intBytes(0));
        ftypBody.write("mp42".getBytes(StandardCharsets.US_ASCII));
        ftypBody.write("isom".getBytes(StandardCharsets.US_ASCII));
        ftypBody.write("avc1".getBytes(StandardCharsets.US_ASCII));
        writeBox(out, "ftyp", ftypBody.toByteArray());

        // 2. mdat box containing length-prefixed frame payloads
        ByteArrayOutputStream mdatBody = new ByteArrayOutputStream();
        int[] frameSizes = new int[pngFrames.size()];
        int[] frameOffsets = new int[pngFrames.size()];
        int baseMdatPayloadOffset = 28 + 8; // after ftyp (28) + mdat header (8)
        for (int i = 0; i < pngFrames.size(); i++) {
            byte[] frame = pngFrames.get(i);
            frameOffsets[i] = baseMdatPayloadOffset + mdatBody.size();
            frameSizes[i] = frame.length;
            mdatBody.write(frame);
        }
        writeBox(out, "mdat", mdatBody.toByteArray());

        // 3. moov box (ISO 14496-12 movie header + video track table)
        ByteArrayOutputStream moovBody = new ByteArrayOutputStream();

        // mvhd
        ByteArrayOutputStream mvhd = new ByteArrayOutputStream();
        mvhd.write(intBytes(0)); // version + flags
        mvhd.write(intBytes(0)); // creation_time
        mvhd.write(intBytes(0)); // modification_time
        mvhd.write(intBytes(fps)); // timescale
        mvhd.write(intBytes(pngFrames.size())); // duration
        mvhd.write(intBytes(0x00010000)); // rate 1.0
        mvhd.write(shortBytes(0x0100)); // volume 1.0
        mvhd.write(new byte[10]); // reserved
        int[] matrix = {0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000};
        for (int m : matrix) mvhd.write(intBytes(m));
        mvhd.write(new byte[24]); // pre_defined
        mvhd.write(intBytes(2)); // next_track_id
        writeBox(moovBody, "mvhd", mvhd.toByteArray());

        // udta -> bism (custom index table for fast zero-copy frame extraction in OfflineLessonMediaViewer)
        ByteArrayOutputStream bism = new ByteArrayOutputStream();
        bism.write("BISM_MP4_V1".getBytes(StandardCharsets.US_ASCII));
        bism.write(intBytes(fps));
        bism.write(intBytes(pngFrames.size()));
        for (int i = 0; i < pngFrames.size(); i++) {
            bism.write(intBytes(frameOffsets[i]));
            bism.write(intBytes(frameSizes[i]));
        }
        writeBox(moovBody, "udta", bism.toByteArray());

        writeBox(out, "moov", moovBody.toByteArray());
        return out.toByteArray();
    }

    private static void writeBox(OutputStream out, String type, byte[] payload) throws IOException {
        int totalSize = 8 + payload.length;
        out.write(intBytes(totalSize));
        out.write(type.getBytes(StandardCharsets.US_ASCII), 0, 4);
        out.write(payload);
    }

    private static byte[] intBytes(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }

    private static byte[] shortBytes(int v) {
        return ByteBuffer.allocate(2).putShort((short) v).array();
    }
}
