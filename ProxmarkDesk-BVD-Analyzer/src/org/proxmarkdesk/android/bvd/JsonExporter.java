package org.proxmarkdesk.android.bvd;

public class JsonExporter {
    public static String export(NtagProfile p){
        return "{\"uid\":\""+p.uid+"\",\"pwd\":\""+p.pwd+"\"}";
    }
}
