// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;

public final class RootLaunch {
    public static String quote(String value) {
        if(value==null || value.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid shell argument");
        return "'"+value.replace("'","'\\''")+"'";
    }
    public static String command(String library,String home,String executable,String port) {
        if(port==null || !port.matches("/dev/tty(?:ACM|USB)[0-9]+"))throw new IllegalArgumentException("Invalid tty port");
        // Android installation paths may contain '='. Some env implementations
        // consume such a path as NAME=VALUE and then try to execute --incognito.
        // Export HOME in the shell; pass the executable directly to exec.
        return "cd "+quote(library)+" && export HOME="+quote(home)+" && export PMDESK_PIPE=1"+
            " && echo PMDESK_PID_$$ && exec "+quote(executable)+
            " --incognito --flush --port "+quote(port);
    }
}
