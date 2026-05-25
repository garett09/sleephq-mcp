package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses EDF (European Data Format) binary files into {@link WaveformResult}.
 * The returned result has {@code filename=null} — the caller must set it from the metadata source.
 */
public final class EdfParser {

    private EdfParser() {}

    public static WaveformResult parse(byte[] edf, int startSeconds, int maxSeconds) {
        EdfHeader header = EdfBinarySupport.readHeader(edf);
        int ns = header.signalCount();
        String[] labels = header.labels();
        int[] samplesPerRec = header.samplesPerRecord();
        double recDuration = header.recordDurationSeconds();
        int nRecords = header.nRecords();
        LocalDateTime startDatetime = header.startDatetime();

        int skipRecords = (int) Math.max(0, Math.floor((double) startSeconds / recDuration));
        int maxRecords = (int) Math.ceil((double) maxSeconds / recDuration);
        int availableRecords = nRecords < 0 ? Integer.MAX_VALUE : Math.max(0, nRecords - skipRecords);
        int recordsToRead = Math.min(availableRecords, maxRecords);

        if (skipRecords > 0) {
            double skipSeconds = skipRecords * recDuration;
            startDatetime = startDatetime.plusNanos((long) (skipSeconds * 1_000_000_000L));
        }

        int recordSizeBytes = EdfBinarySupport.recordSizeBytes(header);
        double[] physMins = EdfBinarySupport.parseDoubleBlock(edf, 256 + ns * 104, ns, 8);
        double[] physMaxs = EdfBinarySupport.parseDoubleBlock(edf, 256 + ns * 112, ns, 8);
        int[] digMins = EdfBinarySupport.parseIntBlock(edf, 256 + ns * 120, ns, 8);
        int[] digMaxs = EdfBinarySupport.parseIntBlock(edf, 256 + ns * 128, ns, 8);
        String[] units = EdfBinarySupport.readAsciiBlock(edf, 256 + ns * 96, ns, 8);

        List<List<Double>> channelSamples = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            channelSamples.add(new ArrayList<>(samplesPerRec[i] * recordsToRead));
        }

        int startOffset = header.headerBytes() + skipRecords * recordSizeBytes;
        for (int rec = 0; rec < recordsToRead; rec++) {
            int recStart = startOffset + rec * recordSizeBytes;
            if (recStart + recordSizeBytes > edf.length) {
                break;
            }
            int pos = recStart;
            for (int i = 0; i < ns; i++) {
                int nSamples = samplesPerRec[i];
                boolean skip = EdfBinarySupport.ANNOTATIONS_LABEL.equals(labels[i]) || "Crc16".equals(labels[i]);
                for (int s = 0; s < nSamples; s++) {
                    short raw = (short) ((edf[pos] & 0xFF) | ((edf[pos + 1] & 0xFF) << 8));
                    pos += 2;
                    if (!skip) {
                        double physical = EdfBinarySupport.scale(raw, digMins[i], digMaxs[i], physMins[i], physMaxs[i]);
                        channelSamples.get(i).add(EdfBinarySupport.round4(physical));
                    }
                }
            }
        }

        List<WaveformChannel> channels = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            if (EdfBinarySupport.ANNOTATIONS_LABEL.equals(labels[i])) {
                continue;
            }
            if ("Crc16".equals(labels[i])) {
                continue;
            }
            if (samplesPerRec[i] == 0) {
                continue;
            }
            double sampleRate = samplesPerRec[i] / recDuration;
            int maxSamples = (int) Math.floor(maxSeconds * sampleRate);
            List<Double> samples = channelSamples.get(i);
            if (samples.size() > maxSamples) {
                samples = new ArrayList<>(samples.subList(0, maxSamples));
            }
            channels.add(new WaveformChannel(labels[i], sampleRate, units[i], samples));
        }

        double durationSeconds = nRecords >= 0 ? nRecords * recDuration : (skipRecords + recordsToRead) * recDuration;
        return new WaveformResult(null, startDatetime.toString(), durationSeconds, channels);
    }
}
