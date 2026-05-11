# Gesture Math and Native Android Kotlin Guide

This document records the exact gesture math used by the legacy React Native GlyphOS implementation and describes how to rebuild the same feature set from the ground up in native Android Kotlin.

The legacy React Native implementation is based on these files:

- `legacy/react-native/src/utils/GestureNormalizer.ts`
- `legacy/react-native/src/utils/GestureMatcher.ts`
- `legacy/react-native/src/utils/GestureStorage.ts`
- `legacy/react-native/src/components/GestureCanvas.tsx`
- `legacy/react-native/src/components/GestureManagementScreen.tsx`
- `legacy/react-native/src/components/MergeGestureDialog.tsx`
- `legacy/react-native/src/components/AssignAppModal.tsx`
- `legacy/react-native/App.tsx`

Note: the active matcher is the segment-angle / angular-difference matcher. It is not the older turning-signature plus Euclidean/cosine matcher mentioned in the README architecture table.

## Data Model

Every point is a 2D coordinate:

```ts
interface Point {
  x: number;
  y: number;
}
```

Every saved gesture stores a normalized path:

```ts
interface SavedGesture {
  label: string;
  packageName?: string;
  normalizedPath: Point[];
}
```

`normalizedPath` is the canonical gesture representation. It is normally 40 points long.

## Constants

Gesture constants:

```txt
NUM_POINTS = 40
ANGULAR_THRESHOLD = 0.5 radians
GESTURE_ADAPTATION_RATE = 0.05
```

`0.5` radians is about `28.6479` degrees. The threshold check is strict: a score must be `< 0.5`, not `<= 0.5`.

Touch and UI timing constants:

```txt
Long press duration = 800 ms
Long press movement cancel threshold = 10 px
Canvas clear delay after release = 400 ms
Feedback display duration = 2000 ms
Feedback fade-in duration = 200 ms
```

Trail rendering constants:

```txt
TRAIL_SEGMENTS = 5
TRAIL_MIN_OPACITY = 0.15
TRAIL_OPACITY_RANGE = 0.85
TRAIL_MAX_WIDTH = 7.5
TRAIL_WIDTH_RANGE = 5
```

Preview constants:

```txt
Gesture management preview size = 52 px
Merge dialog preview size = 56 px
Preview padding = 6 px
```

Storage keys:

```txt
glyph_os_gestures
glyph_os_onboarding_done
glyph_os_trail_effect
glyph_os_launch_on_create_shortcut
glyph_os_allow_backward_gestures
```

Stored gesture schema version:

```txt
STORAGE_SCHEMA_VERSION = 2
```

## Raw Gesture Capture

The canvas records raw touch coordinates in view-local space:

```txt
rawPoints += Point(locationX, locationY)
```

On touch start:

```txt
rawPoints = [Point(locationX, locationY)]
```

On touch move:

```txt
rawPoints.push(Point(locationX, locationY))
```

A long press opens the gesture management screen if the user holds for `800 ms` without moving more than `10 px` from the touch start. Movement is computed as:

```txt
totalMovement = sqrt(dx * dx + dy * dy)
```

In the React Native code this uses `Math.hypot(gestureState.dx, gestureState.dy)`.

On touch release, the raw path is normalized. If normalization fails, the stroke is ignored.

## Path Length Math

Distance between two points:

```txt
dx = b.x - a.x
dy = b.y - a.y
distance(a, b) = sqrt(dx * dx + dy * dy)
```

Total polyline arc length:

```txt
totalPathLength(points) = sum(distance(points[i - 1], points[i])) for i = 1..points.size - 1
```

A one-point path has total length `0` because the sum has no segments.

## 40-Point Normalization

The normalizer resamples a raw path to exactly `NUM_POINTS = 40` arc-length-spaced points.

Invalid inputs:

```txt
if rawPoints.size < 2 -> null
if totalPathLength(rawPoints) == 0 -> null
```

Spacing interval:

```txt
interval = totalPathLength(rawPoints) / (NUM_POINTS - 1)
```

Since `NUM_POINTS = 40`, the interval is:

```txt
interval = total / 39
```

Resampling state:

```txt
resampled = [copy(rawPoints[0])]
accumulated = 0
prev = rawPoints[0]
i = 1
```

Loop condition:

```txt
while i < rawPoints.size and resampled.size < NUM_POINTS - 1
```

