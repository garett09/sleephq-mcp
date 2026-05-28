package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.oscar.OscarChannelIds;
import com.adriangarett.sleephqmcp.oscar.OscarSummaryEventCounts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public final class NightDataConflictAnalyzer {

    private static final String[] AVG_KEYS = AhiSummarySupport.avgKeys();
    private static final String[] OA_KEYS  = AhiSummarySupport.oaKeys();
    private static final String[] CA_KEYS  = AhiSummarySupport.caKeys();
    private static final String[] H_KEYS   = AhiSummarySupport.hKeys();
    private static final String[] PERCENTILE_95_KEYS = {"95", "p95", "ninety_five", "95th"};

    private NightDataConflictAnalyzer() {}

    public static ArrayNode analyze(JsonNode machineDateAttrs, OscarSession oscarSession) {
        ArrayNode conflicts = JsonApi.mapper().createArrayNode();
        if (machineDateAttrs == null || !machineDateAttrs.isObject() || oscarSession == null) {
            return conflicts;
        }

        // 1. AHI Comparison
        compareAhi(conflicts, machineDateAttrs, oscarSession);

        // 2. OA Comparison
        compareOa(conflicts, machineDateAttrs, oscarSession);

        // 3. CA Comparison
        compareCa(conflicts, machineDateAttrs, oscarSession);

        // 4. Hypopnea Comparison
        compareHypopnea(conflicts, machineDateAttrs, oscarSession);

        // 5. Pressure Comparison
        comparePressure(conflicts, machineDateAttrs, oscarSession);

        // 6. Leak Comparison
        compareLeak(conflicts, machineDateAttrs, oscarSession);

        return conflicts;
    }

    private static void compareAhi(ArrayNode conflicts, JsonNode attrs, OscarSession session) {
        Double shqAhi = readNumeric(attrs.path("ahi_summary"), AVG_KEYS);
        ChannelSummary oscarAhiSummary = session.channels().get(OscarChannelIds.CPAP_AHI);
        Double oscarAhi = (oscarAhiSummary != null) ? oscarAhiSummary.avg() : null;

        if (shqAhi != null && oscarAhi != null) {
            double diff = Math.abs(shqAhi - oscarAhi);
            if (diff > 0.1) {
                String severity = "info";
                if (diff > 2.0) {
                    severity = "critical";
                } else if (diff > 0.5) {
                    severity = "warn";
                }
                addConflict(conflicts, "ahi", shqAhi, oscarAhi, diff, severity,
                        String.format("AHI differs by %.2f/hr (SleepHQ: %.2f, OSCAR: %.2f). SleepHQ is used as primary.", diff, shqAhi, oscarAhi));
            }
        }
    }

    private static void compareOa(ArrayNode conflicts, JsonNode attrs, OscarSession session) {
        Double shqOa = readNumeric(attrs.path("ahi_summary"), OA_KEYS);
        double durationHours = session.durationSeconds() / 3600.0;
        if (shqOa != null && durationHours > 0.0) {
            Map<String, Integer> eventCounts = OscarSummaryEventCounts.fromSession(session);
            Integer count = eventCounts.get("obstructive");
            if (count != null) {
                double oscarOa = count / durationHours;
                double diff = Math.abs(shqOa - oscarOa);
                if (diff > 0.1) {
                    String severity = "info";
                    if (diff > 1.0) {
                        severity = "critical";
                    } else if (diff > 0.5) {
                        severity = "warn";
                    }
                    addConflict(conflicts, "obstructive_apnea_index", shqOa, oscarOa, diff, severity,
                            String.format("Obstructive Apnea Index differs by %.2f/hr (SleepHQ: %.2f, OSCAR: %.2f).", diff, shqOa, oscarOa));
                }
            }
        }
    }

    private static void compareCa(ArrayNode conflicts, JsonNode attrs, OscarSession session) {
        Double shqCa = readNumeric(attrs.path("ahi_summary"), CA_KEYS);
        double durationHours = session.durationSeconds() / 3600.0;
        if (shqCa != null && durationHours > 0.0) {
            Map<String, Integer> eventCounts = OscarSummaryEventCounts.fromSession(session);
            Integer count = eventCounts.get("clear_airway");
            if (count != null) {
                double oscarCa = count / durationHours;
                double diff = Math.abs(shqCa - oscarCa);
                if (diff > 0.1) {
                    String severity = "info";
                    if (diff > 1.0) {
                        severity = "critical";
                    } else if (diff > 0.3) {
                        severity = "warn";
                    }
                    addConflict(conflicts, "central_apnea_index", shqCa, oscarCa, diff, severity,
                            String.format("Central Apnea Index differs by %.2f/hr (SleepHQ: %.2f, OSCAR: %.2f).", diff, shqCa, oscarCa));
                }
            }
        }
    }

    private static void compareHypopnea(ArrayNode conflicts, JsonNode attrs, OscarSession session) {
        Double shqH = readNumeric(attrs.path("ahi_summary"), H_KEYS);
        double durationHours = session.durationSeconds() / 3600.0;
        if (shqH != null && durationHours > 0.0) {
            Map<String, Integer> eventCounts = OscarSummaryEventCounts.fromSession(session);
            Integer count = eventCounts.get("hypopnea");
            if (count != null) {
                double oscarH = count / durationHours;
                double diff = Math.abs(shqH - oscarH);
                if (diff > 0.1) {
                    String severity = "info";
                    if (diff > 1.0) {
                        severity = "critical";
                    } else if (diff > 0.5) {
                        severity = "warn";
                    }
                    addConflict(conflicts, "hypopnea_index", shqH, oscarH, diff, severity,
                            String.format("Hypopnea Index differs by %.2f/hr (SleepHQ: %.2f, OSCAR: %.2f).", diff, shqH, oscarH));
                }
            }
        }
    }

    private static void comparePressure(ArrayNode conflicts, JsonNode attrs, OscarSession session) {
        Double shqPres = readNumeric(attrs.path("pressure_summary"), AVG_KEYS);
        ChannelSummary oscarPresSummary = session.channels().get(OscarChannelIds.CPAP_Pressure);
        Double oscarPres = (oscarPresSummary != null) ? oscarPresSummary.avg() : null;

        if (shqPres != null && oscarPres != null) {
            double diff = Math.abs(shqPres - oscarPres);
            if (diff > 0.1) {
                String severity = "info";
                if (diff > 2.0) {
                    severity = "critical";
                } else if (diff > 1.0) {
                    severity = "warn";
                }
                addConflict(conflicts, "average_pressure", shqPres, oscarPres, diff, severity,
                        String.format("Average pressure differs by %.2f cmH2O (SleepHQ: %.2f, OSCAR: %.2f).", diff, shqPres, oscarPres));
            }
        }
    }

    private static void compareLeak(ArrayNode conflicts, JsonNode attrs, OscarSession session) {
        Double shqLeakAvg = readNumeric(attrs.path("leak_rate_summary"), AVG_KEYS);
        ChannelSummary oscarLeakSummary = session.channels().get(OscarChannelIds.CPAP_Leak);
        Double oscarLeakAvg = (oscarLeakSummary != null) ? oscarLeakSummary.avg() : null;

        if (shqLeakAvg != null && oscarLeakAvg != null) {
            double diff = Math.abs(shqLeakAvg - oscarLeakAvg);
            if (diff > 0.1) {
                String severity = "info";
                if (diff > 10.0) {
                    severity = "critical";
                } else if (diff > 5.0) {
                    severity = "warn";
                }
                addConflict(conflicts, "average_leak", shqLeakAvg, oscarLeakAvg, diff, severity,
                        String.format("Average leak differs by %.2f L/min (SleepHQ: %.2f, OSCAR: %.2f).", diff, shqLeakAvg, oscarLeakAvg));
            }
        }
    }

    private static void addConflict(ArrayNode conflicts, String metric, double shqVal, double oscarVal, double delta, String severity, String note) {
        ObjectNode c = conflicts.addObject();
        c.put("metric", metric);
        c.put("sleephq_value", Math.round(shqVal * 100.0) / 100.0);
        c.put("oscar_value", Math.round(oscarVal * 100.0) / 100.0);
        c.put("delta", Math.round(delta * 100.0) / 100.0);
        c.put("severity", severity);
        c.put("note", note);
    }

    private static Double readNumeric(JsonNode summary, String... keys) {
        if (summary == null || !summary.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode node = summary.path(key);
            if (node.isNumber()) {
                return node.asDouble();
            }
            if (node.isTextual()) {
                String text = node.asText().trim();
                if (!text.isEmpty()) {
                    try {
                        return Double.parseDouble(text);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return null;
    }
}
