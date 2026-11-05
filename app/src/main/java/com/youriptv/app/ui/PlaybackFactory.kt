package com.youriptv.app.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.youriptv.app.YourIptvApp
import com.youriptv.app.data.Http
import com.youriptv.app.data.ItemKind
import com.youriptv.app.data.MediaItem
import com.youriptv.app.data.XtreamUrls

@OptIn(UnstableApi::class)
object PlaybackFactory {
    data class Created(val player: ExoPlayer, val userAgent: String)

    fun create(context: Context, item: MediaItem?, audioDelay: AudioDelayProcessor): Created {
        val app = YourIptvApp.instance
        val settings = app.settings
        val playlist = item?.let { app.repo.playlist(it.playlistId) }
        val ua = playlist?.userAgent.orEmpty().ifBlank { settings.globalUserAgent }.ifBlank { Http.DEFAULT_USER_AGENT }
        val appContext = context.applicationContext
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val extractors = LiveTsExtractorsFactory(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        val start = settings.bufferMs.coerceIn(500, 30_000)
        val minBuffer = maxOf(15_000, start * 2)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxOf(50_000, minBuffer), start, minOf(start * 2, minBuffer))
            .build()
        audioDelay.delayMs = settings.audioDelayMs
        val renderers = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(audioDelay))
                    .build()
        }.setEnableDecoderFallback(true)
        if (settings.preferSoftwareDecoding) {
            renderers.setMediaCodecSelector(MediaCodecSelector { mime, secure, tunneling ->
                MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { if (it.softwareOnly) 0 else 1 }
            })
        }
        val player = ExoPlayer.Builder(appContext, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, http), extractors))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(settings.skipSeconds * 1000L)
            .setSeekForwardIncrementMs(settings.skipSeconds * 1000L)
            .build()
        return Created(player, ua)
    }

    fun streamUrl(item: MediaItem): String =
        if (item.kind == ItemKind.LIVE) XtreamUrls.applyLiveFormat(item.url, YourIptvApp.instance.settings.liveFormat) else item.url

    fun mediaItem(item: MediaItem): androidx.media3.common.MediaItem {
        val url = streamUrl(item)
        val builder = androidx.media3.common.MediaItem.Builder().setUri(url).setMediaId(item.url)
        if (url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        return builder.build()
    }

    fun restartLive(player: ExoPlayer, item: MediaItem) {
        require(item.kind == ItemKind.LIVE && !item.isCatchup)
        player.stop()
        player.setMediaItem(mediaItem(item))
        player.prepare()
        player.playWhenReady = true
    }
}
