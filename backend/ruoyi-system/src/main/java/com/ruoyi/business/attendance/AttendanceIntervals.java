package com.ruoyi.business.attendance;

import java.util.*;

/** Epoch-second interval algebra; overlapping approvals are counted once. */
public final class AttendanceIntervals
{
    private AttendanceIntervals() { }
    public static List<long[]> union(List<long[]> values)
    {
        List<long[]> sorted = new ArrayList<>(), result = new ArrayList<>();
        for (long[] p : values) { if (p == null || p.length != 2 || p[1] <= p[0]) throw new IllegalArgumentException("Invalid interval"); sorted.add(p.clone()); }
        sorted.sort(Comparator.comparingLong(a -> a[0]));
        for (long[] p : sorted)
        {
            if (result.isEmpty() || result.get(result.size() - 1)[1] < p[0]) result.add(p);
            else result.get(result.size() - 1)[1] = Math.max(result.get(result.size() - 1)[1], p[1]);
        }
        return result;
    }
    public static List<long[]> subtract(List<long[]> work, List<long[]> unavailable)
    {
        List<long[]> result = new ArrayList<>();
        List<long[]> cuts = union(unavailable);
        for (long[] p : union(work))
        {
            long cursor = p[0];
            for (long[] cut : cuts)
            {
                if (cut[1] <= cursor || cut[0] >= p[1]) continue;
                if (cut[0] > cursor) result.add(new long[] {cursor, Math.min(cut[0], p[1])});
                cursor = Math.max(cursor, cut[1]); if (cursor >= p[1]) break;
            }
            if (cursor < p[1]) result.add(new long[] {cursor, p[1]});
        }
        return result;
    }
    public static long minutes(List<long[]> values)
    { long seconds = 0; for (long[] p : union(values)) seconds += p[1] - p[0]; return seconds / 60; }
}
