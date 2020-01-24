package com.digitalappsbd.app.epurreader.drm

import android.app.ProgressDialog
import android.net.Uri
import com.digitalappsbd.app.epurreader.db.Book
import org.readium.r2.shared.Publication
import org.readium.r2.shared.drm.DRM
import org.readium.r2.streamer.parser.PubBox
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File


data class DRMFulfilledPublication(
  val localURL: String,
  val suggestedFilename: String
)

interface DRMLibraryService {
  val brand: DRM.Brand
  fun canFulfill(file: String): Boolean
  fun fulfill(byteArray: ByteArray, completion: (Any?) -> Unit)
  fun loadPublication(publication: String, drm: DRM, completion: (Any?) -> Unit)
}

interface LCPLibraryActivityService {
  fun parseIntentLcpl(uriString: String, networkAvailable: Boolean)
  fun prepareAndStartActivityWithLCP(
    drm: DRM,
    pub: PubBox,
    book: Book,
    file: File,
    publicationPath: String,
    parser: EpubParser,
    publication: Publication,
    networkAvailable: Boolean
  )

  fun processLcpActivityResult(
    uri: Uri,
    it: Uri,
    progress: ProgressDialog,
    networkAvailable: Boolean
  )
}
