package com.bismuth.app.engine

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message

object PromptFormatting {

    fun buildUserContents(
        text: String,
        imagePngBytes: List<ByteArray> = emptyList(),
        audioBytes: List<ByteArray> = emptyList(),
    ): Contents {
        if (imagePngBytes.isEmpty() && audioBytes.isEmpty()) {
            return Contents.of(Content.Text(text))
        }

        val parts = mutableListOf<Content>()
        audioBytes.forEach { parts.add(Content.AudioBytes(it)) }
        imagePngBytes.forEach { parts.add(Content.ImageBytes(it)) }
        if (text.isNotBlank()) {
            parts.add(Content.Text(text))
        }
        return Contents.of(parts)
    }

    fun buildSystemInstruction(text: String): Contents {
        return Contents.of(Content.Text(text))
    }

    fun buildToolResponse(toolName: String, resultJson: String): Message {
        return Message.tool(
            Contents.of(Content.ToolResponse(toolName, resultJson))
        )
    }
}