For each current raw point:

```txt
curr = rawPoints[i]
segLen = distance(prev, curr)
remaining = interval - accumulated
```

If the next normalized point lies on the current raw segment:

```txt
if segLen >= remaining:
  t = remaining / segLen
  newPoint.x = prev.x + t * (curr.x - prev.x)
  newPoint.y = prev.y + t * (curr.y - prev.y)
  resampled.push(newPoint)
  prev = newPoint
  accumulated = 0
  i is not advanced
```

Leaving `i` unchanged lets the algorithm place multiple normalized points on one long raw segment.

If the current raw segment is too short to reach the next interval boundary:

```txt
else:
  accumulated += segLen
  prev = curr
  i += 1
```

After the loop, the algorithm always appends the final raw point exactly:

```txt
resampled.push(copy(rawPoints.last()))
```

For normal, non-degenerate input this returns exactly 40 points: the first raw point, 38 interpolated points, and the last raw point.

Important properties:

- The path is not translated to an origin.
- The path is not scaled into a unit box.
- The path is not rotated.
- Matching later compares segment directions, so absolute screen position does not affect the match score.
- Uniform scale normally does not affect the match score because segment angles stay the same.
- Rotation does affect the match score because every segment angle changes.

## Segment-Angle Difference

Two normalized paths are compared by corresponding segment direction.

The number of points compared is:

```txt
n = min(line1.size, line2.size)
```

Invalid comparison:

```txt
if n < 2 -> Infinity
```

For every segment index `i = 0..n - 2`:

```txt
v1x = line1[i + 1].x - line1[i].x
v1y = line1[i + 1].y - line1[i].y
v2x = line2[i + 1].x - line2[i].x
v2y = line2[i + 1].y - line2[i].y

angle1 = atan2(v1y, v1x)
angle2 = atan2(v2y, v2x)

diff = abs(angle1 - angle2)
if diff > pi:
  diff = 2 * pi - diff

totalDiff += diff
```

Final score:

```txt
calculateDifference(line1, line2) = totalDiff / (n - 1)
```

The result is an average angular difference in radians. Lower is better.

Examples from the tests:

```txt
identical horizontal path vs itself -> 0
horizontal path vs vertical path -> about pi / 2
horizontal path vs slight y perturbation -> less than 0.5
path with fewer than 2 points -> Infinity
```

## Backward Gesture Matching

The project can optionally treat the same shape drawn in reverse as a match.

Forward-only score:

```txt
forward = calculateDifference(candidate, reference)
```

Backward-enabled score:

```txt
reversed = calculateDifference(candidate, reverse(reference))
best = min(forward, reversed)
```

Function-level default:

```txt
allowBackward = false
```

App-level current default:

```txt
allowBackwardGestures = true
```

The app stores the setting under `glyph_os_allow_backward_gestures` and passes it into the matcher.

## Gesture Match Selection

Given a normalized candidate path and a list of saved gestures:

```txt
if savedGestures is empty -> null
```

For each saved gesture:

```txt
skip if normalizedPath is missing
skip if normalizedPath.size < 2
score = calculateBestDirectionDifference(candidate, gesture.normalizedPath, allowBackward)
keep the gesture with the smallest score
```

Accept the best match only when:

```txt
bestScore < ANGULAR_THRESHOLD
bestScore < 0.5
```

Otherwise return `null`.

## Similar-App Ranking

When a new gesture needs an app assignment, the app pins up to five likely apps first. The ranking is based on the same angular score.

For each saved gesture:

```txt
skip if packageName is missing
skip if normalizedPath is missing or shorter than 2
score = calculateBestDirectionDifference(candidate, gesture.normalizedPath, allowBackward)
store only the best score per packageName
```

Then sort:

```txt
primary sort = angularDifference ascending
tie break = packageName.localeCompare(otherPackageName)
```

Return:

```txt
first max(0, limit) results
```

The app uses `limit = 5`.

## Path Blending For Gesture Merges

When assigning a new gesture to an app that already has a gesture, the app may offer to merge the old and new paths.

Path blending uses linear interpolation per corresponding point:

```txt
n = min(lineA.size, lineB.size)
if n < 2 -> []

clampedT = max(0, min(1, t))

blended[i].x = lineA[i].x * (1 - clampedT) + lineB[i].x * clampedT
blended[i].y = lineA[i].y * (1 - clampedT) + lineB[i].y * clampedT
```

