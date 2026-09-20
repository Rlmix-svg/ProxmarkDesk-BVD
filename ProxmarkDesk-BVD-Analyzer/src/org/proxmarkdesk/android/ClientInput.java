// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;
import java.nio.charset.StandardCharsets;

public final class ClientInput {
    public static String error(String command) {
        if(command==null || command.trim().isEmpty()) return "Введите команду, например hw version";
        if(command.indexOf('\n')>=0 || command.indexOf('\r')>=0 || command.indexOf('\0')>=0) return "Команда должна занимать одну строку";
        if(command.getBytes(StandardCharsets.UTF_8).length>250) return "Команда превышает 250 байт UTF-8";
        return null;
    }
}
