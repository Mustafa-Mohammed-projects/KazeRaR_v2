package com.kaze.rar

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.github.junrar.Archive
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

enum class Kind(val label: String) {
    ZIP("ZIP"), RAR("RAR"), SEVEN_Z("7Z"),
    TAR("TAR"), TAR_GZ("TAR.GZ"), TAR_BZ2("TAR.BZ2"), TAR_XZ("TAR.XZ"),
    GZ("GZ"), BZ2("BZ2"), XZ("XZ")
}

data class Item(val path: String, val size: Long, val isDir: Boolean)

data class Progress(
    val doneBytes: Long,
    val totalBytes: Long,
    val doneFiles: Int,
    val totalFiles: Int,
    val current: String
)

data class ExtractResult(val files: Int, val bytes: Long, val folder: String)

enum class Problem { UNSUPPORTED, RAR5, ENCRYPTED_ZIP, CORRUPT, IO }

class KazeException(val problem: Problem, cause: Throwable? = null) : Exception(cause?.message, cause)
class NeedPasswordException(val wrong: Boolean) : Exception()
class CancelledException : Exception()

/** Opens the real stream only when someone actually reads it (keeps listing cheap). */
private class LazyIn(private val open: () -> InputStream) : InputStream() {
    private var s: InputStream? = null
    private fun st(): InputStream = s ?: open().also { s = it }
    override fun read(): Int = st().read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = st().read(b, off, len)
    override fun close() { s?.close() }
}

object Formats {
    private val multiExt = listOf(".tar.gz", ".tar.bz2", ".tar.xz")

    fun fromName(name: String): Kind? {
        val n = name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Kind.TAR_GZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> Kind.TAR_BZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> Kind.TAR_XZ
            n.endsWith(".tar") -> Kind.TAR
            listOf(".zip", ".jar", ".apk", ".apks", ".xapk", ".aar", ".war", ".cbz").any { n.endsWith(it) } -> Kind.ZIP
            n.endsWith(".rar") || n.endsWith(".cbr") -> Kind.RAR
            n.endsWith(".7z") -> Kind.SEVEN_Z
            n.endsWith(".gz") -> Kind.GZ
            n.endsWith(".bz2") -> Kind.BZ2
            n.endsWith(".xz") -> Kind.XZ
            else -> null
        }
    }

    fun sniff(file: File): Kind? {
        val h = ByteArray(512)
        val n = FileInputStream(file).use { it.read(h) }
        fun b(i: Int) = if (i < n) h[i].toInt() and 0xFF else -1
        return when {
            b(0) == 0x50 && b(1) == 0x4B -> Kind.ZIP
            b(0) == 0x52 && b(1) == 0x61 && b(2) == 0x72 && b(3) == 0x21 -> Kind.RAR
            b(0) == 0x37 && b(1) == 0x7A && b(2) == 0xBC && b(3) == 0xAF -> Kind.SEVEN_Z
            b(0) == 0x1F && b(1) == 0x8B -> Kind.GZ
            b(0) == 0x42 && b(1) == 0x5A && b(2) == 0x68 -> Kind.BZ2
            b(0) == 0xFD && b(1) == 0x37 && b(2) == 0x7A && b(3) == 0x58 -> Kind.XZ
            n >= 262 && b(257) == 0x75 && b(258) == 0x73 && b(259) == 0x74 &&
                b(260) == 0x61 && b(261) == 0x72 -> Kind.TAR
            else -> null
        }
    }

    /** Magic bytes win; the file name only decides between "gz" and "tar.gz" style variants. */
    fun detect(file: File, name: String): Kind? {
        val byName = fromName(name)
        val byMagic = sniff(file)
        return when {
            byMagic == null -> byName
            byMagic == Kind.GZ && byName == Kind.TAR_GZ -> Kind.TAR_GZ
            byMagic == Kind.BZ2 && byName == Kind.TAR_BZ2 -> Kind.TAR_BZ2
            byMagic == Kind.XZ && byName == Kind.TAR_XZ -> Kind.TAR_XZ
            else -> byMagic
        }
    }

    fun stripExt(name: String): String {
        val n = name.lowercase()
        for (e in multiExt) if (n.endsWith(e)) return name.dropLast(e.length)
        for (e in listOf(".tgz", ".tbz2", ".tbz", ".txz")) if (n.endsWith(e)) return name.dropLast(e.length)
        val i = name.lastIndexOf('.')
        return if (i > 0) name.substring(0, i) else name
    }
}

