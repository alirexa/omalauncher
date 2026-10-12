package dev.omalauncher.widgets

import dev.omalauncher.widgets.lang.WidgetError
import java.io.File

/** Resolve at the time of access, including after an editor replaces a watched file. */
internal fun resolveWidgetFile(path: String, hostFile: (String) -> File, allowedRoots: List<File>, privateRoots: List<File> = emptyList()): File {
    val guest = if (path.startsWith("~/")) "/root/" + path.removePrefix("~/") else path
    if (guest.split('/').any { it == ".." }) throw WidgetError("file path escapes Linux")
    val file = hostFile(guest).canonicalFile
    if (allowedRoots.none { file.toPath().startsWith(it.canonicalFile.toPath()) }) throw WidgetError("file path escapes Linux")
    if (privateRoots.any { file.toPath().startsWith(it.canonicalFile.toPath()) }) throw WidgetError("launcher-internal widget data is private")
    return file
}
