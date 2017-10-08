package net.mbonnin.arcanetracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.text.format.DateFormat
import android.widget.ImageView
import net.mbonnin.arcanetracker.detector.*
import net.mbonnin.arcanetracker.parser.ArenaParser
import net.mbonnin.arcanetracker.parser.LoadingScreenParser
import rx.Single
import rx.SingleSubscriber
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenCapture private constructor(internal var mediaProjection: MediaProjection) : ImageReader.OnImageAvailableListener {
    private val mDetector: Detector
    internal var mCallback: MediaProjection.Callback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
        }
    }
    internal var mWidth: Int = 0
    internal var mHeight: Int = 0
    internal var mImageReader: ImageReader
    internal var mHandler = Handler()
    private val mSubscriberList = LinkedList<SingleSubscriber<in File>>()

    override fun onImageAvailable(reader: ImageReader) {
        val image = reader.acquireLatestImage()
        if (image != null /*Handler*/) {
            if (image.planes.size != 1) {
                Timber.d("unknown image with %d planes", image.planes.size)
                image.close()
                return
            }

            val bbImage = ByteBufferImage(image.width, image.height, image.planes[0].buffer, image.planes[0].rowStride)

            var subscriber: SingleSubscriber<in File>? = null
            synchronized(mSubscriberList) {
                if (!mSubscriberList.isEmpty()) {
                    subscriber = mSubscriberList.removeFirst()
                }
            }

            if (subscriber != null) {
                val now = DateFormat.format("yyyy_MM_dd_hh_mm_ss", Date())
                val file = File(ArcaneTrackerApplication.get().getExternalFilesDir(null), "screenshot_" + now + ".jpg")
                val bitmap = Bitmap.createBitmap(bbImage.w, bbImage.h, Bitmap.Config.ARGB_8888)
                val buffer = bbImage.buffer.asIntBuffer()
                val stride = bbImage.stride
                for (j in 0 until bbImage.h) {
                    for (i in 0 until bbImage.w) {
                        val r = buffer.get(i * 4 + 0 + j * stride).and(0xff)
                        val g = buffer.get(i * 4 + 1 + j * stride).and(0xff)
                        val b = buffer.get(i * 4 + 2 + j * stride).and(0xff)
                        bitmap.setPixel(i, j, Color.argb(255, r, g, b))
                    }
                }
                try {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(file))
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }

                mHandler.post({
                    val imageView = ImageView(ArcaneTrackerApplication.getContext())
                    imageView.setImageBitmap(bitmap)
                    val params = ViewManager.Params()
                    params.x = 0
                    params.y = 0
                    params.w = bitmap.width / 2
                    params.h = bitmap.height / 2
                    ViewManager.get().addView(imageView, params)
                })

                subscriber!!.onSuccess(file)
            }

            if (LoadingScreenParser.MODE_TOURNAMENT == LoadingScreenParser.get().mode) {
                val format = mDetector.detectFormat(bbImage)
                if (format != FORMAT_UNKNOWN) {
                    ScreenCaptureResult.setFormat(format)
                }
                val mode = mDetector.detectMode(bbImage)
                if (mode != MODE_UNKNOWN) {
                    ScreenCaptureResult.setMode(mode)
                    if (mode == MODE_RANKED) {
                        val rank = mDetector.detectRank(bbImage)
                        if (rank != RANK_UNKNOWN) {
                            ScreenCaptureResult.setRank(rank)
                        }
                    }
                }
            }

            if (LoadingScreenParser.MODE_DRAFT == LoadingScreenParser.get().mode
                    && ArenaParser.DRAFT_MODE_DRAFTING == ArenaParser.get().draftMode) {
                val hero = getPlayerClass(DeckList.getArenaDeck().classIndex)

                val arenaResults = mDetector.detectArenaHaar(bbImage, hero)
                ScreenCaptureResult.setArena(arenaResults, hero)
            } else {
                ScreenCaptureResult.clearArena()
            }
            image.close()
        }
    }

    init {
        mediaProjection.registerCallback(mCallback, null)

        mDetector = Detector(ArcaneTrackerApplication.get(), ArcaneTrackerApplication.get().hasTabletLayout())

        val wm = ArcaneTrackerApplication.get().getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val display = wm.defaultDisplay
        val point = Point()
        display.getRealSize(point)

        // if we start in landscape, we might have the wrong orientation
        mWidth = if (point.x > point.y) point.x else point.y
        mHeight = if (point.y < point.x) point.y else point.x

        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 3)

        val worker = ScreenCaptureWorker()
        worker.start()
        val handler = worker.waitUntilReady()
        mImageReader.setOnImageAvailableListener(this, handler)
        mediaProjection.createVirtualDisplay("Arcane Tracker",
                mWidth, mHeight, 320,
                0,
                mImageReader.surface, null, null)/*Callbacks*/
    }

    fun screenShotSingle(): Single<File> {
        return Single.create { singleSubscriber ->
            synchronized(mSubscriberList) {
                mSubscriberList.add(singleSubscriber)
            }
        }
    }

    /*
     * the thread where the image processing is made. Maybe we could have reused the ImageReader looper
     */
    internal class ScreenCaptureWorker : HandlerThread("ScreenCaptureWorker") {

        fun waitUntilReady(): Handler {
            return Handler(looper)
        }
    }

    companion object {
        private var sScreenCapture: ScreenCapture? = null

        fun get(): ScreenCapture? {
            return sScreenCapture
        }

        fun create(mediaProjection: MediaProjection): ScreenCapture? {
            sScreenCapture = ScreenCapture(mediaProjection)
            return sScreenCapture
        }
    }

}
