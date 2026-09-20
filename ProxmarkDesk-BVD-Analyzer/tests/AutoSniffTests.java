package org.proxmarkdesk.android;
import java.util.*;
public class AutoSniffTests {
 public static void main(String[] a)throws Exception {
  List<String> calls=new ArrayList<>();
  AutoSniff.Transport t=new AutoSniff.Transport(){public String command(String c,int timeout){calls.add(c);return "ok";}public void waitSeconds(int s){calls.add("wait:"+s);}};
  AutoSniff.run(t,30,1,"dumps/sniff.123");
  List<String> expected=Arrays.asList("hf 14a sniff","wait:30","hw break","wait:1","trace list -t 14a","wait:1","trace list -1 -t 14a -c --frame","wait:1","trace save -f dumps/sniff.123");
  if(!calls.equals(expected))throw new AssertionError(calls);
  calls.clear();try{AutoSniff.run(t,0,1,"dumps/sniff.123");throw new AssertionError();}catch(IllegalArgumentException ok){}if(!calls.isEmpty())throw new AssertionError("invalid duration sent command");
  calls.clear();try{AutoSniff.run(new AutoSniff.Transport(){public String command(String c,int timeout){calls.add(c);return "";}public void waitSeconds(int s)throws Exception{throw new java.util.concurrent.CancellationException();}},30,1,"dumps/sniff.124");throw new AssertionError();}catch(java.util.concurrent.CancellationException ok){}if(calls.size()!=1)throw new AssertionError("commands continued after stop");
  System.out.println("PASS: AutoSniff command order, timer, delays, invalid duration, cancellation");
 }
}