class ArchiveEngine(private val ctx: Context) {

    // ---------------------------------------------------------------- listing

    fun list(file: File, kind: Kind, pw: String?, name: String): List<Item> {
        val out = ArrayList<Item>()
        iterate(file, kind, pw, name) { item, _ -> out.add(item) }
        return out
    }

    // ------------------------------------------------------------- extracting

    fun extract(
        file: File,
        kind: Kind,
        pw: String?,
        name: String,
        totalFiles: Int,
        totalBytes: Long,
        onProgress: (Progress) -> Unit,
        isCancelled: () -> Boolean
    ): ExtractResult {
        val root = clean(Formats.stripExt(name))
        val created = ArrayList<Uri>()
        var files = 0
        var bytes = 0L
        var lastEmit = 0L
        val buf = ByteArray(128 * 1024)

        try {
            iterate(file, kind, pw, name) { item, input ->
                if (isCancelled()) throw CancelledException()
                if (item.isDir) return@iterate
                val rel = safeRel(item.path) ?: return@iterate
                val shortName = rel.substringAfterLast('/')

                val (uri, out) = openOut(root, rel)
                created.add(uri)
                try {
                    out.use { o ->
                        while (true) {
                            if (isCancelled()) throw CancelledException()
                            val n = input.read(buf)
                            if (n < 0) break
                            try {
                                o.write(buf, 0, n)
                            } catch (e: IOException) {
                                throw KazeException(Problem.IO, e)
                            }
                            bytes += n
                            val now = SystemClock.uptimeMillis()
                            if (now - lastEmit > 60) {
                                lastEmit = now
                                onProgress(Progress(bytes, totalBytes, files, totalFiles, shortName))
                            }
                        }
                    }
                    markDone(uri)
                    files++
                } catch (e: Throwable) {
                    deleteQuietly(uri)
                    created.remove(uri)
                    throw e
                }
                onProgress(Progress(bytes, totalBytes, files, totalFiles, shortName))
            }
        } catch (e: CancelledException) {
            created.forEach { deleteQuietly(it) }
            throw e
        }
        return ExtractResult(files, bytes, "Download/Kaze RAR/$root")
    }

    // ------------------------------------------------------------- iteration

    private fun iterate(
        file: File,
        kind: Kind,
        pw: String?,
        name: String,
        cb: (Item, InputStream) -> Unit
    ) {
        val hasPw = !pw.isNullOrEmpty()
        try {
            iterateInner(file, kind, pw, name, cb)
        } catch (e: CancelledException) {
            throw e
        } catch (e: NeedPasswordException) {
            throw e
        } catch (e: KazeException) {
            throw e
        } catch (e: PasswordRequiredException) {
            throw NeedPasswordException(hasPw)
        } catch (e: Exception) {
            if (hasPw && (kind == Kind.SEVEN_Z || kind == Kind.RAR)) throw NeedPasswordException(true)
            throw KazeException(Problem.CORRUPT, e)
        }
    }