Meaning:

```txt
t = 0 -> old path
t = 1 -> new path
t = 0.5 -> midpoint between old and new path coordinates
```

## Adaptive Gesture Updates

Every successful gesture use nudges the matched saved gesture toward the normalized path the user just drew.

The adaptation amount is:

```txt
GESTURE_ADAPTATION_RATE = 0.05
```

The update uses the same point-by-point linear interpolation as path blending:

```txt
adaptedPath = blendNormalizedPaths(savedPath, usedPath, 0.05)
```

If backward gestures are enabled and the used path matches the saved path better in reverse, the used path is reversed before blending so the saved gesture keeps a stable point order:

```txt
forwardDifference = calculateDifference(usedPath, savedPath)
backwardDifference = calculateDifference(usedPath, reverse(savedPath))

if backwardDifference < forwardDifference:
    alignedUsedPath = reverse(usedPath)
else:
    alignedUsedPath = usedPath

adaptedPath = blendNormalizedPaths(savedPath, alignedUsedPath, 0.05)
```

The adapted path replaces the saved gesture and is persisted only after the gesture actually opens an app or executes a special function.

## Threshold Helper

A candidate is within threshold when:

```txt
calculateDifference(candidate, reference) < threshold
```

Default threshold:

```txt
threshold = ANGULAR_THRESHOLD = 0.5
```

This helper does not use backward matching.

## Merge Bound Search

The merge dialog needs the widest contiguous interval around `t = 0.5` where the blended path still matches both source paths.

Definitions:

```txt
midpoint = blendNormalizedPaths(oldPath, newPath, 0.5)

matchesBoth(candidate) =
  isWithinThreshold(candidate, oldPath) and
  isWithinThreshold(candidate, newPath)
```

Immediate failure:

```txt
if midpoint.size < 2 -> { canMerge: false, minT: 0.5, maxT: 0.5 }
if matchesBoth(midpoint) == false -> { canMerge: false, minT: 0.5, maxT: 0.5 }
```

Constants:

```txt
step = 0.05
iterations = 18
```

Step search to the left:

```txt
left = 0.5
while left - step >= 0 and matchesAt(left - step):
  left -= step
```

Step search to the right:

```txt
right = 0.5
while right + step <= 1 and matchesAt(right + step):
  right += step
```

Left binary refinement:

```txt
minT = left

if left > 0 and matchesAt(0) == false:
  lo = max(0, left - step)
  hi = left
  repeat 18 times:
    mid = (lo + hi) / 2
    if matchesAt(mid):
      hi = mid
    else:
      lo = mid
  minT = hi
else if matchesAt(0):
  minT = 0
```

Right binary refinement:

```txt
maxT = right

if right < 1 and matchesAt(1) == false:
  lo = right
  hi = min(1, right + step)
  repeat 18 times:
    mid = (lo + hi) / 2
    if matchesAt(mid):
      lo = mid
    else:
      hi = mid
  maxT = lo
else if matchesAt(1):
  maxT = 1
```

Success return:

```txt
{ canMerge: true, minT, maxT }
```

The merge UI initializes its slider at:

```txt
initialT = (minT + maxT) / 2
```

## Merge Slider Math

Slider values are clamped to the allowed merge interval:

```txt
value = max(minT, min(maxT, value))
```

Touch position to value:

```txt
normalized = max(0, min(1, localX / sliderWidth))
next = minT + normalized * (maxT - minT)
value = max(minT, min(maxT, next))
```

Value to visual progress:

```txt
if maxT > minT:
  progress = (value - minT) / (maxT - minT)
else:
  progress = 0
```

## Gesture Preview Scaling

Gesture previews fit a gesture path into a square box while preserving aspect ratio.

Bounds:

```txt
minX = min(points.x)
minY = min(points.y)
maxX = max(points.x)
maxY = max(points.y)
```

Size with zero-size fallback:

```txt
width = maxX - minX
height = maxY - minY

if width == 0 -> width = 1
if height == 0 -> height = 1
```

Available drawing area:

```txt
available = PREVIEW_SIZE - PREVIEW_PADDING * 2
```

Scale and center offsets:

