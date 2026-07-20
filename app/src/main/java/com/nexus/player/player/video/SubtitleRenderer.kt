package com.nexus.player.player.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
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
    }
    
    private var subtitles: List<SubtitleEntry> = emptyList()
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
        val text: String
    )
    
    fun loadSubtitle(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Subtitle file not found: $filePath")
            return
        }
        
        subtitles = parseSrtFile(file)
        isLoaded = subtitles.isNotEmpty()
    }
    
    fun setOffset(offsetMs: Int) {
        subtitleOffsetMs = offsetMs
    }
    
    fun getSubtitleForTime(timeMs: Long): SubtitleEntry? {
        if (!isLoaded) return null
        val adjustedTime = timeMs + subtitleOffsetMs
        return subtitles.find { adjustedTime in it.startTimeMs..it.endTimeMs }
    }
    
    fun render(canvas: Canvas, width: Int, height: Int, currentTimeMs: Long) {
        val subtitle = getSubtitleForTime(currentTimeMs) ?: return
        val text = subtitle.text
        if (text.isEmpty()) return
        
        val textWidth = textPaint.measureText(text)
        val x = width / 2f
        val y = height * 0.85f
        val padding = 16f
        
        canvas.drawRect(
            x - textWidth / 2 - padding,
            y - textPaint.textSize - padding,
            x + textWidth / 2 + padding,
            y + padding,
            backgroundPaint
        )
        canvas.drawText(text, x, y, textPaint)
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
                    
                    val startTime = ((timeMatcher.group(1)?.toIntOrNull() ?: 0) * 3600000L) +
                            ((timeMatcher.group(2)?.toIntOrNull() ?: 0) * 60000L) +
                            ((timeMatcher.group(3)?.toIntOrNull() ?: 0) * 1000L) +
                            (timeMatcher.group(4)?.toIntOrNull() ?: 0)
                    
                    val endTime = ((timeMatcher.group(5)?.toIntOrNull() ?: 0) * 3600000L) +
                            ((timeMatcher.group(6)?.toIntOrNull() ?: 0) * 60000L) +
                            ((timeMatcher.group(7)?.toIntOrNull() ?: 0) * 1000L) +
                            (timeMatcher.group(8)?.toIntOrNull() ?: 0)
                    
                    val textBuilder = StringBuilder()
                    while (true) {
                        val textLine = reader.readLine() ?: break
                        if (textLine.isBlank()) break
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                        textBuilder.append(textLine)
                    }
                    
                    entries.add(
                        SubtitleEntry(
                            index = index,
                            startTimeMs = startTime,
                            endTimeMs = endTime,
                            text = textBuilder.toString().replace(Regex("<[^>]+>"), "").trim()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SRT file", e)
        }
        
        return entries.sortedBy { it.startTimeMs }
    }
    
    fun release() {
        subtitles = emptyList()
        isLoaded = false
    }
}