    private fun iterateInner(
        file: File,
        kind: Kind,
        pw: String?,
        name: String,
        cb: (Item, InputStream) -> Unit
    ) {
        when (kind) {
            Kind.ZIP -> ZipFile(file).use { zf ->
                for (e in zf.entries.toList()) {
                    val item = Item(e.name, e.size, e.isDirectory)
                    cb(item, LazyIn {
                        if (!zf.canReadEntryData(e)) throw KazeException(Problem.ENCRYPTED_ZIP)
                        zf.getInputStream(e)
                    })
                }
            }

            Kind.SEVEN_Z -> {
                val sz = if (pw.isNullOrEmpty()) SevenZFile(file) else SevenZFile(file, pw.toCharArray())
                sz.use {
                    val stream = object : InputStream() {
                        override fun read(): Int = sz.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int = sz.read(b, off, len)
                    }
                    while (true) {
                        val e = sz.nextEntry ?: break
                        cb(Item(e.name, e.size, e.isDirectory), stream)
                    }
                }
            }

            Kind.RAR -> {
                val archive = try {
                    if (pw.isNullOrEmpty()) Archive(file) else Archive(file, pw)
                } catch (e: Exception) {
                    throw mapRarOpen(e, pw)
                }
                archive.use { arc ->
                    val headers = arc.fileHeaders
                    if (pw.isNullOrEmpty() && headers.any { it.isEncrypted }) throw NeedPasswordException(false)
                    for (h in headers) {
                        val item = Item(h.fileName.replace('\\', '/'), h.fullUnpackSize, h.isDirectory)
                        cb(item, LazyIn { arc.getInputStream(h) })
                    }
                }
            }

            Kind.TAR, Kind.TAR_GZ, Kind.TAR_BZ2, Kind.TAR_XZ -> {
                TarArchiveInputStream(openCompressed(file, kind)).use { tis ->
                    while (true) {
                        @Suppress("DEPRECATION")
                        val e = tis.nextTarEntry ?: break
                        if (e.isSymbolicLink || e.isLink) continue
                        if (!tis.canReadEntryData(e)) continue
                        cb(Item(e.name, e.size, e.isDirectory), tis)
                    }
                }
            }

            Kind.GZ, Kind.BZ2, Kind.XZ -> {
                openCompressed(file, kind).use { s ->
                    cb(Item(Formats.stripExt(name), -1, false), s)
                }
            }
        }
    }

    private fun openCompressed(file: File, kind: Kind): InputStream {
        val base = BufferedInputStream(FileInputStream(file), 1 shl 16)
        return when (kind) {
            Kind.TAR_GZ, Kind.GZ -> GzipCompressorInputStream(base)
            Kind.TAR_BZ2, Kind.BZ2 -> BZip2CompressorInputStream(base)
            Kind.TAR_XZ, Kind.XZ -> XZCompressorInputStream(base)
            else -> base
        }
    }

    private fun mapRarOpen(e: Exception, pw: String?): Exception {
        val n = e.javaClass.simpleName
        val msg = e.message.orEmpty()
        return when {
            n.contains("V5", ignoreCase = true) -> KazeException(Problem.RAR5, e)
            n.contains("Encrypted", ignoreCase = true) ||
                n.contains("Password", ignoreCase = true) ||
                msg.contains("password", ignoreCase = true) ||
                msg.contains("encrypt", ignoreCase = true) -> NeedPasswordException(!pw.isNullOrEmpty())
            else -> KazeException(Problem.CORRUPT, e)
        }
    }

    // --------------------------------------------------------------- output

    private fun openOut(root: String, rel: String): Pair<Uri, OutputStream> {
        val dir = rel.substringBeforeLast('/', "")
        val fname = rel.substringAfterLast('/')
        val ext = fname.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val relPath = "Download/Kaze RAR/$root" + (if (dir.isEmpty()) "" else "/$dir") + "/"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fname)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw KazeException(Problem.IO)
        val os = try {
            resolver.openOutputStream(uri)
        } catch (e: Exception) {
            null
        }
        if (os == null) {
            deleteQuietly(uri)
            throw KazeException(Problem.IO)
        }
        return uri to BufferedOutputStream(os, 64 * 1024)
    }

    private fun markDone(uri: Uri) {
        val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        ctx.contentResolver.update(uri, v, null, null)
    }

    private fun deleteQuietly(uri: Uri) {
        try { ctx.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
    }

    // -------------------------------------------------------------- helpers

    /** Rejects "../" traversal and strips characters Android file names can't hold. */
    private fun safeRel(raw: String): String? {
        val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/") { clean(it) }
    }

    private fun clean(s: String): String {
        val c = s.map { ch -> if (ch in "<>:\"|?*" || ch.code < 32) '_' else ch }
            .joinToString("")
            .trim()
        return c.ifEmpty { "_" }.take(180)
    }
}