```txt
scale = available / max(width, height)
offsetX = PREVIEW_PADDING + (available - width * scale) / 2
offsetY = PREVIEW_PADDING + (available - height * scale) / 2
```

Point transform:

```txt
sx = (p.x - minX) * scale + offsetX
sy = (p.y - minY) * scale + offsetY
```

The first preview command is `M sx sy`; later commands are `L sx sy`.

## Trail Rendering Math

The optional trail effect splits the currently drawn raw path into five overlapping visual segments.

For `n = points.size` and `seg = 0..4`:

```txt
startIdx = floor(seg * (n - 1) / TRAIL_SEGMENTS)
endIdx = min(n - 1, floor((seg + 1) * (n - 1) / TRAIL_SEGMENTS))
```

Skip the segment if:

```txt
endIdx <= startIdx
slice.size < 2
```

Visual interpolation parameter:

```txt
t = seg / (TRAIL_SEGMENTS - 1)
```

Opacity:

```txt
opacity = TRAIL_MIN_OPACITY + t * TRAIL_OPACITY_RANGE
opacity = 0.15 + t * 0.85
```

Stroke width:

```txt
strokeWidth = TRAIL_MAX_WIDTH - t * TRAIL_WIDTH_RANGE
strokeWidth = 7.5 - t * 5
```

Segment `0` is oldest: opacity `0.15`, width `7.5`.

Segment `4` is newest: opacity `1.0`, width `2.5`.

## Persistence Shape

All gestures are stored under `glyph_os_gestures` as a versioned JSON envelope:

```json
{
  "version": 2,
  "gestures": [
    {
      "label": "gesture_1700000000000",
      "packageName": "com.example.app",
      "normalizedPath": [
        { "x": 0, "y": 0 },
        { "x": 10, "y": 0 }
      ]
    }
  ]
}
```

Loading accepts either:

- The versioned envelope above.
- An old plain array of gestures.

Invalid entries are filtered out. A valid gesture must have:

```txt
label is a string
normalizedPath is an array
normalizedPath.size >= 2
every normalizedPath item has numeric x and y
```

Malformed JSON, missing data, and unrecognized shapes load as an empty gesture list.

## Native Android Kotlin Rebuild

The simplest native rebuild is a single-activity Android launcher app with:

- A full-screen custom `View` for drawing gestures.
- A gesture math module matching the formulas above.
- A `SharedPreferences` or DataStore-backed gesture repository.
- A package manager app picker.
- A management screen opened by long press.
- A merge dialog when one app already has a gesture.

### 1. Create The Project

Create a native Android project in Android Studio:

```txt
Template: Empty Views Activity or Empty Activity
Language: Kotlin
Minimum SDK: choose the lowest Android version you want to support
```

Use the Android View system if you want the closest mental model to the current React Native code. Jetpack Compose also works, but a custom `View` maps directly to the existing canvas behavior.

Recommended dependencies:

```kotlin
dependencies {
    implementation("androidx.activity:activity-ktx:<latest>")
    implementation("androidx.appcompat:appcompat:<latest>")
    implementation("androidx.recyclerview:recyclerview:<latest>")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<latest>")

    testImplementation("junit:junit:4.13.2")
}
```

### 2. Configure Launcher Manifest

Add package visibility and launcher-home intent filters:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

    <application
        android:theme="@style/AppTheme"
        android:label="GlyphOS"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="portrait">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`QUERY_ALL_PACKAGES` is what lets the app enumerate installed apps on Android 11 and newer. If you publish to Google Play, be aware that this permission has policy restrictions; for private launcher builds it is the closest equivalent to the current project setup.

### 3. Match The Transparent Wallpaper Theme

The React Native app shows the user's wallpaper behind the launcher. Use the same Android theme attributes:

```xml
<resources>
    <style name="AppTheme" parent="Theme.AppCompat.DayNight.NoActionBar">
        <item name="android:windowShowWallpaper">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
    </style>
</resources>
```

In `MainActivity`, make the window transparent and keep the launcher from closing on Back:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.activity_main)
    }

    override fun onBackPressed() {
        // Match GlyphOS launcher behavior: do not exit the home screen.
    }
}
```

### 4. Port The Gesture Math

Create `GestureMath.kt`:

```kotlin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Point(val x: Double, val y: Double)

data class SavedGesture(
    val label: String,
    val packageName: String? = null,
    val normalizedPath: List<Point>
)

