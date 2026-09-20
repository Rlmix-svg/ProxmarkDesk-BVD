package org.proxmarkdesk.android;

/** Serialized ISO14443-A capture; the transport must finish each command before returning. */
public final class AutoSniff {
    public interface Transport {
        String command(String command,int timeout) throws Exception;
        void waitSeconds(int seconds) throws Exception;
    }
    public static String run(Transport t,int duration,int delay,String filename)throws Exception {
        if(duration<1||duration>3600)throw new IllegalArgumentException("Длительность: 1–3600 секунд");
        if(delay<1||delay>10)throw new IllegalArgumentException("Пауза: 1–10 секунд");
        if(!filename.matches("dumps/sniff\\.[0-9]+"))throw new IllegalArgumentException("Неверное имя трассы");
        StringBuilder result=new StringBuilder();
        result.append(t.command("hf 14a sniff",30));
        t.waitSeconds(duration);
        result.append(t.command("hw break",30));
        t.waitSeconds(delay);
        result.append(t.command("trace list -t 14a",120));
        t.waitSeconds(delay);
        result.append(t.command("trace list -1 -t 14a -c --frame",120));
        t.waitSeconds(delay);
        result.append(t.command("trace save -f "+filename,60));
        return result.toString();
    }
}
