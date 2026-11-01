package it.jarvis.assistant.models

import java.io.File
import java.io.RandomAccessFile

/** Controlla i limiti dei campi protobuf prima di passare un ONNX al codice nativo. */
object ModelFiles {
    fun requireCompleteOnnx(file: File) {
        require(file.length() > 1024) { "Modello mancante o incompleto: ${file.name}. Riscaricalo." }
        RandomAccessFile(file, "r").use { input ->
            fun varint(): Long {
                var result = 0L
                for (shift in 0..63 step 7) {
                    check(input.filePointer < input.length()) { "Modello troncato: ${file.name}. Riscaricalo." }
                    val b = input.readUnsignedByte()
                    result = result or ((b and 127).toLong() shl shift)
                    if (b and 128 == 0) return result
                }
                error("Modello non valido: ${file.name}.")
            }
            var graph = false
            while (input.filePointer < input.length()) {
                val tag = varint()
                check(tag ushr 3 > 0) { "Modello non valido: ${file.name}." }
                val bytes = when ((tag and 7).toInt()) {
                    0 -> { varint(); 0L }
                    1 -> 8L
                    2 -> varint()
                    5 -> 4L
                    else -> error("Modello non valido: ${file.name}.")
                }
                check(bytes >= 0 && bytes <= input.length() - input.filePointer) { "Modello troncato: ${file.name}. Riscaricalo." }
                if (tag ushr 3 == 7L && tag and 7 == 2L) graph = true
                input.seek(input.filePointer + bytes)
            }
            check(graph) { "Il file ${file.name} non contiene un modello ONNX. Riscaricalo." }
        }
    }
}
