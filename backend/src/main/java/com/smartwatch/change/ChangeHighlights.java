package com.smartwatch.change;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Count sentences built from detected changes. The wording states counts,
 * not a forecast.
 */
public final class ChangeHighlights {

    private ChangeHighlights() {
    }

    public static List<String> from(List<DetectedChange> changes) {
        if (changes.isEmpty()) {
            return List.of();
        }
        Map<ChangeType, Integer> counts = new EnumMap<>(ChangeType.class);
        for (DetectedChange change : changes) {
            counts.merge(change.type(), 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>();
        int instruments = (int) changes.stream().map(DetectedChange::instrumentId).distinct().count();
        lines.add(stocks(instruments) + " changed since the last check.");
        add(lines, counts.getOrDefault(ChangeType.PRICE_MOVE, 0),
                "had a price movement.", "had price movements.");
        add(lines, counts.getOrDefault(ChangeType.VOLUME_SPIKE, 0),
                "had a volume spike.", "had volume spikes.");
        add(lines, counts.getOrDefault(ChangeType.NEW_DAY_HIGH, 0),
                "reached a new observed day high.", "reached new observed day highs.");
        add(lines, counts.getOrDefault(ChangeType.NEW_DAY_LOW, 0),
                "reached a new observed day low.", "reached new observed day lows.");
        add(lines, counts.getOrDefault(ChangeType.GAP_UP, 0),
                "gapped up from the previous close.", "gapped up from the previous close.");
        add(lines, counts.getOrDefault(ChangeType.GAP_DOWN, 0),
                "gapped down from the previous close.", "gapped down from the previous close.");
        add(lines, counts.getOrDefault(ChangeType.LARGE_INTRADAY_MOVE, 0),
                "had a large intraday move.", "had large intraday moves.");
        add(lines, counts.getOrDefault(ChangeType.WATCHLIST_ADDED, 0),
                "was added to this watchlist.", "were added to this watchlist.");
        add(lines, counts.getOrDefault(ChangeType.WATCHLIST_REMOVED, 0),
                "was removed from this watchlist.", "were removed from this watchlist.");
        return List.copyOf(lines);
    }

    public static int count(List<DetectedChange> changes, ChangeType type) {
        return (int) changes.stream().filter(change -> change.type() == type).count();
    }

    private static void add(List<String> lines, int count, String singular, String plural) {
        if (count <= 0) {
            return;
        }
        lines.add(stocks(count) + " " + (count == 1 ? singular : plural));
    }

    private static String stocks(int count) {
        return count + (count == 1 ? " stock" : " stocks");
    }
}
