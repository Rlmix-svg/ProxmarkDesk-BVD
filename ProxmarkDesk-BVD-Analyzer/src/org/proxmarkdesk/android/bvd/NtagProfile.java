package org.proxmarkdesk.android.bvd;

import java.util.HashMap;
import java.util.Map;

public class NtagProfile {
    public String uid;
    public String pwd;
    public String pack;
    public String signature;
    public final Map<Integer,String> pages = new HashMap<>();

    public String getPage(int page){ return pages.get(page); }
}
