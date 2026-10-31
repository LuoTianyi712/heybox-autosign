package com.autosign.xiaoheihe;

import com.autosign.xiaoheihe.XhhSig;

import java.util.ArrayList;
import java.util.List;

public class DesktopTest {
    public static void main(String[] args) {
        System.out.println("== pkey parse test ==");
        String fakePkey = "MTIzNDU2Xzg3NjU0MzIxXzE5ODIzX3Rlc3Rf";
        System.out.println("heybox_id from fake pkey: [" + XhhSig.decodePkeyToUserId(fakePkey) + "]");

        System.out.println("== api pipeline test (fake cookie) ==");
        List<String> cookies = new ArrayList<String>();
        cookies.add("pkey=" + fakePkey + "; x_xhh_tokenid=FAKE123");
        SignWorker.RunResult r = SignWorker.runAll(cookies, false, true);
        for (String line : r.lines) {
            System.out.println("LINE: " + line);
        }
        System.out.println("allOk=" + r.allOk);

        System.out.println("== bad cookie test ==");
        List<String> bad = new ArrayList<String>();
        bad.add("pkey=notvalid;x_xhh_tokenid=x");
        SignWorker.RunResult r2 = SignWorker.runAll(bad, false, true);
        for (String line : r2.lines) {
            System.out.println("LINE: " + line);
        }
    }
}

