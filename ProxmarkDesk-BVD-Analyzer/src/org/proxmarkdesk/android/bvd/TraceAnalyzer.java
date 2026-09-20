package org.proxmarkdesk.android.bvd;

import java.util.ArrayList;
import java.util.List;

/**
 * BVD trace helper v1.1.
 * Stores decoded ISO14443A frames supplied by the PM3 layer.
 */
public class TraceAnalyzer {
    public static class Frame {
        public String direction;
        public String data;
        public Frame(String direction, String data) {
            this.direction = direction;
            this.data = data;
        }
    }

    private final List<Frame> frames = new ArrayList<>();

    public void addFrame(String direction, String hex) {
        frames.add(new Frame(direction, hex));
    }

    public List<Frame> getFrames() {
        return frames;
    }

    public String findFirstDifference(TraceAnalyzer other) {
        int count = Math.min(frames.size(), other.frames.size());
        for (int i = 0; i < count; i++) {
            Frame a = frames.get(i);
            Frame b = other.frames.get(i);
            if (!a.direction.equals(b.direction) || !a.data.equalsIgnoreCase(b.data)) {
                return "Difference at frame " + i + ": " + a.data + " / " + b.data;
            }
        }
        if (frames.size() != other.frames.size()) {
            return "Different frame count: " + frames.size() + " / " + other.frames.size();
        }
        return "No difference found";
    }
}
