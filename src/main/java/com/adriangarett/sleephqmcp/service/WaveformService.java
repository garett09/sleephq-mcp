package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.ApneaEvent;
import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import com.adriangarett.sleephqmcp.support.BinaryDownloadSupport;
import com.adriangarett.sleephqmcp.support.CpapClockAlignment;
import com.adriangarett.sleephqmcp.support.EdfParser;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.TeamFileResolver;
import com.adriangarett.sleephqmcp.support.WaveformDownsampler;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

@Service
public class WaveformService {

    private final SleepHqClient sleepHqClient;
    private final RestClient s3RestClient;
    private final ClinicalContextProperties clinical;
    private final MachineDateTimeOffsetLoader machineDateTimeOffsetLoader;

    public WaveformService(SleepHqClient sleepHqClient,
                           @Qualifier("s3RestClient") RestClient s3RestClient,
                           ClinicalContextProperties clinical,
                           MachineDateTimeOffsetLoader machineDateTimeOffsetLoader) {
        this.sleepHqClient = sleepHqClient;
        this.s3RestClient = s3RestClient;
        this.clinical = clinical;
        this.machineDateTimeOffsetLoader = machineDateTimeOffsetLoader;
    }

    public String getWaveform(String fileId, int startSeconds, int maxSeconds) {
        return getWaveform(fileId, startSeconds, maxSeconds, null);
    }

    public String getWaveform(String fileId, int startSeconds, int maxSeconds, Integer cpapClockAdjustSeconds) {
        return getWaveform(fileId, startSeconds, maxSeconds, cpapClockAdjustSeconds, OptionalInt.empty());
    }

    public String getWaveformByDate(String teamId, String date, int startSeconds, int maxSeconds) {
        return getWaveformByDate(teamId, date, startSeconds, maxSeconds, null);
    }

    public String getWaveformByDate(String teamId, String date, int startSeconds, int maxSeconds,
                                    Integer cpapClockAdjustSeconds) {
        String resolvedTeamId = teamId != null && !teamId.isBlank() ? teamId : clinical.defaultTeamId();
        if (resolvedTeamId == null || resolvedTeamId.isBlank()) {
            throw new IllegalArgumentException("Required teamId is missing and no default SLEEPHQ_TEAM_ID is configured");
        }
        String fileId = TeamFileResolver.resolveByDate(sleepHqClient, resolvedTeamId, date, "brp.edf");
        OptionalInt machineDateOffset = machineDateTimeOffsetLoader.loadForCpapDate(date, null);
        return getWaveform(fileId, startSeconds, maxSeconds, cpapClockAdjustSeconds, machineDateOffset);
    }

    public String getWaveform(String fileId, int startSeconds, int maxSeconds, Integer cpapClockAdjustSeconds,
                              OptionalInt machineDateOffset) {
        String fileJson = sleepHqClient.getImportFile(fileId);
        JsonNode attrs = JsonApi.attributes(JsonApi.parse(fileJson));

        String downloadUrl = attrs.path("download_url").asText(null);
        String filename = attrs.path("name").asText(null);

        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("download_url missing for file " + fileId);
        }
        if (!downloadUrl.startsWith("https://")) {
            throw new IllegalArgumentException("download_url is not HTTPS for file " + fileId);
        }

        URI uri = URI.create(downloadUrl);
        byte[] edfBytes = BinaryDownloadSupport.download(s3RestClient, uri, fileId);

        WaveformResult parsed = EdfParser.parse(edfBytes, startSeconds, maxSeconds);
        WaveformResult result = WaveformDownsampler.compact(new WaveformResult(
                filename != null ? filename : "",
                parsed.startDatetime(),
                parsed.durationSeconds(),
                parsed.channels()
        ));

