package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.createSkiaLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.native.ComposeLayer
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.Foundation.NSCoder
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import org.jetbrains.skiko.SkikoUIView
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSValue
import platform.UIKit.CGRectValue
import platform.UIKit.setClipsToBounds
import platform.darwin.NSObject

// The only difference with macos' Window is that
// it has return type of UIViewController rather than unit.
fun Application(
    title: String = "JetpackNativeWindow",
    content: @Composable () -> Unit = { }

) = ComposeWindow().apply {
    setTitle(title)
    setContent(content)
} as UIViewController

@ExportObjCClass
internal actual class ComposeWindow : UIViewController {
    @OverrideInit
    actual constructor() : super(nibName = null, bundle = null)

    @OverrideInit
    constructor(coder: NSCoder) : super(coder)

    private lateinit var layer: ComposeLayer
    private lateinit var content: @Composable () -> Unit
    private val keyboardVisibilityListener = object : NSObject() {
        @Suppress("unused")
        @ObjCAction
        fun keyboardWillShow(arg: NSNotification) {
            val keyboardInfo = arg.userInfo!!["UIKeyboardFrameEndUserInfoKey"] as NSValue
            val keyboardHeight = keyboardInfo.CGRectValue().useContents { size.height }
            val screenHeight = UIScreen.mainScreen.bounds.useContents { size.height }
            val focused = layer.getActiveFocusRect()
            if (focused != null) {
                val focusedBottom = focused.bottom + getTopLeftOffset().y
                val hiddenPartOfFocusedElement = focusedBottom + keyboardHeight - screenHeight
                if (hiddenPartOfFocusedElement > 0) {
                    // If focused element hidden by keyboard, then change UIView bounds.
                    // Focused element will be visible
                    view.setClipsToBounds(true)
                    val (width, height) = getViewFrameSize()
                    view.layer.setBounds(
                        CGRectMake(
                            x = 0.0,
                            y = hiddenPartOfFocusedElement,
                            width = width.toDouble(),
                            height = height.toDouble()
                        )
                    )
                }
            }
        }

        @Suppress("unused")
        @ObjCAction
        fun keyboardWillHide(arg: NSNotification) {
            val (width, height) = getViewFrameSize()
            view.layer.setBounds(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        }

        @Suppress("unused")
        @ObjCAction
        fun keyboardDidHide(arg: NSNotification) {
            view.setClipsToBounds(false)
        }
    }

    actual fun setTitle(title: String) {
        println("TODO: set title to SkiaWindow")
    }

    override fun loadView() {
        val skiaLayer = createSkiaLayer()
        val skikoUIView = SkikoUIView(skiaLayer).load()
        view = skikoUIView
        layer = ComposeLayer(
            layer = skiaLayer,
            showSoftwareKeyboard = {
                skikoUIView.showScreenKeyboard()
            },
            hideSoftwareKeyboard = {
                skikoUIView.hideScreenKeyboard()
            },
            getTopLeftOffset = ::getTopLeftOffset,
        )
        layer.setContent(content = content)
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        val (width, height) = getViewFrameSize()
        layer.setSize(width, height)
        NSNotificationCenter.defaultCenter.addObserver(
            observer = keyboardVisibilityListener,
            selector = NSSelectorFromString("keyboardWillShow:"),
            name = platform.UIKit.UIKeyboardWillShowNotification,
            `object` = null
        )
        NSNotificationCenter.defaultCenter.addObserver(
            observer = keyboardVisibilityListener,
            selector = NSSelectorFromString("keyboardWillHide:"),
            name = platform.UIKit.UIKeyboardWillHideNotification,
            `object` = null
        )
        NSNotificationCenter.defaultCenter.addObserver(
            observer = keyboardVisibilityListener,
            selector = NSSelectorFromString("keyboardDidHide:"),
            name = platform.UIKit.UIKeyboardDidHideNotification,
            `object` = null
        )
    }

    // viewDidUnload() is deprecated and not called.
    override fun viewDidDisappear(animated: Boolean) {
        this.dispose()
        NSNotificationCenter.defaultCenter.removeObserver(
            observer = keyboardVisibilityListener,
            name = platform.UIKit.UIKeyboardWillShowNotification,
            `object` = null
        )
        NSNotificationCenter.defaultCenter.removeObserver(
            observer = keyboardVisibilityListener,
            name = platform.UIKit.UIKeyboardWillHideNotification,
            `object` = null
        )
        NSNotificationCenter.defaultCenter.removeObserver(
            observer = keyboardVisibilityListener,
            name = platform.UIKit.UIKeyboardDidHideNotification,
            `object` = null
        )
    }

    actual fun setContent(
        content: @Composable () -> Unit
    ) {
        println("ComposeWindow.setContent")
        this.content = content
    }

    actual fun dispose() {
        layer.dispose()
    }

    private fun getViewFrameSize(): IntSize {
        val (width, height) = view.frame().useContents { this.size.width to this.size.height }
        return IntSize(width.toInt(), height.toInt())
    }

    private fun getTopLeftOffset(): Offset {
        val topLeftPoint =
            view.coordinateSpace().convertPoint(
                point = CGPointMake(0.0, 0.0),
                toCoordinateSpace = UIScreen.mainScreen.coordinateSpace()
            )
        return topLeftPoint.useContents { Offset(x.toFloat(), y.toFloat()) }
    }

}