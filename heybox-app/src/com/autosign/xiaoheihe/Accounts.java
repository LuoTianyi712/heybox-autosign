package com.autosign.xiaoheihe;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Accounts {

    static final class Row {
        final String id;
        final String nick;

        Row(String id, String nick) {
            this.id = id;
            this.nick = nick;
        }

        boolean hasNick() {
            return nick != null && !nick.isEmpty();
        }
    }

    private static final Pattern P_ACCOUNT = Pattern.compile("账号: (.+?) \\(ID:(\\d+)\\)");

    private Accounts() {
    }

    static List<String> cookieLines(Context c) {
        List<String> out = new ArrayList<String>();
        for (String line : Store.cookieText(c).split("\n")) {
            String item = line.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    static List<Row> read(Context c) {
        return readText(c, Store.cookieText(c));
    }

    static List<Row> readText(Context c, String text) {
        Map<String, String> nicks = new LinkedHashMap<String, String>();
        Matcher m = P_ACCOUNT.matcher(Store.lastResult(c));
        while (m.find()) {
            nicks.put(m.group(2), m.group(1));
        }
        List<Row> rows = new ArrayList<Row>();
        for (String line : text.split("\n")) {
            String item = line.trim();
            if (item.isEmpty()) {
                continue;
            }
            String id = cookieId(item);
            if (id.isEmpty()) {
                continue;
            }
            boolean dup = false;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).id.equals(id)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                rows.add(new Row(id, nicks.get(id)));
            }
        }
        return rows;
    }

    static String cookieId(String line) {
        String id = "";
        try {
            id = XhhSig.decodePkeyToUserId(HeyboxClient.parseCookie(line).get("pkey"));
        } catch (Exception ignored) {
        }
        if (id == null || id.isEmpty() || "0".equals(id)) {
            id = HeyboxClient.parseCookie(line).get("heybox_id");
        }
        return id == null || "0".equals(id) ? "" : id;
    }
}