data class MatchResult(
    val gesture: SavedGesture,
    val angularDifference: Double
)

data class SimilarAppMatch(
    val packageName: String,
    val angularDifference: Double
)

data class BlendBoundsResult(
    val canMerge: Boolean,
    val minT: Double,
    val maxT: Double
)

const val NUM_POINTS = 40
const val ANGULAR_THRESHOLD = 0.5

private fun distance(a: Point, b: Point): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}

fun totalPathLength(points: List<Point>): Double {
    var length = 0.0
    for (i in 1 until points.size) {
        length += distance(points[i - 1], points[i])
    }
    return length
}

fun normalizeTo40Points(rawPoints: List<Point>): List<Point>? {
    if (rawPoints.size < 2) return null

    val total = totalPathLength(rawPoints)
    if (total == 0.0) return null

    val interval = total / (NUM_POINTS - 1)
    val resampled = mutableListOf(rawPoints.first().copy())

    var accumulated = 0.0
    var prev = rawPoints.first()
    var i = 1

    while (i < rawPoints.size && resampled.size < NUM_POINTS - 1) {
        val curr = rawPoints[i]
        val segLen = distance(prev, curr)
        val remaining = interval - accumulated

        if (segLen >= remaining) {
            val t = remaining / segLen
            val newPoint = Point(
                x = prev.x + t * (curr.x - prev.x),
                y = prev.y + t * (curr.y - prev.y)
            )
            resampled += newPoint
            prev = newPoint
            accumulated = 0.0
        } else {
            accumulated += segLen
            prev = curr
            i += 1
        }
    }

    resampled += rawPoints.last().copy()
    return resampled
}

fun calculateDifference(line1: List<Point>, line2: List<Point>): Double {
    val n = min(line1.size, line2.size)
    if (n < 2) return Double.POSITIVE_INFINITY

    var totalDiff = 0.0
    for (i in 0 until n - 1) {
        val v1x = line1[i + 1].x - line1[i].x
        val v1y = line1[i + 1].y - line1[i].y
        val v2x = line2[i + 1].x - line2[i].x
        val v2y = line2[i + 1].y - line2[i].y

        val angle1 = atan2(v1y, v1x)
        val angle2 = atan2(v2y, v2x)

        var diff = abs(angle1 - angle2)
        if (diff > PI) {
            diff = 2 * PI - diff
        }
        totalDiff += diff
    }

    return totalDiff / (n - 1)
}

fun calculateBestDirectionDifference(
    candidate: List<Point>,
    reference: List<Point>,
    allowBackward: Boolean
): Double {
    val forward = calculateDifference(candidate, reference)
    if (!allowBackward) return forward
    val reversed = calculateDifference(candidate, reference.asReversed())
    return min(forward, reversed)
}

fun matchGesture(
    normalizedPoints: List<Point>,
    savedGestures: List<SavedGesture>,
    allowBackward: Boolean = false
): MatchResult? {
    if (savedGestures.isEmpty()) return null

    var best: MatchResult? = null
    var minDiff = Double.POSITIVE_INFINITY

    for (gesture in savedGestures) {
        if (gesture.normalizedPath.size < 2) continue
        val diff = calculateBestDirectionDifference(
            normalizedPoints,
            gesture.normalizedPath,
            allowBackward
        )
        if (diff < minDiff) {
            minDiff = diff
            best = MatchResult(gesture, diff)
        }
    }

    return if (best != null && minDiff < ANGULAR_THRESHOLD) best else null
}

fun rankSimilarApps(
    normalizedPoints: List<Point>,
    savedGestures: List<SavedGesture>,
    limit: Int = 5,
    allowBackward: Boolean = false
): List<SimilarAppMatch> {
    val byPackage = mutableMapOf<String, Double>()

    for (gesture in savedGestures) {
        val packageName = gesture.packageName ?: continue
        if (gesture.normalizedPath.size < 2) continue

        val diff = calculateBestDirectionDifference(
            normalizedPoints,
            gesture.normalizedPath,
            allowBackward
        )
        val current = byPackage[packageName]
        if (current == null || diff < current) {
            byPackage[packageName] = diff
        }
    }

    return byPackage.entries
        .map { SimilarAppMatch(it.key, it.value) }
        .sortedWith(compareBy<SimilarAppMatch> { it.angularDifference }.thenBy { it.packageName })
        .take(max(0, limit))
}

