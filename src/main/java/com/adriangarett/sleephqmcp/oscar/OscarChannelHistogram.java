package com.adriangarett.sleephqmcp.oscar;

import java.util.TreeMap;

public record OscarChannelHistogram(TreeMap<Integer, Long> buckets, double gainFactor) {}
