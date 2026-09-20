package org.proxmarkdesk.android.capability;
import java.util.*;
public class ActionRegistryTests {
    static int n;
    static void check(boolean v,String m){ if(!v) throw new AssertionError(m); n++; }
    static void expectFail(Runnable r,String contains){
        boolean ok=false;
        try { r.run(); } catch(IllegalArgumentException e){ ok=e.getMessage()!=null && e.getMessage().contains(contains); }
        check(ok,"expected IllegalArgumentException containing: "+contains);
    }
    public static void main(String[] args){
        ActionDef hf=ActionRegistry.findById("hf.search");
        check(hf!=null,"hf.search exists");
        check(ActionRegistry.buildCommand(hf, Collections.emptyMap()).equals("hf search"),"hf.search command");
        check(ActionRegistry.findById("does.not.exist")==null,"unknown id returns null");
        Set<String> ids=new HashSet<>(); for(ActionDef a:ActionRegistry.all()) check(ids.add(a.id),"unique action id: "+a.id);
        check(ActionRegistry.buildCommand(ActionRegistry.findById("tag.auto"),Collections.emptyMap()).equals("auto"),"auto action");
        Map<String,String> mfu=new HashMap<>();mfu.put("blk","4");mfu.put("keyA","A2819B7D");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("mfu.rdbl.key"),mfu).equals("hf mfu rdbl -b 4 -k A2819B7D"),"mfu protected read");
        Map<String,String> iso=new HashMap<>();iso.put("path","dumps/test");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("iso15693.dump"),iso).equals("hf 15 dump -f dumps/test"),"iso15693 dump");


        check(ActionRegistry.buildCommand(ActionRegistry.findById("hw.tune"),Collections.emptyMap()).equals("hw tune"),"hw tune action");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("lua.envcheck"),Collections.emptyMap()).equals("script run pmdesk_envcheck"),"lua envcheck action");
        Map<String,String> dump=new HashMap<>(); dump.put("size","1k"); dump.put("path","dumps/test-classic");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("classic.dump.file"),dump).equals("hf mf dump --1k -k classic-keys.bin -f dumps/test-classic"),"classic full dump action");
        Map<String,String> save=new HashMap<>(); save.put("path","dumps/signal-1");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("data.save"),save).equals("data save -f dumps/signal-1"),"data save action");
        check(ActionRegistry.findById("sniff.hf14b")!=null,"sniff hf14b action exists");
        check(ActionRegistry.findById("trace.hf14a")!=null,"trace hf14a action exists");

        CardCapability classicCap=new CardCapability.Builder().protocol(1).subtype("Classic 1K").verified(true).build();
        List<ActionDef> operationActions=ActionRegistry.forOperationScreen(classicCap);
        Set<String> operationIds=new HashSet<>(); for(ActionDef a:operationActions) operationIds.add(a.id);
        check(operationIds.contains("classic.autopwn"),"operations include Classic autopwn");
        check(operationIds.contains("classic.dump"),"operations include Classic dump");
        check(!operationIds.contains("hf.sniff.14a"),"operations exclude sniff");
        check(!operationIds.contains("hf.search"),"operations exclude generic search");
        check(!operationIds.contains("hw.version"),"operations exclude tools");

        List<ActionDef> cardClassic=ActionRegistry.forCardMenu(classicCap);
        Set<String> cardClassicIds=new HashSet<>(); for(ActionDef a:cardClassic) cardClassicIds.add(a.id);
        check(cardClassicIds.contains("classic.chk"),"Classic card menu includes fast key check");
        check(cardClassicIds.contains("classic.chk.file"),"Classic card menu includes dictionary file check");
        check(!cardClassicIds.contains("classic.sim"),"live card menu excludes dump-based emulation");

        CardCapability plainMfu=new CardCapability.Builder().protocol(2).subtype("MIFARE Ultralight").verified(true).build();
        Set<String> plainMfuIds=new HashSet<>(); for(ActionDef a:ActionRegistry.forCardMenu(plainMfu)) plainMfuIds.add(a.id);
        check(plainMfuIds.contains("mfu.info"),"plain Ultralight includes info");
        check(!plainMfuIds.contains("mfu.pwdauth"),"plain Ultralight excludes password auth");
        check(!plainMfuIds.contains("mfu.dump.key"),"plain Ultralight excludes keyed dump");

        CardCapability ntag=new CardCapability.Builder().protocol(2).subtype("NTAG213").authType(CardCapability.AuthType.PWD).verified(true).build();
        Set<String> ntagIds=new HashSet<>(); for(ActionDef a:ActionRegistry.forCardMenu(ntag)) ntagIds.add(a.id);
        check(ntagIds.contains("mfu.pwdauth"),"NTAG menu includes PWD auth");
        check(ntagIds.contains("mfu.dump.key"),"NTAG menu includes password dump");

        check(ActionRegistry.forCardMenu(new CardCapability.Builder().protocol(10).subtype("iCLASS").verified(true).build()).stream().anyMatch(a->a.id.equals("iclass.info")),"iCLASS menu has safe info action");
        Map<String,String> dict=new HashMap<>(); dict.put("size","1k"); dict.put("dict","/data/user/0/org.proxmarkdesk.android/files/library/dictionaries/classic.dic");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("classic.chk"), Collections.singletonMap("size","1k")).equals("hf mf fchk --1k"),"Classic quick check uses fchk");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("classic.chk.file"),dict).equals("hf mf fchk --1k -f /data/user/0/org.proxmarkdesk.android/files/library/dictionaries/classic.dic"),"Classic dictionary check command");
        Map<String,String> df=new HashMap<>(); df.put("dict","/tmp/desfire.dic");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("desfire.chk.file"),df).equals("hf mfdes chk -f /tmp/desfire.dic"),"DESFire dictionary command");
        Map<String,String> t55=new HashMap<>(); t55.put("dict","/tmp/t55xx.dic");
        check(ActionRegistry.buildCommand(ActionRegistry.findById("t55xx.chk.file"),t55).equals("lf t55xx chk -f /tmp/t55xx.dic"),"T55xx dictionary command");


        ActionDef rdbl=new ActionDef.Builder().id("test").commandTemplate("hf mf rdbl --blk {blk} -a -k {keyA}").build();
        expectFail(() -> ActionRegistry.buildCommand(rdbl, Collections.singletonMap("blk","4")), "keyA");
        Map<String,String> p=new HashMap<>(); p.put("blk","4"); p.put("keyA","FFFFFFFFFFFF");
        check(ActionRegistry.buildCommand(rdbl,p).equals("hf mf rdbl --blk 4 -a -k FFFFFFFFFFFF"),"resolved command");
        System.out.println("PASS action registry checks: "+n);
    }
}