        CpapClockAlignment.CpapClockAdjustResolution resolution =
                CpapClockAlignment.resolveAdjust(clinical, cpapClockAdjustSeconds, machineDateOffset);
        return CpapClockAlignment.serializeWithAlignment(
                CpapClockAlignment.alignWaveform(result, resolution.adjustSeconds()), resolution);
    }

    public String scanApneaEvents(String fileId, String teamId, String date, Double threshold, Integer minDurationSeconds) {
        return scanApneaEvents(fileId, teamId, date, threshold, minDurationSeconds, null);
    }

    public String scanApneaEvents(String fileId, String teamId, String date, Double threshold, Integer minDurationSeconds,
                                 Integer cpapClockAdjustSeconds) {
        String targetFileId = fileId;
        if (targetFileId == null || targetFileId.isBlank()) {
            if (date == null || date.isBlank()) {
                throw new IllegalArgumentException("Either fileId or date must be provided");
            }
            String resolvedTeamId = teamId != null && !teamId.isBlank() ? teamId : clinical.defaultTeamId();
            if (resolvedTeamId == null || resolvedTeamId.isBlank()) {
                throw new IllegalArgumentException("Required teamId is missing and no default SLEEPHQ_TEAM_ID is configured");
            }
            targetFileId = TeamFileResolver.resolveByDate(sleepHqClient, resolvedTeamId, date, "brp.edf");
        }

        double hypopneaLimit = threshold != null ? threshold : 0.15;
        int minDuration = minDurationSeconds != null ? minDurationSeconds : 10;

        String fileJson = sleepHqClient.getImportFile(targetFileId);
        JsonNode attrs = JsonApi.attributes(JsonApi.parse(fileJson));
        String downloadUrl = attrs.path("download_url").asText(null);
        String filename = attrs.path("name").asText("");

        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("download_url missing for file " + targetFileId);
        }

        URI uri = URI.create(downloadUrl);
        byte[] edfBytes = BinaryDownloadSupport.download(s3RestClient, uri, targetFileId);

        int twelveHours = 12 * 3600;
        WaveformResult fullResult = EdfParser.parseFlowChannel(edfBytes, 0, twelveHours);

        WaveformChannel flowChannel = fullResult.channels().getFirst();
        if (flowChannel == null) {
            throw new IllegalArgumentException("No flow respiration channel found in file " + filename);
        }

        double Fs = flowChannel.sampleRate();
        List<Double> samples = flowChannel.samples();
        int totalSamples = samples.size();

        double apneaLimit = 0.04;
        int minSamples = (int) Math.round(minDuration * Fs);

        int windowSize = (int) Math.round(4.0 * Fs);
        if (windowSize <= 0) {
            windowSize = 1;
        }

        double[] envelope = new double[totalSamples];
        double sum = 0.0;
        for (int i = 0; i < totalSamples; i++) {
            sum += Math.abs(samples.get(i));
            if (i >= windowSize) {
                sum -= Math.abs(samples.get(i - windowSize));
            }
            int denom = Math.min(i + 1, windowSize);
            envelope[i] = sum / denom;
        }

        List<ApneaEvent> events = new ArrayList<>();
        boolean inEvent = false;
        int eventStartIdx = -1;

        for (int i = 0; i < totalSamples; i++) {
            boolean belowLimit = envelope[i] < hypopneaLimit;
            if (belowLimit && !inEvent) {
                inEvent = true;
                eventStartIdx = i;
            } else if (!belowLimit && inEvent) {
                inEvent = false;
                int durationSamples = i - eventStartIdx;
                if (durationSamples >= minSamples) {
                    double startSec = eventStartIdx / Fs;
                    double durationSec = durationSamples / Fs;
                    String classification = classifyEvent(samples, eventStartIdx, i, Fs, apneaLimit);
                    events.add(createEvent(fullResult.startDatetime(), startSec, durationSec, classification));
                }
            }
        }

        if (inEvent) {
            int durationSamples = totalSamples - eventStartIdx;
            if (durationSamples >= minSamples) {
                double startSec = eventStartIdx / Fs;
                double durationSec = durationSamples / Fs;
                String classification = classifyEvent(samples, eventStartIdx, totalSamples, Fs, apneaLimit);
                events.add(createEvent(fullResult.startDatetime(), startSec, durationSec, classification));
            }
        }

        ApneaScanResult scanResult = new ApneaScanResult(
                filename,
                fullResult.startDatetime(),
                fullResult.durationSeconds(),
                flowChannel.label(),
                hypopneaLimit,
                events
        );

        OptionalInt machineDateOffset = resolveMachineDateOffsetForScan(date);
        CpapClockAlignment.CpapClockAdjustResolution resolution =
                CpapClockAlignment.resolveAdjust(clinical, cpapClockAdjustSeconds, machineDateOffset);
        return CpapClockAlignment.serializeWithAlignment(
                CpapClockAlignment.alignApneaScan(scanResult, resolution.adjustSeconds()), resolution);
    }

    private OptionalInt resolveMachineDateOffsetForScan(String calendarDate) {
        if (calendarDate == null || calendarDate.isBlank()) {
            return OptionalInt.empty();
        }
        return machineDateTimeOffsetLoader.loadForCpapDate(calendarDate, null);
    }

    private String classifyEvent(List<Double> samples, int startIdx, int endIdx, double Fs, double apneaLimit) {
        int N = endIdx - startIdx;
        double sumAbs = 0.0;
        for (int i = startIdx; i < endIdx; i++) {
            sumAbs += Math.abs(samples.get(i));
        }
        double avgEnvelope = sumAbs / N;

        if (avgEnvelope >= apneaLimit) {
            return "HYPOPNEA";
        }

        int cropStart = startIdx + N / 4;
        int cropEnd = endIdx - N / 4;
        int cropN = cropEnd - cropStart;

        if (cropN < 25) {
            cropStart = startIdx;
            cropEnd = endIdx;
            cropN = N;
        }

        if (cropN < 10) {
            return "APNEA_OBSTRUCTIVE";
        }

        double cosSum = 0.0;
        double sinSum = 0.0;
        for (int i = cropStart; i < cropEnd; i++) {
            double angle = 2.0 * Math.PI * 4.0 * (i - cropStart) / Fs;
            cosSum += samples.get(i) * Math.cos(angle);
            sinSum += samples.get(i) * Math.sin(angle);
        }
        double amp4Hz = Math.sqrt(Math.pow(cosSum * 2.0 / cropN, 2) + Math.pow(sinSum * 2.0 / cropN, 2));

        if (amp4Hz >= 0.003) {
            return "APNEA_CENTRAL";
        }
        return "APNEA_OBSTRUCTIVE";
    }

    private ApneaEvent createEvent(String startDatetimeStr, double startSec, double durationSec, String classification) {
        LocalDateTime baseTime = LocalDateTime.parse(startDatetimeStr);
        LocalDateTime eventTime = baseTime.plusNanos((long) (startSec * 1_000_000_000L));

        long hr = (long) (startSec / 3600);
        long min = (long) ((startSec % 3600) / 60);
        long sec = (long) (startSec % 60);
        String offsetStr = String.format("%02d:%02d:%02d", hr, min, sec);

        return new ApneaEvent(
                offsetStr,
                startSec,
                durationSec,
                eventTime.toString(),
                classification
        );
    }
}
