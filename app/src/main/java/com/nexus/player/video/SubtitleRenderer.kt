package com.nexus.player.player.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.media3.common.text.Cue
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

class SubtitleRenderer(private val context: Context) {
    
    companion object {
        private const val TAG = "SubtitleRenderer"
        private val SRT_TIME_PATTERN = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})"
        )
        private val ASS_DIALOGUE_PATTERN = Pattern.compile(
            "^Dialogue:\\s*\\d+,\\s*(\\d+:\\d{2}:\\d{2}\\.\\d{2}),\\s*(\\d+:\\d{2}:\\d{2}\\.\\d{2}),\\s*(.*)$"
        )
    }
    
    private var subtitleFile: File? = null
    private var subtitles: List<SubtitleEntry> = emptyList()
    private var currentSubtitle: SubtitleEntry? = null
    private var subtitleOffsetMs: Int = 0
    private var isLoaded = false
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        isAntiAlias = true
    }
    
    data class SubtitleEntry(
        val index: Int,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String,
        val style: SubtitleStyle = SubtitleStyle()
    )
    
    data class SubtitleStyle(
        val fontSize: Float = 48f,
        val primaryColor: Int = Color.WHITE,
        val outlineColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.parseColor("#80000000"),
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false
    )
    
    fun loadSubtitle(filePath: String) {
        subtitleFile = File(filePath)
        
        val file = subtitleFile
        if (file == null || !file.exists()) {
            Log.e(TAG, "Subtitle file not found: $filePath")
            return
        }
        
        subtitles = when {
            filePath.endsWith(".srt", true) -> parseSrtFile(file)
            filePath.endsWith(".ass", true) || filePath.endsWith(".ssa", true) -> parseAssFile(file)
            else -> {
                Log.e(TAG, "Unsupported subtitle format: $filePath")
                emptyList()
            }
        }
        
        isLoaded = subtitles.isNotEmpty()
    }
    
    fun setOffset(offsetMs: Int) {
        subtitleOffsetMs = offsetMs
    }
    
    fun getSubtitleForTime(timeMs: Long): SubtitleEntry? {
        if (!isLoaded) return null
        
        val adjustedTime = timeMs + subtitleOffsetMs
        
        val entry = subtitles.find { entry ->
            adjustedTime in entry.startTimeMs..entry.endTimeMs
        }
        
        currentSubtitle = entry
        return entry
    }
    
    fun render(canvas: Canvas, width: Int, height: Int, currentTimeMs: Long) {
        val subtitle = getSubtitleForTime(currentTimeMs) ?: return
        
        val text = subtitle.text
        if (text.isEmpty()) return
        
        val textWidth = textPaint.measureText(text)
        val x = width / 2f
        val y = height * 0.85f
        
        val padding = 16f
        val bgLeft = x - textWidth / 2 - padding
        val bgRight = x + textWidth / 2 + padding
        val bgTop = y - textPaint.textSize - padding
        val bgBottom = y + padding
        
        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, backgroundPaint)
        canvas.drawText(text, x, y, textPaint)
    }
    
    fun renderMultipleSubtitles(
        canvas: Canvas,
        width: Int,
        height: Int,
        currentTimeMs: Long,
        cues: List<Cue>
    ) {
        cues.forEach { cue ->
            val text = cue.text.toString()
            if (text.isNotEmpty()) {
                val x = if (cue.position == Cue.DIMEN_UNSET) width / 2f else width * cue.position
                val y = if (cue.line == Cue.DIMEN_UNSET) height * 0.85f else height * cue.line
                
                canvas.drawText(text, x, y, textPaint)
            }
        }
    }
    
    private fun parseSrtFile(file: File): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        
        try {
            BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { reader ->
                var index = 0
                
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    
                    index = line.trim().toIntOrNull() ?: continue
                    
                    val timeLine = reader.readLine() ?: break
                    val timeMatcher = SRT_TIME_PATTERN.matcher(timeLine)
                    
                    if (!timeMatcher.find()) continue
                    
                    val startHours = timeMatcher.group(1)?.toIntOrNull() ?: 0
                    val startMinutes = timeMatcher.group(2)?.toIntOrNull() ?: 0
                    val startSeconds = timeMatcher.group(3)?.toIntOrNull() ?: 0
                    val startMillis = timeMatcher.group(4)?.toIntOrNull() ?: 0
                    
                    val endHours = timeMatcher.group(5)?.toIntOrNull() ?: 0
                    val endMinutes = timeMatcher.group(6)?.toIntOrNull() ?: 0
                    val endSeconds = timeMatcher.group(7)?.toIntOrNull() ?: 0
                    val endMillis = timeMatcher.group(8)?.toIntOrNull() ?: 0
                    
                    val startTime = ((startHours * 3600 + startMinutes * 60 + startSeconds) * 1000L) + startMillis
                    val endTime = ((endHours * 3600 + endMinutes * 60 + endSeconds) * 1000L) + endMillis
                    
                    val textBuilder = StringBuilder()
                    while (true) {
                        val textLine = reader.readLine() ?: break
                        if (textLine.isBlank()) break
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                        textBuilder.append(textLine)
                    }
                    
                    val style = parseSrtStyle(textBuilder.toString())
                    val cleanText = stripSrtTags(textBuilder.toString())
                    
                    entries.add(
                        SubtitleEntry(
                            index = index,
                            startTimeMs = startTime,
                            endTimeMs = endTime,
                            text = cleanText,
                            style = style
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SRT file", e)
        }
        
        return entries.sortedBy { it.startTimeMs }
    }
    
    private fun parseAssFile(file: File): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        var inEvents = false
        
        try {
            BufferedReader(InputStreamReader(FileInputStream(file), "UTF-8")).use { reader ->
                var index = 0
                
                while (true) {
                    val line = reader.readLine() ?: break
                    
                    when {
                        line.startsWith("[Events]") -> {
                            inEvents = true
                            continue
                        }
                        line.startsWith("[") -> {
                            inEvents = false
                            continue
                        }
                    }
                    
                    if (!inEvents) continue
                    
                    val matcher = ASS_DIALOGUE_PATTERN.matcher(line)
                    if (matcher.find()) {
                        val startTime = parseAssTime(matcher.group(1) ?: "0:00:00.00")
                        val endTime = parseAssTime(matcher.group(2) ?: "0:00:00.00")
                        val text = matcher.group(3) ?: ""
                        
                        val cleanText = stripAssTags(text)
                        
                        entries.add(
                            SubtitleEntry(
                                index = ++index,
                                startTimeMs = startTime,
                                endTimeMs = endTime,
                                text = cleanText
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ASS file", e)
        }
        
        return entries.sortedBy { it.startTimeMs }
    }
    
    private fun parseAssTime(time: String): Long {
        val parts = time.split(":")
        if (parts.size != 3) return 0
        
        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0
        
        val secondParts = parts[2].split(".")
        val seconds = secondParts[0].toIntOrNull() ?: 0
        val centiseconds = if (secondParts.size > 1) {
            (secondParts[1] + "00").take(2).toIntOrNull() ?: 0
        } else 0
        
        return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + (centiseconds * 10L)
    }
    
    private fun parseSrtStyle(text: String): SubtitleStyle {
        var bold = false
        var italic = false
        var underline = false
        var color = Color.WHITE
        
        if (text.contains("<b>", true)) bold = true
        if (text.contains("<i>", true)) italic = true
        if (text.contains("<u>", true)) underline = true
        
        val colorPattern = Pattern.compile("<font\\s+color=[\"']([#\\w]+)[\"']>", Pattern.CASE_INSENSITIVE)
        val colorMatcher = colorPattern.matcher(text)
        if (colorMatcher.find()) {
            try {
                color = Color.parseColor(colorMatcher.group(1) ?: "#FFFFFF")
            } catch (e: Exception) {
                color = Color.WHITE
            }
        }
        
        return SubtitleStyle(
            bold = bold,
            italic = italic,
            underline = underline,
            primaryColor = color
        )
    }
    
    private fun stripSrtTags(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\{[^}]+\\}"), "")
            .trim()
    }
    
    private fun stripAssTags(text: String): String {
        return text
            .replace(Regex("\\{[^}]+\\}"), "")
            .replace(Regex("\\\\[Nn]"), "\n")
            .replace(Regex("\\\\[Hh]"), " ")
            .trim()
    }
    
    fun release() {
        subtitles = emptyList()
        currentSubtitle = null
        subtitleFile = null
        isLoaded = false
    }
}
