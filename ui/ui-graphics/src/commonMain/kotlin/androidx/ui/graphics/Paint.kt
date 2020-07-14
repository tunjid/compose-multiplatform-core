/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.graphics

/**
 * Default alpha value used on [Paint]. This value will draw source content fully opaque.
 */
const val DefaultAlpha: Float = 1.0f

expect class NativePaint

internal expect fun makeNativePaint(): NativePaint

internal expect fun NativePaint.setNativeBlendMode(value: BlendMode)
internal expect fun NativePaint.setNativeColorFilter(value: ColorFilter?)
internal expect fun NativePaint.getNativeAlpha(): Float
internal expect fun NativePaint.setNativeAlpha(value: Float)
internal expect fun NativePaint.getNativeAntiAlias(): Boolean
internal expect fun NativePaint.setNativeAntiAlias(value: Boolean)
internal expect fun NativePaint.getNativeColor(): Color
internal expect fun NativePaint.setNativeColor(value: Color)
internal expect fun NativePaint.getNativeStyle(): PaintingStyle
internal expect fun NativePaint.setNativeStyle(value: PaintingStyle)
internal expect fun NativePaint.getNativeStrokeWidth(): Float
internal expect fun NativePaint.setNativeStrokeWidth(value: Float)
internal expect fun NativePaint.getNativeStrokeCap(): StrokeCap
internal expect fun NativePaint.setNativeStrokeCap(value: StrokeCap)
internal expect fun NativePaint.getNativeStrokeJoin(): StrokeJoin
internal expect fun NativePaint.setNativeStrokeJoin(value: StrokeJoin)
internal expect fun NativePaint.getNativeStrokeMiterLimit(): Float
internal expect fun NativePaint.setNativeStrokeMiterLimit(value: Float)
internal expect fun NativePaint.getNativeFilterQuality(): FilterQuality
internal expect fun NativePaint.setNativeFilterQuality(value: FilterQuality)
internal expect fun NativePaint.setNativeShader(value: Shader?)
internal expect fun NativePaint.setNativePathEffect(value: NativePathEffect?)

class Paint {

    private var internalPaint = makeNativePaint()
    private var _blendMode = BlendMode.srcOver
    private var internalShader: Shader? = null
    private var internalColorFilter: ColorFilter? = null

    fun asFrameworkPaint(): NativePaint = internalPaint

    var alpha: Float
        get() = internalPaint.getNativeAlpha()
        set(value) {
            internalPaint.setNativeAlpha(value)
        }

    // Whether to apply anti-aliasing to lines and images drawn on the
    // canvas.
    //
    // Defaults to true.
    var isAntiAlias: Boolean
            get() = internalPaint.getNativeAntiAlias()
            set(value) {
                // We encode true as zero and false as one because the default value, which
                // we always encode as zero, is true.
                // final int encoded = value ? 0 : 1;
                // _data.setInt32(_kIsAntiAliasOffset, encoded, _kFakeHostEndian);
                internalPaint.setNativeAntiAlias(value)
            }

    // The color to use when stroking or filling a shape.
    //
    // Defaults to opaque black.
    //
    // See also:
    //
    //  * [style], which controls whether to stroke or fill (or both).
    //  * [colorFilter], which overrides [color].
    //  * [shader], which overrides [color] with more elaborate effects.
    //
    // This color is not used when compositing. To colorize a layer, use
    // [colorFilter].
    var color: Color
        get() = internalPaint.getNativeColor()
        set(color) {
            internalPaint.setNativeColor(color)
        }

    // A blend mode to apply when a shape is drawn or a layer is composited.
    //
    // The source colors are from the shape being drawn (e.g. from
    // [Canvas.drawPath]) or layer being composited (the graphics that were drawn
    // between the [Canvas.saveLayer] and [Canvas.restore] calls), after applying
    // the [colorFilter], if any.
    //
    // The destination colors are from the background onto which the shape or
    // layer is being composited.
    //
    // Defaults to [BlendMode.srcOver].
    //
    // See also:
    //
    //  * [Canvas.saveLayer], which uses its [Paint]'s [blendMode] to composite
    //    the layer when [restore] is called.
    //  * [BlendMode], which discusses the user of [saveLayer] with [blendMode].
    var blendMode: BlendMode
        get() = _blendMode
        set(value) {
            _blendMode = value
            internalPaint.setNativeBlendMode(value)
        }

