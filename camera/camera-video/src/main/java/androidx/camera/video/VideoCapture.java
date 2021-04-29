/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.camera.video;

import static androidx.camera.core.impl.ImageOutputConfig.OPTION_DEFAULT_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_MAX_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_SUPPORTED_RESOLUTIONS;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_ASPECT_RATIO;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_ROTATION;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_ATTACHED_USE_CASES_UPDATE_LISTENER;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_CAMERA_SELECTOR;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_CAPTURE_CONFIG_UNPACKER;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_DEFAULT_CAPTURE_CONFIG;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_DEFAULT_SESSION_CONFIG;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_SESSION_CONFIG_UNPACKER;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_SURFACE_OCCUPANCY_PRIORITY;
import static androidx.camera.core.internal.TargetConfig.OPTION_TARGET_CLASS;
import static androidx.camera.core.internal.TargetConfig.OPTION_TARGET_NAME;
import static androidx.camera.core.internal.ThreadConfig.OPTION_BACKGROUND_EXECUTOR;
import static androidx.camera.core.internal.UseCaseEventConfig.OPTION_USE_CASE_EVENT_CALLBACK;
import static androidx.camera.video.impl.VideoCaptureConfig.OPTION_VIDEO_OUTPUT;

import android.graphics.Rect;
import android.util.Pair;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.UiThread;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalUseCaseGroup;
import androidx.camera.core.Logger;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.core.UseCase;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.Config;
import androidx.camera.core.impl.ConfigProvider;
import androidx.camera.core.impl.DeferrableSurface;
import androidx.camera.core.impl.ImageOutputConfig;
import androidx.camera.core.impl.ImageOutputConfig.RotationValue;
import androidx.camera.core.impl.MutableConfig;
import androidx.camera.core.impl.MutableOptionsBundle;
import androidx.camera.core.impl.Observable;
import androidx.camera.core.impl.Observable.Observer;
import androidx.camera.core.impl.OptionsBundle;
import androidx.camera.core.impl.SessionConfig;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.core.impl.utils.Threads;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.internal.ThreadConfig;
import androidx.camera.video.VideoOutput.StreamState;
import androidx.camera.video.impl.VideoCaptureConfig;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * A use case that provides camera stream suitable for video application.
 *
 * <p>VideoCapture is used to create a camera stream suitable for video application. This stream
 * is used by the implementation of {@link VideoOutput}. Calling {@link #withOutput(VideoOutput)}
 * can generate a VideoCapture use case binding to the given VideoOutput.
 *
 * <p>When binding VideoCapture, VideoCapture will initialize the camera stream according to the
 * resolution found by the {@link QualitySelector} in VideoOutput. Then VideoCapture will invoke
 * {@link VideoOutput#onSurfaceRequested(SurfaceRequest)} to request VideoOutput to provide a
 * {@link Surface} via {@link SurfaceRequest#provideSurface} to complete the initialization
 * process. After VideoCapture is bound, updating the QualitySelector in VideoOutput will have no
 * effect. If it needs to change the resolution of the camera stream after VideoCapture is bound,
 * it has to unbind the original VideoCapture, update the QualitySelector in VideoOutput and then
 * re-bind the VideoCapture. If the implementation of VideoOutput does not support modifying the
 * QualitySelector afterwards, it has to create a new VideoOutput and VideoCapture for re-bind.
 *
 * @param <T> the type of VideoOutput
 */
public final class VideoCapture<T extends VideoOutput> extends UseCase {
    private static final String TAG = "VideoCapture";
    private static final Defaults DEFAULT_CONFIG = new Defaults();

    private DeferrableSurface mDeferrableSurface;
    private SurfaceRequest mSurfaceRequest;

    /**
     * Create a VideoCapture builder with a {@link VideoOutput}.
     *
     * @param videoOutput the associated VideoOutput.
     * @return the new Builder
     */
    @NonNull
    public static <T extends VideoOutput> VideoCapture<T> withOutput(@NonNull T videoOutput) {
        return new VideoCapture.Builder<T>(videoOutput).build();
    }

    /**
     * Creates a new video capture use case from the given configuration.
     *
     * @param config for this use case instance
     */
    VideoCapture(@NonNull VideoCaptureConfig<T> config) {
        super(config);
    }

    /**
     * Gets the {@link VideoOutput} associated to this VideoCapture.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public T getOutput() {
        return ((VideoCaptureConfig<T>) getCurrentConfig()).getVideoOutput();
    }

    /**
     * Sets the desired rotation of the output video.
     *
     * <p>In most cases this should be set to the current rotation returned by {@link
     * Display#getRotation()}.
     *
     * @param rotation Desired rotation of the output video.
     */
    public void setTargetRotation(@RotationValue int rotation) {
        setTargetRotationInternal(rotation);
    }

    @Override
    public void onAttached() {
        getOutput().getStreamState().addObserver(CameraXExecutors.mainThreadExecutor(),
                mStreamStateObserver);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @SuppressWarnings("unchecked")
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected Size onSuggestedResolutionUpdated(@NonNull Size suggestedResolution) {
        String cameraId = getCameraId();
        VideoCaptureConfig<T> config = (VideoCaptureConfig<T>) getCurrentConfig();

        SessionConfig.Builder sessionConfigBuilder = createPipeline(cameraId, config,
                suggestedResolution);
        updateSessionConfig(sessionConfigBuilder.build());

        return suggestedResolution;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void onDetached() {
        clearPipeline();

        getOutput().getStreamState().removeObserver(mStreamStateObserver);
    }

    @NonNull
    @Override
    public String toString() {
        return TAG + ":" + getName();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public UseCaseConfig<?> getDefaultConfig(boolean applyDefaultConfig,
            @NonNull UseCaseConfigFactory factory) {
        Config captureConfig = factory.getConfig(UseCaseConfigFactory.CaptureType.VIDEO_CAPTURE);

        if (applyDefaultConfig) {
            captureConfig = Config.mergeConfigs(captureConfig, DEFAULT_CONFIG.getConfig());
        }

        return captureConfig == null ? null :
                getUseCaseConfigBuilder(captureConfig).getUseCaseConfig();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    @Override
    protected UseCaseConfig<?> onMergeConfig(@NonNull CameraInfoInternal cameraInfo,
            @NonNull UseCaseConfig.Builder<?, ?, ?> builder) {

        updateTargetResolutionByQuality(cameraInfo, builder);

        return builder.getUseCaseConfig();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public UseCaseConfig.Builder<?, ?, ?> getUseCaseConfigBuilder(@NonNull Config config) {
        return Builder.fromConfig(config);
    }

    @UiThread
    @OptIn(markerClass = ExperimentalUseCaseGroup.class)
    @NonNull
    private SessionConfig.Builder createPipeline(@NonNull String cameraId,
            @NonNull VideoCaptureConfig<T> config,
            @NonNull Size resolution) {
        Threads.checkMainThread();

        mSurfaceRequest = new SurfaceRequest(resolution, getCamera(), false);
        config.getVideoOutput().onSurfaceRequested(mSurfaceRequest);
        Rect cropRect = getViewPortCropRect() != null ? getViewPortCropRect() : new Rect(0, 0,
                resolution.getWidth(), resolution.getHeight());
        mSurfaceRequest.updateTransformationInfo(SurfaceRequest.TransformationInfo.of(cropRect,
                getRelativeRotation(getCamera()), getTargetRotationInternal()));
        mDeferrableSurface = mSurfaceRequest.getDeferrableSurface();

        SessionConfig.Builder sessionConfigBuilder = SessionConfig.Builder.createFrom(config);
        sessionConfigBuilder.addSurface(mDeferrableSurface);
        sessionConfigBuilder.addErrorListener(
                (sessionConfig, error) -> resetPipeline(cameraId, config, resolution));

        return sessionConfigBuilder;
    }

    /**
     * Clear the internal pipeline so that the pipeline can be set up again.
     */
    @UiThread
    private void clearPipeline() {
        Threads.checkMainThread();

        if (mDeferrableSurface != null) {
            mDeferrableSurface.close();
            mDeferrableSurface = null;
        }

        mSurfaceRequest = null;
    }

    @UiThread
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    void resetPipeline(@NonNull String cameraId,
            @NonNull VideoCaptureConfig<T> config,
            @NonNull Size resolution) {
        clearPipeline();

        // Ensure the attached camera has not changed before resetting.
        // TODO(b/143915543): Ensure this never gets called by a camera that is not attached
        //  to this use case so we don't need to do this check.
        if (isCurrentCamera(cameraId)) {
            // Only reset the pipeline when the bound camera is the same.
            SessionConfig.Builder sessionConfigBuilder = createPipeline(cameraId, config,
                    resolution);
            updateSessionConfig(sessionConfigBuilder.build());
            notifyReset();
        }
    }

    /**
     * Provides a base static default configuration for the VideoCapture
     *
     * <p>These values may be overridden by the implementation. They only provide a minimum set of
     * defaults that are implementation independent.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final class Defaults implements ConfigProvider<VideoCaptureConfig<?>> {
        /** Surface occupancy priority to this use case */
        private static final int DEFAULT_SURFACE_OCCUPANCY_PRIORITY = 3;
        private static final VideoOutput DEFAULT_VIDEO_OUTPUT =
                SurfaceRequest::willNotProvideSurface;
        static final Size DEFAULT_RESOLUTION = new Size(1920, 1080);

        private static final VideoCaptureConfig<?> DEFAULT_CONFIG;

        static {
            Builder<?> builder = new Builder<>(DEFAULT_VIDEO_OUTPUT)
                    .setSurfaceOccupancyPriority(DEFAULT_SURFACE_OCCUPANCY_PRIORITY);

            DEFAULT_CONFIG = builder.getUseCaseConfig();
        }

        @NonNull
        @Override
        public VideoCaptureConfig<?> getConfig() {
            return DEFAULT_CONFIG;
        }
    }

    @Nullable
    private MediaSpec getMediaSpec() {
        return fetchObservableValue(getOutput().getMediaSpec(), null);
    }

    private final Observer<StreamState> mStreamStateObserver = new Observer<StreamState>() {
        @Override
        public void onNewData(@Nullable StreamState streamState) {
            Logger.d(TAG, "Receive streamState = " + streamState);
            if (streamState == StreamState.ACTIVE) {
                notifyActive();
            } else {
                notifyInactive();
            }
        }

        @Override
        public void onError(@NonNull Throwable t) {
            Logger.w(TAG, "Receive onError from StreamState observer", t);
        }
    };

    /**
     * Set {@link ImageOutputConfig#OPTION_TARGET_RESOLUTION} according to the resolution found
     * by the {@link QualitySelector} in VideoOutput.
     *
     * <p>If the device doesn't have any supported quality, a default resolution will be set.
     *
     * @throws IllegalArgumentException if not able to find a resolution by the QualitySelector
     * in VideoOutput.
     */
    private void updateTargetResolutionByQuality(@NonNull CameraInfoInternal cameraInfo,
            @NonNull UseCaseConfig.Builder<?, ?, ?> builder) throws IllegalArgumentException {
        MediaSpec mediaSpec = getMediaSpec();

        Preconditions.checkArgument(mediaSpec != null,
                "Unable to update target resolution by null MediaSpec.");

        Size resolution;
        if (QualitySelector.getSupportedQualities(cameraInfo).isEmpty()) {
            // When the device does not have any supported quality, even the most flexible
            // QualitySelector such as QualitySelector.of(QUALITY_HIGHEST), still cannot
            // find any resolution. This should be a rare case but will cause VideoCapture
            // to always fail to bind. The workaround is to set a default resolution.
            Logger.w(TAG,
                    "Can't find any supported quality on the device, use default resolution.");
            resolution = Defaults.DEFAULT_RESOLUTION;
        } else {
            QualitySelector qualitySelector = mediaSpec.getVideoSpec().getQualitySelector();

            int quality = qualitySelector.select(cameraInfo);

            Logger.d(TAG, "Found quality " + quality + " by qualitySelector " + qualitySelector);

            if (quality != QualitySelector.QUALITY_NONE) {
                resolution = QualitySelector.getResolution(cameraInfo, quality);
            } else {
                throw new IllegalArgumentException(
                        "Unable to find a resolution by QualitySelector: " + qualitySelector);
            }
        }

        int relativeRotation = cameraInfo.getSensorRotationDegrees(getTargetRotationInternal());
        boolean needRotate = relativeRotation == 90 || relativeRotation == 270;
        if (needRotate) {
            resolution = new Size(/* width= */resolution.getHeight(),
                    /* height= */resolution.getWidth());
        }
        Logger.d(TAG,
                "relativeRotation = " + relativeRotation + " and final resolution = " + resolution);

        builder.getMutableConfig().insertOption(OPTION_TARGET_RESOLUTION, resolution);
    }

    /**
     * Gets the snapshot value of the given {@link Observable}.
     *
     * <p>Note: Set {@code valueIfMissing} to a non-{@code null} value doesn't mean the method
     * will never return a {@code null} value. The observable could contain exact {@code null}
     * value.
     *
     * @param observable the observable
     * @param valueIfMissing if the observable doesn't contain value.
     * @param <T> the value type
     * @return the snapshot value of the given {@link Observable}.
     */
    @Nullable
    private static <T> T fetchObservableValue(@NonNull Observable<T> observable,
            @Nullable T valueIfMissing) {
        ListenableFuture<T> future = observable.fetchData();
        if (!future.isDone()) {
            return valueIfMissing;
        }
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            // Should not happened
            throw new IllegalStateException(e);
        }
    }

    /**
     * Builder for a {@link VideoCapture}.
     *
     * @param <T> the type of VideoOutput
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @SuppressWarnings("ObjectToString")
    public static final class Builder<T extends VideoOutput> implements
            UseCaseConfig.Builder<VideoCapture<T>, VideoCaptureConfig<T>, Builder<T>>,
            ImageOutputConfig.Builder<Builder<T>>, ThreadConfig.Builder<Builder<T>> {
        private final MutableOptionsBundle mMutableConfig;

        /** Creates a new Builder object. */
        Builder(@NonNull T videoOutput) {
            this(createInitialBundle(videoOutput));
        }

        @SuppressWarnings("unchecked")
        private Builder(@NonNull MutableOptionsBundle mutableConfig) {
            mMutableConfig = mutableConfig;

            if (!mMutableConfig.containsOption(OPTION_VIDEO_OUTPUT)) {
                throw new IllegalArgumentException("VideoOutput is required");
            }

            Class<?> oldConfigClass =
                    mutableConfig.retrieveOption(OPTION_TARGET_CLASS, null);
            if (oldConfigClass != null && !oldConfigClass.equals(VideoCapture.class)) {
                throw new IllegalArgumentException(
                        "Invalid target class configuration for "
                                + Builder.this
                                + ": "
                                + oldConfigClass);
            }

            setTargetClass((Class<VideoCapture<T>>) (Type) VideoCapture.class);
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        static Builder<? extends VideoOutput> fromConfig(@NonNull Config configuration) {
            return new Builder<>(MutableOptionsBundle.from(configuration));
        }

        /**
         * Generates a Builder from another Config object
         *
         * @param configuration An immutable configuration to pre-populate this builder.
         * @return The new Builder.
         */
        @NonNull
        public static <T extends VideoOutput> Builder<T> fromConfig(
                @NonNull VideoCaptureConfig<T> configuration) {
            return new Builder<>(MutableOptionsBundle.from(configuration));
        }

        @NonNull
        private static <T extends VideoOutput> MutableOptionsBundle createInitialBundle(
                @NonNull T videoOutput) {
            MutableOptionsBundle bundle = MutableOptionsBundle.create();
            bundle.insertOption(OPTION_VIDEO_OUTPUT, videoOutput);
            return bundle;
        }

        /**
         * {@inheritDoc}
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public MutableConfig getMutableConfig() {
            return mMutableConfig;
        }

        /**
         * {@inheritDoc}
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public VideoCaptureConfig<T> getUseCaseConfig() {
            return new VideoCaptureConfig<>(OptionsBundle.from(mMutableConfig));
        }

        /**
         * Builds an immutable {@link VideoCaptureConfig} from the current state.
         *
         * @return A {@link VideoCaptureConfig} populated with the current state.
         */
        @Override
        @NonNull
        public VideoCapture<T> build() {
            return new VideoCapture<>(getUseCaseConfig());
        }

        // Implementations of TargetConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setTargetClass(@NonNull Class<VideoCapture<T>> targetClass) {
            getMutableConfig().insertOption(OPTION_TARGET_CLASS, targetClass);

            // If no name is set yet, then generate a unique name
            if (null == getMutableConfig().retrieveOption(OPTION_TARGET_NAME, null)) {
                String targetName = targetClass.getCanonicalName() + "-" + UUID.randomUUID();
                setTargetName(targetName);
            }

            return this;
        }

        /**
         * Sets the name of the target object being configured, used only for debug logging.
         *
         * <p>The name should be a value that can uniquely identify an instance of the object being
         * configured.
         *
         * <p>If not set, the target name will default to an unique name automatically generated
         * with the class canonical name and random UUID.
         *
         * @param targetName A unique string identifier for the instance of the class being
         *                   configured.
         * @return the current Builder.
         */
        @Override
        @NonNull
        public Builder<T> setTargetName(@NonNull String targetName) {
            getMutableConfig().insertOption(OPTION_TARGET_NAME, targetName);
            return this;
        }

        // Implementations of ImageOutputConfig.Builder default methods

        /**
         * Sets the aspect ratio of the intended target for images from this configuration.
         *
         * <p>It is not allowed to set both target aspect ratio and target resolution on the same
         * use case.
         *
         * <p>The target aspect ratio is used as a hint when determining the resulting output aspect
         * ratio which may differ from the request, possibly due to device constraints.
         * Application code should check the resulting output's resolution.
         *
         * <p>If not set, resolutions with aspect ratio 16:9 will be considered in higher
         * priority.
         *
         * @param aspectRatio A {@link AspectRatio} representing the ratio of the target's width
         *                    and height.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setTargetAspectRatio(@AspectRatio.Ratio int aspectRatio) {
            getMutableConfig().insertOption(OPTION_TARGET_ASPECT_RATIO, aspectRatio);
            return this;
        }

        /**
         * Sets the rotation of the intended target for images from this configuration.
         *
         * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link
         * Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
         * Rotation values are relative to the "natural" rotation, {@link Surface#ROTATION_0}.
         *
         * <p>If not set, the target rotation will default to the value of
         * {@link Display#getRotation()} of the default display at the time the use case is
         * created. The use case is fully created once it has been attached to a camera.
         *
         * @param rotation The rotation of the intended target.
         * @return The current Builder.
         */
        @NonNull
        @Override
        public Builder<T> setTargetRotation(@RotationValue int rotation) {
            getMutableConfig().insertOption(OPTION_TARGET_ROTATION, rotation);
            return this;
        }

        /**
         * setTargetResolution is not supported on VideoCapture
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setTargetResolution(@NonNull Size resolution) {
            throw new UnsupportedOperationException("setTargetResolution is not supported.");
        }

        /**
         * Sets the default resolution of the intended target from this configuration.
         *
         * @param resolution The default resolution to choose from supported output sizes list.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setDefaultResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_DEFAULT_RESOLUTION, resolution);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setMaxResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_MAX_RESOLUTION, resolution);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setSupportedResolutions(
                @NonNull List<Pair<Integer, Size[]>> resolutions) {
            getMutableConfig().insertOption(OPTION_SUPPORTED_RESOLUTIONS, resolutions);
            return this;
        }

        // Implementations of ThreadConfig.Builder default methods

        /**
         * Sets the default executor that will be used for background tasks.
         *
         * <p>If not set, the background executor will default to an automatically generated
         * {@link Executor}.
         *
         * @param executor The executor which will be used for background tasks.
         * @return the current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setBackgroundExecutor(@NonNull Executor executor) {
            getMutableConfig().insertOption(OPTION_BACKGROUND_EXECUTOR, executor);
            return this;
        }

        // Implementations of UseCaseConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setDefaultSessionConfig(@NonNull SessionConfig sessionConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_SESSION_CONFIG, sessionConfig);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setDefaultCaptureConfig(@NonNull CaptureConfig captureConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_CAPTURE_CONFIG, captureConfig);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setSessionOptionUnpacker(
                @NonNull SessionConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_SESSION_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setCaptureOptionUnpacker(
                @NonNull CaptureConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_CAPTURE_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setSurfaceOccupancyPriority(int priority) {
            getMutableConfig().insertOption(OPTION_SURFACE_OCCUPANCY_PRIORITY, priority);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY)
        @Override
        @NonNull
        public Builder<T> setCameraSelector(@NonNull CameraSelector cameraSelector) {
            getMutableConfig().insertOption(OPTION_CAMERA_SELECTOR, cameraSelector);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setUseCaseEventCallback(
                @NonNull EventCallback useCaseEventCallback) {
            getMutableConfig().insertOption(OPTION_USE_CASE_EVENT_CALLBACK, useCaseEventCallback);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setAttachedUseCasesUpdateListener(
                @NonNull Consumer<Collection<UseCase>> attachedUseCasesUpdateListener) {
            getMutableConfig().insertOption(OPTION_ATTACHED_USE_CASES_UPDATE_LISTENER,
                    attachedUseCasesUpdateListener);
            return this;
        }
    }
}