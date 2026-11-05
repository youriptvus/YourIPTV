package com.youriptv.app.ui

import android.net.Uri
import android.util.SparseArray
import androidx.annotation.OptIn
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.ts.TsPayloadReader

/**
 * MPEG-TS extraction for live IPTV streams.
 *
 * Media3's FLAG_DETECT_ACCESS_UNITS is needed for H.264 streams without access unit delimiters (AUDs),
 * but on streams that do carry AUDs it cuts every following AUD/SEI/SPS/PPS NAL unit into its own sample,
 * so each picture reaches the decoder as ~4 inputs. Slower hardware decoders (e.g. MediaTek phones) then
 * cannot keep up with 50/60 fps and playback falls behind, drops frames and skips to keyframes.
 * Each H.264 stream therefore uses slice-based detection only when its first PES packet has no AUD.
 */
@OptIn(UnstableApi::class)
class LiveTsExtractorsFactory(private val tsFlags: Int) : ExtractorsFactory {
    private val base = DefaultExtractorsFactory().setTsExtractorFlags(tsFlags)
    private var subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var textTrackTranscodingEnabled = true

    override fun experimentalSetTextTrackTranscodingEnabled(enabled: Boolean): ExtractorsFactory = apply {
        textTrackTranscodingEnabled = enabled
        base.experimentalSetTextTrackTranscodingEnabled(enabled)
    }

    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): ExtractorsFactory = apply {
        subtitleParserFactory = factory
        base.setSubtitleParserFactory(factory)
    }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecFlags: Int): ExtractorsFactory = apply {
        base.experimentalSetCodecsToParseWithinGopSampleDependencies(codecFlags)
    }

    override fun setParseHagcMetadata(parseHagcMetadata: Boolean): ExtractorsFactory = apply {
        base.setParseHagcMetadata(parseHagcMetadata)
    }

    override fun createExtractors(): Array<Extractor> = replaceTs(base.createExtractors())

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        replaceTs(base.createExtractors(uri, responseHeaders))

    private fun replaceTs(extractors: Array<Extractor>): Array<Extractor> = Array(extractors.size) { i ->
        if (extractors[i] is TsExtractor) TsExtractor(
            TsExtractor.MODE_SINGLE_PMT,
            if (textTrackTranscodingEnabled) 0 else TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            subtitleParserFactory,
            TimestampAdjuster(0),
            AccessUnitAwarePayloadReaderFactory(tsFlags and DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS.inv()),
            TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES,
        ) else extractors[i]
    }
}

@OptIn(UnstableApi::class)
private class AccessUnitAwarePayloadReaderFactory(flags: Int) : TsPayloadReader.Factory {
    private val delimited = DefaultTsPayloadReaderFactory(flags)
    private val sliceDetected = DefaultTsPayloadReaderFactory(flags or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)

    override fun createInitialPayloadReaders(): SparseArray<TsPayloadReader> = delimited.createInitialPayloadReaders()

    override fun createPayloadReader(streamType: Int, esInfo: TsPayloadReader.EsInfo): TsPayloadReader? {
        val withAud = delimited.createPayloadReader(streamType, esInfo)
        if (streamType != TsExtractor.TS_STREAM_TYPE_H264 || withAud == null) return withAud
        val withoutAud = sliceDetected.createPayloadReader(streamType, esInfo) ?: return withAud
        return H264AccessUnitSelector(withAud, withoutAud)
    }
}

/** Feeds the H.264 PID to exactly one of two readers that share the same track outputs. */
@OptIn(UnstableApi::class)
private class H264AccessUnitSelector(
    private val withAud: TsPayloadReader,
    private val withoutAud: TsPayloadReader,
) : TsPayloadReader {
    private var chosen: TsPayloadReader? = null

    override fun init(timestampAdjuster: TimestampAdjuster, output: ExtractorOutput, idGenerator: TsPayloadReader.TrackIdGenerator) {
        val tracks = ArrayList<TrackOutput>()
        withAud.init(timestampAdjuster, SharedTracks(output, tracks, record = true), idGenerator)
        withoutAud.init(timestampAdjuster, SharedTracks(output, tracks, record = false), idGenerator)
    }

    override fun seek() {
        withAud.seek()
        withoutAud.seek()
    }

    override fun consume(data: ParsableByteArray, flags: Int) {
        val reader = chosen ?: run {
            // Data before the first PES start is discarded by either reader.
            if (flags and TsPayloadReader.FLAG_PAYLOAD_UNIT_START_INDICATOR == 0) return
            val aud = H264Aud.contains(data.data, data.position, data.limit())
            (if (aud) withAud else withoutAud).also { chosen = it }
        }
        reader.consume(data, flags)
    }

    /** The second reader gets the same TrackOutputs, in creation order, as the first. */
    private class SharedTracks(
        private val output: ExtractorOutput,
        private val tracks: MutableList<TrackOutput>,
        private val record: Boolean,
        private var next: Int = 0,
    ) : ExtractorOutput {
        override fun track(id: Int, type: Int): TrackOutput =
            if (record) output.track(id, type).also { tracks.add(it) }
            else tracks.getOrNull(next++) ?: output.track(id, type)

        override fun endTracks() = output.endTracks()
        override fun seekMap(seekMap: SeekMap) = output.seekMap(seekMap)
    }
}

object H264Aud {
    /** True when [data] in [from, to) contains an H.264 access unit delimiter start code (00 00 01 09). */
    fun contains(data: ByteArray, from: Int, to: Int): Boolean {
        var i = maxOf(0, from)
        val end = minOf(to, data.size) - 3
        while (i < end) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 1 && (data[i + 3].toInt() and 0x1F) == 9) return true
            i++
        }
        return false
    }
}
