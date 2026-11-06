package com.louis.melo.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@Composable
actual fun rememberFileExchange(): FileExchange = remember { IosFileExchange() }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosFileExchange : FileExchange {
    private var delegate: PickerDelegate? = null

    @Suppress("DEPRECATION")
    private fun topController(): UIViewController? {
        var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }

    override fun export(fileName: String, content: String) {
        val path = NSTemporaryDirectory() + fileName
        NSString.create(string = content).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
        topController()?.presentViewController(sheet, animated = true, completion = null)
    }

    override fun import(onText: (String) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText), asCopy = true)
        val d = PickerDelegate { url ->
            val text = NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null)
            if (text != null) onText(text) else Messages.show("Fichier illisible")
            delegate = null
        }
        delegate = d
        picker.delegate = d
        topController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class PickerDelegate(private val onPick: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPick)
    }
}
