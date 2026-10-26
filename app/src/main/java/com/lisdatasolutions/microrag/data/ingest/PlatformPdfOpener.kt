package com.lisdatasolutions.microrag.data.ingest

import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import com.lisdatasolutions.microrag.rag.ingest.PdfDamagedException
import com.lisdatasolutions.microrag.rag.ingest.PdfEncryptedException
import com.lisdatasolutions.microrag.rag.ingest.PdfOpener
import com.lisdatasolutions.microrag.rag.ingest.PdfPages
import com.lisdatasolutions.microrag.rag.ingest.PdfUnavailableException
import java.io.File
import java.io.IOException

/**
 * The system PDF reader (PDFium, updated with the system): `PdfRendererPreV` on every device with
 * the S extension 13 or newer, Android 15+ included (F06 research R2, R3, D-037, D-038).
 */
class PlatformPdfOpener : PdfOpener {

    override val isAvailable: Boolean
        get() = SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= MIN_S_EXTENSION

    override fun open(file: File): PdfPages {
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) < MIN_S_EXTENSION) throw PdfUnavailableException()
        return PreVPdfPages.open(file)
    }

    private companion object {
        const val MIN_S_EXTENSION = 13
    }
}

/**
 * Every type of `android.graphics.pdf` stays in this class, which is only loaded once the extension is
 * known to exist. No lambda takes those types either: their synthetic methods would break reflection
 * on devices without the extension (research R3).
 */
@RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
private class PreVPdfPages(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRendererPreV,
) : PdfPages {

    override val pageCount: Int get() = renderer.pageCount

    override fun pageText(index: Int): String {
        val page = renderer.openPage(index)
        try {
            val text = StringBuilder()
            for (content in page.textContents) text.append(content.text)
            return text.toString()
        } finally {
            page.close()
        }
    }

    override fun close() {
        try {
            renderer.close()
        } finally {
            descriptor.close()
        }
    }

    companion object {
        fun open(file: File): PreVPdfPages {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                return PreVPdfPages(descriptor, PdfRendererPreV(descriptor))
            } catch (e: SecurityException) {
                descriptor.close()
                throw PdfEncryptedException(e)
            } catch (e: IOException) {
                descriptor.close()
                throw PdfDamagedException(e)
            } catch (e: IllegalArgumentException) {
                descriptor.close()
                throw PdfDamagedException(e)
            }
        }
    }
}
