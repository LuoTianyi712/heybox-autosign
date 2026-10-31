package com.autosign.xiaoheihe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SignWorker {

    static final String OK = "ok";

    static class Task {
        String groupTitle = "";
        String title = "";
        String state = "";
        String stateDesc = "";
        String taskId = "";
        String type = "";
        String reportType = "";
        String maxjia = "";
        String raw = "";
        String awardText = "";
    }

    static class Snapshot {
        String nickname = "";
        String coin = "";
        String battery = "";
        String level = "";
        String exp = "";
        String maxExp = "";
        final List<Task> tasks = new ArrayList<Task>();
    }

    static class ShareTarget {
        String source = "";
        String linkId = "";
        String hSrc = "";
        String appId = "";
        String detail = "";
    }

    static class RunResult {
        boolean allOk = true;
        final List<String> lines = new ArrayList<String>();
    }

    private SignWorker() {
    }

    static Snapshot fetchSnapshot(HeyboxClient client) throws Exception {
        JSONObject payload = client.getJson("/task/list_v2/", null);
        String status = payload.optString("status");
        String msg = payload.optString("msg");
        if ("login".equals(status) || msg.contains("登录") || msg.contains("重新")) {
            throw new IllegalStateException("登录校验未通过 status=" + status + " msg=" + msg
                    + " 服务端返回: " + snippet(payload.toString()));
        }
        if (!OK.equals(status)) {
            throw new IllegalStateException("拉取任务列表失败: " + msg);
        }
        return parseSnapshot(payload);
    }

    static String snippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    static Snapshot parseSnapshot(JSONObject payload) {
        Snapshot snap = new Snapshot();
        JSONObject result = payload.optJSONObject("result");
        if (result == null) {
            return snap;
        }
        JSONObject user = result.optJSONObject("user");
        if (user != null) {
            snap.nickname = user.optString("username");
            snap.battery = user.optString("battery");
            JSONObject level = user.optJSONObject("level_info");
            if (level != null) {
                snap.coin = level.optString("coin");
                snap.level = level.optString("level");
                snap.exp = level.optString("exp");
                snap.maxExp = level.optString("max_exp");
            }
        }
        JSONArray groups = result.optJSONArray("task_list");
        if (groups != null) {
            for (int g = 0; g < groups.length(); g++) {
                JSONObject group = groups.optJSONObject(g);
                if (group == null) {
                    continue;
                }
                String groupTitle = group.optString("title");
                JSONArray list = group.optJSONArray("tasks");
                if (list == null) {
                    continue;
                }
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    Task t = new Task();
                    t.groupTitle = groupTitle;
                    t.title = item.optString("title");
                    t.state = item.optString("state");
                    t.stateDesc = item.optString("state_desc");
                    t.type = item.optString("type");
                    t.maxjia = item.optString("maxjia");
                    if ("waiting".equals(t.state)) {
                        String raw = item.toString();
                        if (raw.length() > 600) {
                            raw = raw.substring(0, 600);
                        }
                        t.raw = raw;
                    }
                    JSONObject reportExtra = item.optJSONObject("report_extra");
                    if (reportExtra != null) {
                        t.taskId = reportExtra.optString("task_id");
                        t.reportType = reportExtra.optString("task_type");
                    }
                    JSONArray awards = item.optJSONArray("award_desc_v2");
                    if (awards != null) {
                        StringBuilder awardSb = new StringBuilder();
                        for (int a = 0; a < awards.length(); a++) {
                            JSONObject aw = awards.optJSONObject(a);
                            if (aw == null) {
                                continue;
                            }
                            String desc = aw.optString("desc");
                            String icon = aw.optString("icon");
                            if (desc.isEmpty()) {
                                continue;
                            }
                            if (icon.contains("b9aca51c")) {
                                desc = desc + "经验";
                            } else if (icon.contains("c10d89ae")) {
                                desc = desc + "H币";
                            } else if (icon.contains("e63b192a")) {
                                desc = desc + "盒电";
                            }
                            if (awardSb.length() > 0) {
                                awardSb.append(' ');
                            }
                            awardSb.append(desc);
                        }
                        t.awardText = awardSb.toString();
                    }
                    snap.tasks.add(t);
                }
            }
        }
        return snap;
    }

    static String taskKey(Task t) {
        return t.taskId + "|" + t.title;
    }

    static Task findTask(Snapshot snap, String key) {
        for (Task t : snap.tasks) {
            if (taskKey(t).equals(key)) {
                return t;
            }
        }
        return null;
    }

    static boolean isSign(Task t) {
        return "sign".equals(t.type);
    }

    static boolean isDaily(Task t) {
        return isSign(t) || "daily".equals(t.reportType);
    }

    static boolean ok(JSONObject payload) {
        return OK.equals(payload.optString("status"));
    }

    static String executeSign(HeyboxClient client) throws Exception {
        JSONObject r = signViaApp(client, "/task/sign_v3/sign");
        if ("login".equals(r.optString("status")) || "relogin".equals(r.optString("status"))) {
            throw new IllegalStateException("登录态已失效");
        }
        JSONObject res1 = r.optJSONObject("result");
        if (res1 != null && "ignore".equals(res1.optString("state"))) {
            return "今日已签到";
        }
        sleep(800);
        JSONObject r2 = signViaApp(client, "/task/sign_v3/get_sign_state");
        JSONObject res2 = r2.optJSONObject("result");
        if (res2 == null) {
            res2 = new JSONObject();
        }
        String state = res2.optString("state");
        if ((OK.equals(r2.optString("status")) && (OK.equals(state) || "success".equals(state)))
                || "ignore".equals(state)) {
            List<String> parts = new ArrayList<String>();
            String coin = res2.optString("sign_in_coin");
            String exp = res2.optString("sign_in_exp");
            String streak = res2.optString("sign_in_streak");
            if (!coin.isEmpty() && !"0".equals(coin)) {
                parts.add("+" + coin + "H币");
            }
            if (!exp.isEmpty() && !"0".equals(exp)) {
                parts.add("+" + exp + "经验");
            }
            if (!streak.isEmpty() && !"0".equals(streak)) {
                parts.add("连签" + streak + "天");
            }
            return parts.isEmpty() ? "签到完成" : join(parts, " ");
        }
        String msg = r2.optString("msg");
        if (msg.isEmpty()) {
            msg = r.optString("msg");
        }
        return msg.isEmpty() ? ("签到失败(" + state + ")") : ("签到失败: " + msg);
    }

    private static JSONObject signViaApp(HeyboxClient client, String path) throws Exception {
        if (client.appMode) {
            if (!client.allowExternal) {
                throw new IllegalStateException("App 登录态签到需要开启「社区签名服务器」");
            }
            JSONObject app = client.getAppJson(path);
            String status = app.optString("status");
            if ("relogin".equals(status) || "login".equals(status)) {
                throw new IllegalStateException("App 登录态已失效，请在账号页更换 pkey");
            }
            return app;
        }
        if (client.allowExternal) {
            JSONObject app = null;
            try {
                app = client.getAppJson(path);
            } catch (Exception ignored) {
            }
            if (app != null) {
                String status = app.optString("status");
                if ("relogin".equals(status) || "login".equals(status)) {
                    throw new IllegalStateException(
                            "服务器要求 App 登录态（当前是网页登录态），请在官方 App 抓包获取 Cookie 后粘贴");
                }
                if (!"failed".equals(status)) {
                    return app;
                }
            }
        }
        JSONObject web = client.getJson(path, null);
        if (!client.allowExternal && "failed".equals(web.optString("status"))
                && web.optString("msg").contains("非法")) {
            throw new IllegalStateException("服务器拒绝签到请求，请在主页打开「社区签名服务器」后重试");
        }
        return web;
    }

    private static void collectObjects(Object node, List<JSONObject> out, int limit) {
        if (out.size() >= limit || node == null) {
            return;
        }
        if (node instanceof JSONObject) {
            JSONObject jo = (JSONObject) node;
            if (jo.has("appid") && jo.has("h_src")) {
                out.add(jo);
                if (out.size() >= limit) {
                    return;
                }
            }
            java.util.Iterator<String> keys = jo.keys();
            while (keys.hasNext()) {
                collectObjects(jo.opt(keys.next()), out, limit);
                if (out.size() >= limit) {
                    return;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = arr.length() - 1; i >= 0; i--) {
                collectObjects(arr.opt(i), out, limit);
                if (out.size() >= limit) {
                    return;
                }
            }
        }
    }

    private static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> feedQuery() {
        Map<String, String> q = new LinkedHashMap<String, String>();
        q.put("pull", "1");
        q.put("last_pull", "1");
        q.put("is_first", "0");
        q.put("list_ver", "2");
        q.put("has_cache", "1");
        q.put("netmode", "wifi");
        return q;
    }

    static ShareTarget gatherSharePost(HeyboxClient client) throws Exception {
        JSONObject f = client.getJson("/bbs/app/feeds", feedQuery());
        if (!ok(f)) {
            throw new IllegalStateException("拉取帖子流失败");
        }
        JSONArray links = f.optJSONObject("result") == null ? null : f.optJSONObject("result").optJSONArray("links");
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject item = links.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String lid = item.optString("link_id");
                if (lid.isEmpty()) {
                    lid = item.optString("linkid");
                }
                String hs = item.optString("h_src");
                if (isDigits(lid) && !hs.isEmpty()) {
                    ShareTarget t = new ShareTarget();
                    t.source = "link";
                    t.linkId = lid;
                    t.hSrc = hs;
                    t.detail = "link_id=" + lid;
                    return t;
                }
            }
        }
        throw new IllegalStateException("帖子流里没有可用帖子");
    }

    static ShareTarget gatherShareGameDetail(HeyboxClient client) throws Exception {
        JSONObject rec = fetchRecommend(client);
        List<JSONObject> objs = new ArrayList<JSONObject>();
        collectObjects(rec.opt("result"), objs, 40);
        for (JSONObject o : objs) {
            String a = o.optString("appid");
            String hs = o.optString("h_src");
            if (isDigits(a) && !hs.isEmpty()) {
                ShareTarget t = new ShareTarget();
                t.source = "game_detail";
                t.appId = a;
                t.hSrc = hs;
                t.detail = "appid=" + a;
                return t;
            }
        }
        throw new IllegalStateException("没有可用游戏");
    }

    static ShareTarget gatherShareGameComment(HeyboxClient client) throws Exception {
        JSONObject rec = fetchRecommend(client);
        List<JSONObject> objs = new ArrayList<JSONObject>();
        collectObjects(rec.opt("result"), objs, 40);
        String appid = "";
        for (JSONObject o : objs) {
            String a = o.optString("appid");
            if (isDigits(a)) {
                appid = a;
                break;
            }
        }
        if (appid.isEmpty()) {
            throw new IllegalStateException("没有可用游戏");
        }
        Map<String, String> cq = new LinkedHashMap<String, String>();
        cq.put("api_version", "4");
        cq.put("offset", "0");
        cq.put("limit", "30");
        cq.put("appid", appid);
        JSONObject comments = client.getJson("/bbs/app/link/game/comments", cq);
        if (!ok(comments)) {
            throw new IllegalStateException("拉取游戏评论失败");
        }
        JSONArray links = comments.optJSONObject("result") == null ? null : comments.optJSONObject("result").optJSONArray("links");
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject item = links.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String lid = item.optString("linkid");
                if (lid.isEmpty()) {
                    lid = item.optString("link_id");
                }
                String uid = item.optString("userid");
                String hs = item.optString("h_src");
                if (isDigits(lid) && isDigits(uid) && !hs.isEmpty()) {
                    ShareTarget t = new ShareTarget();
                    t.source = "game_comment";
                    t.appId = appid;
                    t.linkId = lid;
                    t.hSrc = hs;
                    t.detail = "appid=" + appid;
                    return t;
                }
            }
        }
        throw new IllegalStateException("评论列表缺少关键字段");
    }

    private static JSONObject fetchRecommend(HeyboxClient client) throws Exception {
        Map<String, String> q = new LinkedHashMap<String, String>();
        q.put("offset", "0");
        q.put("limit", "1");
        JSONObject rec = client.getJson("/game/all_recommend/v2", q);
        if (!ok(rec)) {
            throw new IllegalStateException("拉取游戏列表失败");
        }
        return rec;
    }

    static String buildShareEventReport(boolean tap, String source, String linkId, String hSrc, String appId)
            throws JSONException {
        JSONObject addition = new JSONObject();
        addition.put("src", source);
        addition.put("plat", "WechatSession");
        if (!linkId.isEmpty()) {
            addition.put("link_id", linkId);
        }
        if ("link".equals(source) && !hSrc.isEmpty()) {
            addition.put("h_src", hSrc);
        }
        if (!appId.isEmpty()) {
            addition.put("appid", appId);
        }
        JSONObject event = new JSONObject();
        event.put("type", tap ? "4" : "3");
        event.put("path", tap ? "/share/behavior/tap" : "/share/behavior/success");
        event.put("time", String.valueOf(System.currentTimeMillis() / 1000L));
        event.put("addition", addition);
        JSONArray events = new JSONArray();
        events.put(event);
        JSONObject payload = new JSONObject();
        payload.put("events", events);
        return payload.toString();
    }

    static String buildViewTimeReport(String linkId, String hSrc) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", Long.parseLong(linkId));
        item.put("duration", 5);
        item.put("duration_ms", 5000);
        item.put("type", "link");
        item.put("time", System.currentTimeMillis() / 1000L);
        item.put("h_src", hSrc);
        JSONArray duration = new JSONArray();
        duration.put(item);
        JSONObject payload = new JSONObject();
        payload.put("duration", duration);
        payload.put("shows", new JSONArray());
        payload.put("disappear", new JSONArray());
        return payload.toString();
    }

    static void sendLocalShareEvents(HeyboxClient client, ShareTarget t) throws Exception {
        JSONObject tapResp = client.postLocalEncryptedReport(
                "/account/data_report_web/", buildShareEventReport(true, t.source, t.linkId, t.hSrc, t.appId),
                sessionQuery());
        if (!ok(tapResp)) {
            throw new IllegalStateException("分享tap上报失败: " + tapResp.optString("msg"));
        }
        sleep(2000);
        JSONObject successResp = client.postLocalEncryptedReport(
                "/account/data_report_web/", buildShareEventReport(false, t.source, t.linkId, t.hSrc, t.appId),
                sessionQuery());
        if (!ok(successResp)) {
            throw new IllegalStateException("分享success上报失败: " + successResp.optString("msg"));
        }
    }

    private static Map<String, String> sessionQuery() {
        LinkedHashMap<String, String> q = new LinkedHashMap<String, String>();
        q.put("session_id", "trace-" + HeyboxClient.uuid());
        return q;
    }

    static void sendAppShareEvents(HeyboxClient client, ShareTarget t) throws Exception {
        LinkedHashMap<String, String> extra = new LinkedHashMap<String, String>();
        extra.put("type", "104");
        extra.put("session_id", HeyboxClient.uuid());
        if ("link".equals(t.source) && !t.linkId.isEmpty() && !t.hSrc.isEmpty()) {
            JSONObject vt = client.postAppEncryptedReport(
                    "/bbs/app/link/view/time", buildViewTimeReport(t.linkId, t.hSrc), extra);
            if (!ok(vt)) {
                throw new IllegalStateException("view_time上报失败: " + vt.optString("msg"));
            }
            sleep(1000);
        }
        JSONObject tapResp = client.postAppEncryptedReport(
                "/account/data_report/", buildShareEventReport(true, t.source, t.linkId, t.hSrc, t.appId), extra);
        if (!ok(tapResp)) {
            throw new IllegalStateException("分享tap上报失败: " + tapResp.optString("msg"));
        }
        sleep(2000);
        JSONObject successResp = client.postAppEncryptedReport(
                "/account/data_report/", buildShareEventReport(false, t.source, t.linkId, t.hSrc, t.appId), extra);
        if (!ok(successResp)) {
            throw new IllegalStateException("分享success上报失败: " + successResp.optString("msg"));
        }
    }

    static Task settlePoll(HeyboxClient client, String key, int rounds) throws Exception {
        for (int i = 0; i < rounds; i++) {
            sleep(2200);
            Snapshot snap = fetchSnapshot(client);
            Task after = findTask(snap, key);
            if (after != null && "finish".equals(after.state)) {
                return after;
            }
        }
        return null;
    }

    static RunResult runAll(List<String> cookies, boolean allowExternal, boolean sharesEnabled, String deviceId) {
        RunResult result = new RunResult();
        if (cookies.isEmpty()) {
            result.allOk = false;
            result.lines.add("还没有配置 cookie，请打开 App 填写");
            return result;
        }
        for (int idx = 0; idx < cookies.size(); idx++) {
            String cookie = cookies.get(idx);
            String prefix = cookies.size() > 1 ? ("== 账号" + (idx + 1) + " ==") : "";
            try {
                HeyboxClient client = new HeyboxClient(cookie, allowExternal, deviceId);
                if (!prefix.isEmpty()) {
                    result.lines.add(prefix);
                }
                boolean ok = runAccount(client, result.lines, sharesEnabled);
                if (!ok) {
                    result.allOk = false;
                }
            } catch (IllegalArgumentException e) {
                result.allOk = false;
                result.lines.add((prefix.isEmpty() ? "" : prefix + " ") + "cookie无效: " + e.getMessage());
            } catch (Exception e) {
                result.allOk = false;
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                result.lines.add((prefix.isEmpty() ? "" : prefix + " ") + "执行失败: " + msg);
            }
        }
        return result;
    }

    private static boolean runAccount(HeyboxClient client, List<String> out, boolean sharesEnabled)
            throws Exception {
        out.add("cookie项: " + client.cookieKeysSummary);
        out.add("开关: 分享任务=" + (sharesEnabled ? "开" : "关")
                + " 社区hkey服务器=" + (client.allowExternal ? "开" : "关"));
        Snapshot snap = fetchSnapshot(client);
        String lv = snap.level.isEmpty() ? "" : " Lv." + snap.level;
        String expText = (snap.exp.isEmpty() || snap.maxExp.isEmpty())
                ? "" : " 经验 " + snap.exp + "/" + snap.maxExp;
        out.add("账号: " + (snap.nickname.isEmpty() ? client.heyboxId : snap.nickname)
                + " (ID:" + client.heyboxId + ")" + lv + expText
                + " 当前H币: " + (snap.coin.isEmpty() ? "?" : snap.coin)
                + " 盒电: " + (snap.battery.isEmpty() ? "?" : snap.battery));

        boolean ok = true;
        List<String> unsupported = new ArrayList<String>();

        List<Task> waiting = new ArrayList<Task>();
        for (Task t : snap.tasks) {
            if (isDaily(t) && "waiting".equals(t.state)) {
                waiting.add(t);
            }
        }

        for (Task task : waiting) {
            if (isSign(task)) {
                try {
                    String r = executeSign(client);
                    out.add("签到: " + r);
                    if (r.startsWith("签到失败")) {
                        ok = false;
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    out.add("签到: 失败 - " + msg);
                    ok = false;
                }
            } else if (isKnownShare(task.taskId)) {
                if (sharesEnabled) {
                    shareTaskFlow(client, task, out, gathererFor(task.taskId));
                } else {
                    unsupported.add(task.title.isEmpty() ? ("任务" + task.taskId) : task.title);
                }
            } else {
                unsupported.add(task.title.isEmpty() ? ("任务" + task.taskId) : task.title);
            }
        }

        Snapshot last = fetchSnapshot(client);
        StringBuilder dump = new StringBuilder("任务: ");
        for (Task t : last.tasks) {
            dump.append("[").append(t.taskId).append("/").append(t.type).append("/").append(t.reportType)
                    .append("/").append(t.state).append("] ").append(t.title).append("  ");
        }
        out.add(dump.toString());
        int remain = 0;
        int finished = 0;
        for (Task t : last.tasks) {
            if (!isDaily(t)) {
                continue;
            }
            if ("waiting".equals(t.state)) {
                remain++;
            } else if ("finish".equals(t.state)) {
                finished++;
            }
        }
        out.add("任务汇总: 完成" + finished + "个" + (remain > 0 ? "，未完成" + remain + "个" : "")
                + "，当前H币: " + (last.coin.isEmpty() ? "?" : last.coin));
        if (!unsupported.isEmpty()) {
            out.add("未处理任务: " + join(unsupported, "、"));
        }
        if (remain > 0) {
            ok = false;
        }
        return ok;
    }

    private static boolean isKnownShare(String taskId) {
        return "1".equals(taskId) || "19".equals(taskId) || "11".equals(taskId) || "31".equals(taskId);
    }

    private static Gatherer gathererFor(String taskId) {
        if ("19".equals(taskId)) {
            return new Gatherer() {
                public ShareTarget run(HeyboxClient c) throws Exception {
                    return gatherShareGameDetail(c);
                }
            };
        }
        if ("11".equals(taskId) || "31".equals(taskId)) {
            return new Gatherer() {
                public ShareTarget run(HeyboxClient c) throws Exception {
                    return gatherShareGameComment(c);
                }
            };
        }
        return new Gatherer() {
            public ShareTarget run(HeyboxClient c) throws Exception {
                return gatherSharePost(c);
            }
        };
    }

    private interface Gatherer {
        ShareTarget run(HeyboxClient client) throws Exception;
    }

    private static void shareTaskFlow(HeyboxClient client, Task task, List<String> out, Gatherer gatherer) {
        String key = taskKey(task);
        ShareTarget target = null;
        try {
            target = gatherer.run(client);
            if (client.appMode) {
                if (!client.allowExternal) {
                    out.add(task.title + ": 失败 - App 登录态需要开启「社区签名服务器」");
                    return;
                }
                String[][] appVariants = "game_comment".equals(target.source)
                        ? new String[][]{{"game_comment", "1"}, {"comment", "1"}}
                        : new String[][]{{target.source, "1"}};
                for (String[] v : appVariants) {
                    ShareTarget vt = new ShareTarget();
                    vt.source = v[0];
                    vt.linkId = target.linkId;
                    vt.hSrc = "1".equals(v[1]) ? target.hSrc : "";
                    vt.appId = target.appId;
                    try {
                        sendAppShareEvents(client, vt);
                        Task after = settlePoll(client, key, 4);
                        if (after != null) {
                            out.add(task.title + ": 完成（服务器/" + v[0] + "）"
                                    + (task.awardText.isEmpty() ? "" : " 奖励:" + task.awardText));
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                }
                out.add(task.title + ": 已上报，服务器结算可能延迟");
                if (!task.raw.isEmpty()) {
                    out.add("原始: " + task.raw);
                }
                return;
            }
            boolean commentTask = "game_comment".equals(target.source);
            if (commentTask) {
                String[][] variants = new String[][]{
                        {"app", "game_comment", "1"},
                        {"app", "comment", "1"},
                        {"local", "game_comment", "0"},
                        {"local", "comment", "1"}};
                for (String[] v : variants) {
                    boolean useApp = "app".equals(v[0]);
                    boolean withHSrc = "1".equals(v[2]);
                    if (useApp && !client.allowExternal) {
                        continue;
                    }
                    ShareTarget vt = new ShareTarget();
                    vt.source = v[1];
                    vt.linkId = target.linkId;
                    vt.hSrc = withHSrc ? target.hSrc : "";
                    vt.appId = target.appId;
                    try {
                        if (useApp) {
                            sendAppShareEvents(client, vt);
                        } else {
                            sendLocalShareEvents(client, vt);
                        }
                        Task after = settlePoll(client, key, 4);
                        if (after != null) {
                            out.add(task.title + ": 完成（" + (useApp ? "服务器" : "本地") + "/" + v[1]
                                    + (withHSrc ? "+h_src" : "") + "）"
                                    + (task.awardText.isEmpty() ? "" : " 奖励:" + task.awardText));
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                }
                out.add(task.title + ": 已上报，服务器结算可能延迟");
                if (!task.raw.isEmpty()) {
                    out.add("原始: " + task.raw);
                }
                return;
            }
            if (client.allowExternal) {
                try {
                    sendAppShareEvents(client, target);
                    Task after = settlePoll(client, key, 3);
                    if (after != null) {
                        out.add(task.title + ": 完成（经社区hkey服务器）"
                                + (task.awardText.isEmpty() ? "" : " 奖励:" + task.awardText));
                        return;
                    }
                    out.add(task.title + ": 服务器上报未结算，尝试本地上报…");
                } catch (Exception e) {
                    out.add(task.title + ": 服务器上报失败(" + (e.getMessage() == null ? e.toString() : e.getMessage())
                            + ")，尝试本地上报…");
                }
            }
            sendLocalShareEvents(client, target);
            Task afterLocal = settlePoll(client, key, 3);
            if (afterLocal != null) {
                out.add(task.title + ": 完成（本地上报）"
                        + (task.awardText.isEmpty() ? "" : " 奖励:" + task.awardText));
                return;
            }
            if (!client.allowExternal) {
                out.add(task.title + ": 未完成。本地上报未结算，请在App里勾选「允许社区hkey服务器」后重试");
            } else {
                out.add(task.title + ": 未完成（两种上报方式都没结算）");
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            out.add(task.title + ": 失败 - " + msg);
        }
    }

    static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(s);
        }
        return sb.toString();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
