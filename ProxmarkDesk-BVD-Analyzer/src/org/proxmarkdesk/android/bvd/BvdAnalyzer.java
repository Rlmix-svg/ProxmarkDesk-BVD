package org.proxmarkdesk.android.bvd;

public class BvdAnalyzer {
    public static String analyze(NtagProfile p){
        StringBuilder r = new StringBuilder();
        r.append("UID: ").append(p.uid).append("\n");
        r.append("Page 16: ").append(p.getPage(16)).append("\n");
        r.append("Page 20: ").append(p.getPage(20)).append("\n");
        r.append("Page 24: ").append(p.getPage(24)).append("\n");
        r.append("Page 41: ").append(p.getPage(41)).append("\n");
        String p16=p.getPage(16);
        r.append("STATUS: requires comparison; page bytes do not establish access status\n");
        return r.toString();
    }
}