    // Whether to paint inside shapes, the edges of shapes, or both.
    //
    // Defaults to [PaintingStyle.fill].
    var style: PaintingStyle
        get() = internalPaint.getNativeStyle()
        set(value) {
            internalPaint.setNativeStyle(value)
        }

    // How wide to make edges drawn when [style] is set to
    // [PaintingStyle.stroke]. The width is given in logical pixels measured in
    // the direction orthogonal to the direction of the path.
    //
    // Defaults to 0.0, which correspond to a hairline width.
    var strokeWidth: Float
        get() = internalPaint.getNativeStrokeWidth()
        set(value) {
            internalPaint.setNativeStrokeWidth(value)
        }

    // The kind of finish to place on the end of lines drawn when
    // [style] is set to [PaintingStyle.stroke].
    //
    // Defaults to [StrokeCap.butt], i.e. no caps.
    var strokeCap: StrokeCap
        get() = internalPaint.getNativeStrokeCap()
        set(value) {
            internalPaint.setNativeStrokeCap(value)
        }

    // The kind of finish to place on the joins between segments.
    //
    // This applies to paths drawn when [style] is set to [PaintingStyle.stroke],
    // It does not apply to points drawn as lines with [Canvas.drawPoints].
    //
    // Defaults to [StrokeJoin.miter], i.e. sharp corners. See also
    // [strokeMiterLimit] to control when miters are replaced by bevels.
    var strokeJoin: StrokeJoin
        get() = internalPaint.getNativeStrokeJoin()
        set(value) {
            internalPaint.setNativeStrokeJoin(value)
        }

    // The limit for miters to be drawn on segments when the join is set to
    // [StrokeJoin.miter] and the [style] is set to [PaintingStyle.stroke]. If
    // this limit is exceeded, then a [StrokeJoin.bevel] join will be drawn
    // instead. This may cause some 'popping' of the corners of a path if the
    // angle between line segments is animated.
    //
    // This limit is expressed as a limit on the length of the miter.
    //
    // Defaults to 4.0.  Using zero as a limit will cause a [StrokeJoin.bevel]
    // join to be used all the time.
    var strokeMiterLimit: Float
        get() = internalPaint.getNativeStrokeMiterLimit()
        set(value) {
            internalPaint.setNativeStrokeMiterLimit(value)
        }

    // Controls the performance vs quality trade-off to use when applying
    // when drawing images, as with [Canvas.drawImageRect]
    //
    // Defaults to [FilterQuality.none].
    // TODO(ianh): verify that the image drawing methods actually respect this
    var filterQuality: FilterQuality
        get() = internalPaint.getNativeFilterQuality()
        set(value) {
            internalPaint.setNativeFilterQuality(value)
        }

    // The shader to use when stroking or filling a shape.
    //
    // When this is null, the [color] is used instead.
    //
    // See also:
    //
    //  * [Gradient], a shader that paints a color gradient.
    //  * [ImageShader], a shader that tiles an [Image].
    //  * [colorFilter], which overrides [shader].
    //  * [color], which is used if [shader] and [colorFilter] are null.
    var shader: Shader?
        get() = internalShader
        set(value) {
            internalShader = value
            internalPaint.setNativeShader(internalShader)
        }

    // A color filter to apply when a shape is drawn or when a layer is
    // composited.
    //
    // See [ColorFilter] for details.
    //
    // When a shape is being drawn, [colorFilter] overrides [color] and [shader].
    var colorFilter: ColorFilter?
        get() = internalColorFilter
        set(value) {
            internalColorFilter = value
            internalPaint.setNativeColorFilter(value)
        }
}