fun blendNormalizedPaths(lineA: List<Point>, lineB: List<Point>, t: Double): List<Point> {
    val n = min(lineA.size, lineB.size)
    if (n < 2) return emptyList()

    val clamped = max(0.0, min(1.0, t))
    return (0 until n).map { i ->
        Point(
            x = lineA[i].x * (1 - clamped) + lineB[i].x * clamped,
            y = lineA[i].y * (1 - clamped) + lineB[i].y * clamped
        )
    }
}

fun isWithinThreshold(
    candidate: List<Point>,
    reference: List<Point>,
    threshold: Double = ANGULAR_THRESHOLD
): Boolean {
    return calculateDifference(candidate, reference) < threshold
}

fun findBlendBoundsForDualMatch(
    oldPath: List<Point>,
    newPath: List<Point>
): BlendBoundsResult {
    val midpoint = blendNormalizedPaths(oldPath, newPath, 0.5)
    fun matchesBoth(candidate: List<Point>): Boolean {
        return isWithinThreshold(candidate, oldPath) && isWithinThreshold(candidate, newPath)
    }

    if (midpoint.size < 2 || !matchesBoth(midpoint)) {
        return BlendBoundsResult(false, 0.5, 0.5)
    }

    fun matchesAt(t: Double): Boolean = matchesBoth(blendNormalizedPaths(oldPath, newPath, t))

    val step = 0.05
    val iterations = 18

    var left = 0.5
    while (left - step >= 0 && matchesAt(left - step)) {
        left -= step
    }

    var right = 0.5
    while (right + step <= 1 && matchesAt(right + step)) {
        right += step
    }

    var minT = left
    if (left > 0 && !matchesAt(0.0)) {
        var lo = max(0.0, left - step)
        var hi = left
        repeat(iterations) {
            val mid = (lo + hi) / 2
            if (matchesAt(mid)) {
                hi = mid
            } else {
                lo = mid
            }
        }
        minT = hi
    } else if (matchesAt(0.0)) {
        minT = 0.0
    }

    var maxT = right
    if (right < 1 && !matchesAt(1.0)) {
        var lo = right
        var hi = min(1.0, right + step)
        repeat(iterations) {
            val mid = (lo + hi) / 2
            if (matchesAt(mid)) {
                lo = mid
            } else {
                hi = mid
            }
        }
        maxT = lo
    } else if (matchesAt(1.0)) {
        maxT = 1.0
    }

    return BlendBoundsResult(true, minT, maxT)
}
```

### 5. Build The Drawing View

Create a custom `GestureCanvasView` that collects raw points and reports normalized completed gestures.

```kotlin
class GestureCanvasView(context: Context) : View(context) {
    var onGestureComplete: ((List<Point>) -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var trailEffect: Boolean = false

    private val rawPoints = mutableListOf<Point>()
    private val path = android.graphics.Path()
    private var downX = 0f
    private var downY = 0f
    private var longPressTriggered = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 204)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        rawPoints.clear()
        path.reset()
        invalidate()
        onLongPress?.invoke()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPressRunnable)
                longPressTriggered = false
                downX = event.x
                downY = event.y
                rawPoints.clear()
                path.reset()
                rawPoints += Point(event.x.toDouble(), event.y.toDouble())
                path.moveTo(event.x, event.y)
                postDelayed(longPressRunnable, 800)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (longPressTriggered) return true

                val dx = event.x - downX
                val dy = event.y - downY
                val totalMovement = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
                if (totalMovement > 10.0) {
                    removeCallbacks(longPressRunnable)
                }

                rawPoints += Point(event.x.toDouble(), event.y.toDouble())
                path.lineTo(event.x, event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (longPressTriggered) {
                    longPressTriggered = false
                    clearNow()
                    return true
                }

                normalizeTo40Points(rawPoints)?.let { onGestureComplete?.invoke(it) }
                postDelayed({ clearNow() }, 400)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                longPressTriggered = false
                clearNow()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trailEffect) {
            drawTrail(canvas)
        } else {
            canvas.drawPath(path, paint)
        }
    }

    private fun clearNow() {
        rawPoints.clear()
        path.reset()
        invalidate()
    }

    private fun drawTrail(canvas: Canvas) {
        if (rawPoints.size < 2) return
        val n = rawPoints.size
        val segments = 5

        for (seg in 0 until segments) {
            val startIdx = kotlin.math.floor(seg * (n - 1).toDouble() / segments).toInt()
            val endIdx = minOf(n - 1, kotlin.math.floor((seg + 1) * (n - 1).toDouble() / segments).toInt())
            if (endIdx <= startIdx) continue

            val t = seg.toDouble() / (segments - 1)
            paint.alpha = ((0.15 + t * 0.85) * 255).toInt()
            paint.strokeWidth = (7.5 - t * 5).toFloat()

            val segmentPath = android.graphics.Path()
            val first = rawPoints[startIdx]
            segmentPath.moveTo(first.x.toFloat(), first.y.toFloat())
            for (i in startIdx + 1..endIdx) {
                val point = rawPoints[i]
                segmentPath.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            canvas.drawPath(segmentPath, paint)
        }

        paint.alpha = 255
        paint.strokeWidth = 3f
    }
}
```

### 6. Load Installed Apps

Use `PackageManager` to fetch launchable apps:

```kotlin
data class AppDetail(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

fun loadInstalledApps(context: Context): List<AppDetail> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .map { resolveInfo ->
            AppDetail(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }
        .sortedBy { it.label.lowercase() }
}
```

Filter search results exactly like the React Native service:

```kotlin
fun filterApps(apps: List<AppDetail>, query: String): List<AppDetail> {
    if (query.trim().isEmpty()) return apps
    val lower = query.lowercase()
    return apps.filter {
        it.label.lowercase().contains(lower) || it.packageName.lowercase().contains(lower)
    }
}
```

Launch an app:

```kotlin
fun launchApp(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    return true
}
```

### 7. Store Gestures And Settings

Use the same JSON envelope and keys. With `kotlinx.serialization`, define serializable models and store the JSON string in `SharedPreferences`:

```kotlin
@Serializable
data class StoredPoint(val x: Double, val y: Double)

@Serializable
data class StoredGesture(
    val label: String,
    val packageName: String? = null,
    val normalizedPath: List<StoredPoint>
)

@Serializable
data class GestureStore(
    val version: Int,
    val gestures: List<StoredGesture>
)

private const val STORAGE_KEY = "glyph_os_gestures"
private const val STORAGE_SCHEMA_VERSION = 2
```

Recommended behavior:

```txt
load null -> empty list
load malformed JSON -> empty list
load old plain array -> validate and wrap mentally as version 2
load envelope -> validate gestures
save -> always write { version: 2, gestures: [...] }
clear -> remove glyph_os_gestures
```

Settings should use these defaults to match current app behavior:

```txt
glyph_os_trail_effect -> false
glyph_os_launch_on_create_shortcut -> true
glyph_os_allow_backward_gestures -> true
glyph_os_onboarding_done -> false until completed
```

### 8. Wire The Main Gesture Flow

In `MainActivity` or a `ViewModel`, keep:

```kotlin
var savedGestures: List<SavedGesture> = repository.loadGestures()
var allowBackwardGestures: Boolean = settings.allowBackwardGestures(default = true)
var launchOnCreateShortcut: Boolean = settings.launchOnCreateShortcut(default = true)
```

When the canvas completes a gesture:

```kotlin
fun handleGestureComplete(normalizedPoints: List<Point>) {
    val match = matchGesture(normalizedPoints, savedGestures, allowBackwardGestures)
    if (match != null) {
        val packageName = match.gesture.packageName
        if (packageName != null) {
            val launched = launchApp(this, packageName)
            showFeedback(if (launched) "Launching ${match.gesture.label}" else "Launch failed")
        } else {
            showAssignAppDialog(match.gesture.label, match.gesture.normalizedPath)
        }
    } else {
        val label = "gesture_${System.currentTimeMillis()}"
        showAssignAppDialog(label, normalizedPoints)
    }
}
```

The assignment dialog should list installed apps, filter by search query, and pin the top five packages from:

```kotlin
val prioritizedPackageNames = rankSimilarApps(
    pendingGesture.normalizedPath,
    savedGestures,
    limit = 5,
    allowBackward = allowBackwardGestures
).map { it.packageName }
```

Sort the filtered app list so packages in `prioritizedPackageNames` appear first in that order.

### 9. Implement Assignment And Merge Flow

When the user selects an app:

```kotlin
val existingForApp = savedGestures.filter {
    it.packageName == selectedApp.packageName && it.normalizedPath.size >= 2
}
```

If none exist:

```kotlin
append SavedGesture(label, selectedApp.packageName, normalizedPath)
save
show "App assigned!"
optionally launch selected app
```

If one or more exist, pick the closest merge target:

```kotlin
val mergeTarget = existingForApp.minBy { existing ->
    calculateBestDirectionDifference(
        pendingGesture.normalizedPath,
        existing.normalizedPath,
        allowBackwardGestures
    )
}
```

Find merge bounds:

```kotlin
val bounds = findBlendBoundsForDualMatch(
    mergeTarget.normalizedPath,
    pendingGesture.normalizedPath
)
```

If `bounds.canMerge == false`, append the gesture as a separate binding.

If `bounds.canMerge == true`, show a merge dialog with:

```txt
oldPath = mergeTarget.normalizedPath
newPath = pendingGesture.normalizedPath
minT = bounds.minT
maxT = bounds.maxT
initialT = (bounds.minT + bounds.maxT) / 2
```

If the user chooses `Create Another`, append the new gesture.

If the user chooses `Merge`:

```kotlin
val blendedPath = blendNormalizedPaths(
    mergeTarget.normalizedPath,
    pendingGesture.normalizedPath,
    selectedT
)

if (blendedPath.size < 2) {
    append new gesture
} else {
    replace mergeTarget.normalizedPath with blendedPath
}
```

Then save, show `Gestures merged!`, and optionally launch the selected app.

### 10. Build The Management Screen

Open management from the canvas long press. Match these operations:

- List every saved gesture.
- Show each gesture's assigned app label if available, otherwise the package name or `No app assigned`.
- Draw a preview using the preview scaling math above.
- Reassign updates only `packageName` for that gesture.
- Delete removes the gesture by `label`.
- Clear all removes all stored gestures after confirmation.
- Settings toggles update `trailEffect`, `launchOnCreateShortcut`, and `allowBackwardGestures`.
- Provide shortcuts for wallpaper chooser and home app settings.

Wallpaper chooser intent:

```kotlin
startActivity(Intent("android.intent.action.SET_WALLPAPER"))
```

Home app settings fallback:

```kotlin
startActivity(Intent("android.settings.HOME_SETTINGS"))
```

### 11. Native Tests To Match The Current Suite

Create JUnit tests for the math before building UI around it.

Test normalization:

- Fewer than two points returns null.
- Identical points return null.
- A horizontal line returns exactly 40 points.
- First and last normalized points equal the original endpoints.
- A horizontal line of length `390` has adjacent normalized spacing close to `390 / 39 = 10`.
- An L-shaped path returns 40 points.

Test matching:

- Identical paths score `0`.
- A path with fewer than two points scores `Infinity`.
- Horizontal vs vertical scores close to `PI / 2`.
- Slight perturbations stay below `0.5`.
- Horizontal vs vertical is above `0.5`.
- Reversed paths match only when backward matching is enabled.
- `matchGesture` returns null for empty libraries and for all scores `>= 0.5`.
- Multiple candidates pick the smallest score.

Test ranking and merging:

- Ranking stores one best score per package.
- Ranking sorts by score, then package name.
- Blending with `t = 0` returns the first path.
- Blending with `t = 1` returns the second path.
- Blending with `t = 0.5` returns midpoint coordinates.
- Merge bounds return `canMerge = true` for highly similar paths.
- Merge flow appends when the user chooses create.
- Merge flow replaces the target path when the user chooses merge.

## Build Checklist

Use this order to avoid debugging everything at once:

1. Implement and test `GestureMath.kt`.
2. Add `GestureCanvasView` and print normalized points on release.
3. Add gesture storage with the same JSON envelope.
4. Add `PackageManager` app loading and launch by package name.
5. Wire unmatched gestures to an app picker.
6. Wire matched gestures to app launch.
7. Add long-press management.
8. Add similar-app ranking in the app picker.
9. Add merge detection, merge preview, and merge confirmation.
10. Add settings persistence and launcher/home/wallpaper intents.
11. Add the launcher manifest filters and set the app as the default home app on a device.
