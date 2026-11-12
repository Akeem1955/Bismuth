package com.bismuth.app.teaching

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive, animated skill-aligned simulation panel above the whiteboard canvas.
 * Adapts to each of the 10 curriculum topic IDs so every module has bespoke animated intuition.
 */
@Composable
fun TopicInteractiveSimulationCard(
    topicId: String,
    stepNumber: Int,
    stepTitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (topicId == "math_linear_equations") {
        AnimatedBalanceScaleWidget(
            stepNumber = stepNumber,
            modifier = modifier
        )
        return
    }

    var isExpanded by remember(topicId) { mutableStateOf(true) }

    val simLabel = when (topicId) {
        "math_circle_theorems" -> "CIRCLE GEOMETRY LAB"
        "phys_newton_second_law" -> "FREE-BODY FORCE SIMULATOR"
        "phys_conservation_energy" -> "PENDULUM ENERGY CONSERVATION"
        "chem_atomic_orbitals" -> "BOHR SHELL & NUCLEUS MODEL"
        "chem_stoichiometry_moles" -> "STOICHIOMETRY REAGENT LAB"
        "bio_cell_transport" -> "MEMBRANE GRADIENT SIMULATOR"
        "bio_photosynthesis_atp" -> "CHLOROPLAST ⇄ MITOCHONDRIA"
        "eng_sentence_clauses" -> "CLAUSE ARCHITECTURE BRIDGE"
        "eng_active_passive_voice" -> "SYNTACTIC AGENCY SHIFTER"
        else -> "INTERACTIVE CONCEPT LAB"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$simLabel • Step $stepNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse Lab" else "Expand Lab",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    when (topicId) {
                        "math_circle_theorems" -> CircleGeometryLab(stepNumber = stepNumber, accentColor = accentColor)
                        "phys_newton_second_law" -> NewtonForceLab(stepNumber = stepNumber, accentColor = accentColor)
                        "phys_conservation_energy" -> EnergyPendulumLab(stepNumber = stepNumber, accentColor = accentColor)
                        "chem_atomic_orbitals" -> AtomicOrbitalLab(stepNumber = stepNumber, accentColor = accentColor)
                        "chem_stoichiometry_moles" -> StoichiometryMoleLab(stepNumber = stepNumber, accentColor = accentColor)
                        "bio_cell_transport" -> CellMembraneLab(stepNumber = stepNumber, accentColor = accentColor)
                        "bio_photosynthesis_atp" -> BioenergeticsCycleLab(stepNumber = stepNumber, accentColor = accentColor)
                        "eng_sentence_clauses" -> SentenceClauseLab(stepNumber = stepNumber, accentColor = accentColor)
                        "eng_active_passive_voice" -> ActivePassiveLab(stepNumber = stepNumber, accentColor = accentColor)
                        else -> GenericSkillPulseBar(stepTitle = stepTitle, accentColor = accentColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleGeometryLab(stepNumber: Int, accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "circleTheorem")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )
    val labelText = when (stepNumber) {
        1 -> "Central Angle 2θ = 116°  ⇔  Inscribed Angle θ = 58° (same subtended arc)"
        2 -> "Cyclic Quadrilateral: Opposite vertices satisfy α + β = 180°"
        else -> "Tangent ⊥ Radius at contact point P ⇒ Exact 90° right angle"
    }
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(modifier = Modifier.size(92.dp)) {
            val r = size.minDimension * 0.40f
            val c = center
            drawCircle(color = outlineColor, radius = r, center = c, style = Stroke(width = 3f))
            drawCircle(color = accentColor, radius = 5f, center = c)

            when (stepNumber) {
                1 -> {
                    val leftPt = Offset(c.x - r * 0.82f, c.y + r * 0.56f)
                    val rightPt = Offset(c.x + r * 0.82f, c.y + r * 0.56f)
                    val topShift = (sweep - 0.5f) * 0.35f
                    val circumPt = Offset(c.x + r * sin(topShift), c.y - r * cos(topShift))
                    drawLine(accentColor, c, leftPt, strokeWidth = 3.5f)
                    drawLine(accentColor, c, rightPt, strokeWidth = 3.5f)
                    drawLine(secondaryColor, circumPt, leftPt, strokeWidth = 3f)
                    drawLine(secondaryColor, circumPt, rightPt, strokeWidth = 3f)
                    drawCircle(secondaryColor, radius = 5f, center = circumPt)
                }
                2 -> {
                    val pts = listOf(
                        Offset(c.x - r * 0.65f, c.y - r * 0.75f),
                        Offset(c.x + r * 0.72f, c.y - r * 0.68f),
                        Offset(c.x + r * 0.85f, c.y + r * 0.52f),
                        Offset(c.x - r * 0.78f, c.y + r * 0.62f)
                    )
                    for (i in pts.indices) {
                        drawLine(accentColor, pts[i], pts[(i + 1) % pts.size], strokeWidth = 3.2f)
                        drawCircle(if (i % 2 == 0) secondaryColor else accentColor, radius = 5f, center = pts[i])
                    }
                }
                else -> {
                    val touchPt = Offset(c.x + r * 0.7f, c.y - r * 0.71f)
                    drawLine(accentColor, c, touchPt, strokeWidth = 4f)
                    val tanDx = 0.71f * r * (0.7f + 0.3f * sweep)
                    val tanDy = 0.7f * r * (0.7f + 0.3f * sweep)
                    drawLine(
                        secondaryColor,
                        Offset(touchPt.x - tanDx, touchPt.y - tanDy),
                        Offset(touchPt.x + tanDx, touchPt.y + tanDy),
                        strokeWidth = 3.5f
                    )
                    drawCircle(secondaryColor, radius = 5f, center = touchPt)
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = when (stepNumber) {
                    1 -> "Subtended Arc Invariant"
                    2 -> "Opposite Cyclic Pair"
                    else -> "Tangent Orthogonality"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NewtonForceLab(stepNumber: Int, accentColor: Color) {
    var massKg by remember(stepNumber) { mutableIntStateOf(4) }
    val pullN = if (stepNumber == 3) 35 else 24
    val frictionN = if (stepNumber == 3) 15 else 0
    val netN = (pullN - frictionN).coerceAtLeast(0)
    val accel = netN.toFloat() / massKg.toFloat()

    val infinite = rememberInfiniteTransition(label = "newtonSlide")
    val slide by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (2600 / accel.coerceAtLeast(1f)).toInt().coerceIn(700, 2600), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "crateX"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            val frictionColor = Color(0xFFFF6E40)
            val textColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackY = size.height * 0.78f
                drawLine(textColor, Offset(16f, trackY), Offset(size.width - 16f, trackY), strokeWidth = 4f)
                val boxW = 54.dp.toPx()
                val boxH = 32.dp.toPx()
                val maxTravel = (size.width - boxW - 110f).coerceAtLeast(30f)
                val boxX = 48f + maxTravel * (slide * slide)
                val boxY = trackY - boxH

                drawRect(color = accentColor, topLeft = Offset(boxX, boxY), size = Size(boxW, boxH))
                // Applied Force Vector (Right)
                val pullLen = (pullN * 1.4f).coerceAtLeast(24f)
                drawLine(
                    color = Color(0xFF00E676),
                    start = Offset(boxX + boxW, boxY + boxH / 2f),
                    end = Offset(boxX + boxW + pullLen, boxY + boxH / 2f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                // Friction Vector (Left) in step 3
                if (frictionN > 0) {
                    drawLine(
                        color = frictionColor,
                        start = Offset(boxX, boxY + boxH / 2f),
                        end = Offset((boxX - frictionN * 1.3f).coerceAtLeast(6f), boxY + boxH / 2f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "F_net = $pullN N - $frictionN N = $netN N  ⇒  a = %.1f m/s²".format(accel),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.16f),
                modifier = Modifier.clickable {
                    massKg = when (massKg) {
                        2 -> 4
                        4 -> 8
                        else -> 2
                    }
                }
            ) {
                Text(
                    text = "m = $massKg kg (tap)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EnergyPendulumLab(stepNumber: Int, accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "pendulum")
    val anglePhase by infinite.animateFloat(
        initialValue = -0.65f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "theta"
    )
    val heightRatio = kotlin.math.abs(anglePhase) / 0.65f
    val peRatio = (heightRatio * heightRatio).coerceIn(0.05f, 0.95f)
    val keRatio = (1f - peRatio).coerceIn(0.05f, 0.95f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val stringColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.size(width = 96.dp, height = 74.dp)) {
            val pivot = Offset(size.width / 2f, 8f)
            val len = size.height * 0.72f
            val bobX = pivot.x + len * sin(anglePhase)
            val bobY = pivot.y + len * cos(anglePhase)
            drawLine(stringColor, pivot, Offset(bobX, bobY), strokeWidth = 3f)
            drawCircle(accentColor, radius = 10.dp.toPx(), center = Offset(bobX, bobY))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EnergyGaugeRow("PE (mgh)", peRatio, Color(0xFF42A5F5))
            EnergyGaugeRow("KE (½mv²)", keRatio, Color(0xFFFFB300))
            Text(
                text = "E_total = PE + KE = Constant ($stepNumber/3)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EnergyGaugeRow(label: String, fraction: Float, barColor: Color) {
    val animFrac by animateFloatAsState(targetValue = fraction, label = label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(68.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFrac)
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun AtomicOrbitalLab(stepNumber: Int, accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "atomOrbit")
    val angleDeg by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "deg"
    )
    val electronColor = Color(0xFF40C4FF)
    val orbitColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(modifier = Modifier.size(86.dp)) {
            val r1 = size.minDimension * 0.24f
            val r2 = size.minDimension * 0.42f
            drawCircle(orbitColor, r1, center, style = Stroke(2f))
            drawCircle(orbitColor, r2, center, style = Stroke(2f))
            drawCircle(accentColor, 10f, center)

            // 2 K-shell electrons
            for (i in 0 until 2) {
                val rad = ((angleDeg + i * 180f) * PI / 180.0)
                drawCircle(
                    electronColor,
                    radius = 4.5f,
                    center = Offset(center.x + (r1 * cos(rad)).toFloat(), center.y + (r1 * sin(rad)).toFloat())
                )
            }
            // 4 L-shell valence electrons (Carbon-12)
            for (i in 0 until 4) {
                val rad = ((-angleDeg * 0.7f + i * 90f) * PI / 180.0)
                drawCircle(
                    Color(0xFF00E676),
                    radius = 5f,
                    center = Offset(center.x + (r2 * cos(rad)).toFloat(), center.y + (r2 * sin(rad)).toFloat())
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = when (stepNumber) {
                    1 -> "Atomic Number Z = 6 Protons (Carbon)"
                    2 -> "Isotope C-12 vs C-14 (Same Z=6, different N)"
                    else -> "Shells: K=2, L=4 Valence Electrons"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Mass Number A = Z + N  |  Valence e⁻ govern chemical bonding",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StoichiometryMoleLab(stepNumber: Int, accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "stoichPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionPill("2 H₂ (Limit)", accentColor.copy(alpha = 0.22f * pulse), accentColor)
            Text("+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReactionPill("1 O₂ (Excess)", MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f), MaterialTheme.colorScheme.onTertiaryContainer)
            Text("➔", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            ReactionPill("2 H₂O", Color(0xFF00C853).copy(alpha = 0.2f), Color(0xFF00E676))
        }
        Text(
            text = when (stepNumber) {
                1 -> "Step 1: Convert mass m to moles first: n = m / Molar Mass"
                2 -> "Step 2: Apply balanced coefficient mole ratios (2 : 1 : 2)"
                else -> "Step 3: Smallest quotient runs out FIRST = Limiting Reagent"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReactionPill(label: String, bg: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = bg) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CellMembraneLab(stepNumber: Int, accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "membraneFlow")
    val flow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "particle"
    )
    val activeTransport = stepNumber >= 3
    val ionColor = if (activeTransport) Color(0xFFFFCA28) else Color(0xFF4FC3F7)
    val bilayerColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 112.dp, height = 66.dp)) {
            val midX = size.width / 2f
            // Bilayer center vertical band
            drawRect(
                color = accentColor.copy(alpha = 0.25f),
                topLeft = Offset(midX - 8f, 6f),
                size = Size(16f, size.height - 12f)
            )
            drawLine(bilayerColor, Offset(midX - 8f, 6f), Offset(midX - 8f, size.height - 6f), strokeWidth = 2.5f)
            drawLine(bilayerColor, Offset(midX + 8f, 6f), Offset(midX + 8f, size.height - 6f), strokeWidth = 2.5f)

            val directionStart = if (activeTransport) size.width * 0.85f else size.width * 0.15f
            val directionEnd = if (activeTransport) size.width * 0.15f else size.width * 0.85f
            for (k in 0..2) {
                val p = (flow + k * 0.33f) % 1f
                val px = directionStart + (directionEnd - directionStart) * p
                val py = 16f + k * 17f
                drawCircle(ionColor, radius = 5.5f, center = Offset(px, py))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (activeTransport) "Active Transport (Requires ATP)" else "Passive Diffusion / Osmosis",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (activeTransport) {
                    "Pumps molecules LOW ➔ HIGH against gradient using ATP"
                } else {
                    "Net movement flows HIGH ➔ LOW down gradient (0 ATP)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BioenergeticsCycleLab(stepNumber: Int, accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "atpCycle")
    val highlight by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cycleAlpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionPill("Chloroplast\n(Sunlight ➔ Glucose)", Color(0xFF2E7D32).copy(alpha = 0.3f * highlight), Color(0xFF81C784))
            Text("⇄", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accentColor)
            ReactionPill("Mitochondrion\n(Glucose ➔ ATP)", Color(0xFFFF8F00).copy(alpha = 0.25f * highlight), Color(0xFFFFD54F))
        }
        Text(
            text = when (stepNumber) {
                1 -> "Light Reactions split H₂O ➔ O₂ + ATP + NADPH; Calvin Cycle fixes CO₂"
                2 -> "Cellular Respiration oxidizes C₆H₁₂O₆ + 6 O₂ ➔ 6 CO₂ + 6 H₂O + ATP"
                else -> "Coupled Matter Loop: Outputs of photosynthesis power respiration"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SentenceClauseLab(stepNumber: Int, accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "clausePulse")
    val glow by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "clauseGlow"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionPill(
                if (stepNumber == 2) "Although I studied" else "I finished early",
                if (stepNumber == 2) Color(0xFFFFA726).copy(alpha = 0.22f) else accentColor.copy(alpha = 0.22f * glow),
                if (stepNumber == 2) Color(0xFFFFB74D) else accentColor
            )
            ReactionPill(
                if (stepNumber == 3) ", and" else ",",
                Color(0xFF00C853).copy(alpha = 0.2f),
                Color(0xFF69F0AE)
            )
            ReactionPill(
                "I mastered it.",
                accentColor.copy(alpha = 0.22f * glow),
                accentColor
            )
        }
        Text(
            text = when (stepNumber) {
                1 -> "Independent Clause = Subject + Verb + Complete Thought (stands alone)"
                2 -> "Subordinator ('Although') turns a clause into a Dependent fragment"
                else -> "Fix comma splices with ', + FANBOYS' or a semicolon ';'"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivePassiveLab(stepNumber: Int, accentColor: Color) {
    val isPassiveView = stepNumber >= 2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isPassiveView) {
                ReactionPill("Agent: Researcher", Color(0xFF00C853).copy(alpha = 0.2f), Color(0xFF69F0AE))
                ReactionPill("analyzed", accentColor.copy(alpha = 0.2f), accentColor)
                ReactionPill("the data.", MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurface)
            } else {
                ReactionPill("Target: The data", accentColor.copy(alpha = 0.22f), accentColor)
                ReactionPill("was analyzed", Color(0xFFFFB300).copy(alpha = 0.22f), Color(0xFFFFD54F))
                ReactionPill("by the researcher.", MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = when (stepNumber) {
                1 -> "Active Voice: Actor sits cleanly in the grammatical Subject slot"
                2 -> "Passive Voice: Object moves to Subject + [auxiliary 'be' + past participle]"
                else -> "Use Passive intentionally when process/result matters more than actor"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GenericSkillPulseBar(stepTitle: String, accentColor: Color) {
    Text(
        text = "Guided Focus: $stepTitle — Tap any concept card below to hear detailed intuition.",
        style = MaterialTheme.typography.bodySmall,
        color = accentColor
    )
}
