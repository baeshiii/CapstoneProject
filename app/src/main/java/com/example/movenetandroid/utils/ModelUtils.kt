package com.example.movenetandroid.utils

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.io.IOException

object ModelUtils {
    fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun createInterpreter(context: Context, modelFileName: String): Interpreter {
        val model = loadModelFile(context, modelFileName)
        return Interpreter(model)
    }

    fun runMoveNet(interpreter: Interpreter, inputBuffer: TensorBuffer, outputBuffer: TensorBuffer) {
        interpreter.run(inputBuffer.buffer, outputBuffer.buffer.rewind())
    }
} 