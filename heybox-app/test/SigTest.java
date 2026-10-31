import com.autosign.xiaoheihe.XhhSig;

import java.util.Random;

public class SigTest {
    public static void main(String[] args) {
        String[] paths = {
                "/task/list_v2/",
                "/task/sign_v3/sign",
                "/task/sign_v3/get_sign_state",
                "/bbs/app/feeds",
                "/game/all_recommend/v2",
                "/bbs/app/link/game/comments",
                "/account/data_report_web/",
        };
        Random r = new Random(20261026);
        for (int i = 0; i < 200; i++) {
            String path = paths[r.nextInt(paths.length)];
            long t = 1800000000L + r.nextInt(100000000);
            StringBuilder n = new StringBuilder();
            String pool = "0123456789ABCDEF";
            for (int j = 0; j < 32; j++) {
                n.append(pool.charAt(r.nextInt(16)));
            }
            String hkey = XhhSig.makeWebHkey(path, t, n.toString());
            System.out.println(path + "|" + t + "|" + n + "|" + hkey);
        }
    }
}
