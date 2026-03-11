package com.example.cardvault;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/**
 * Manages the friends list — people scanned via QR.
 * Stored as JSON array in SharedPreferences under key "friends_json".
 */
public class FriendsManager {

    private static final String PREFS_NAME = "cv_friends";
    private static final String KEY        = "friends_json";

    public static class Friend {
        public String id       = "";
        public String name     = "";
        public String role     = "";
        public String phone    = "";
        public String email    = "";
        public String whatsapp = "";
        public String linkedin = "";
        public String twitter  = "";
        public String github   = "";
        public String initials = "";
        public String color    = "#00F5FF";
        public String accent   = "#7C6FCD";
        public long   addedAt  = 0;

        public Friend() {
            id      = String.valueOf(System.currentTimeMillis());
            addedAt = System.currentTimeMillis();
        }

        public Friend(JSONObject j) throws JSONException {
            id       = j.optString("id",       String.valueOf(System.currentTimeMillis()));
            name     = j.optString("name",     "");
            role     = j.optString("role",     "");
            phone    = j.optString("phone",    "");
            email    = j.optString("email",    "");
            whatsapp = j.optString("whatsapp", "");
            linkedin = j.optString("linkedin", "");
            twitter  = j.optString("twitter",  "");
            github   = j.optString("github",   "");
            initials = j.optString("initials", "");
            color    = j.optString("color",    "#00F5FF");
            accent   = j.optString("accent",   "#7C6FCD");
            addedAt  = j.optLong("addedAt",    System.currentTimeMillis());
        }

        /** Build a Friend from the cardvault:// deep link URI parameters */
        public static Friend fromUri(android.net.Uri uri) {
            Friend f = new Friend();
            f.name     = decode(uri.getQueryParameter("name"));
            f.role     = decode(uri.getQueryParameter("role"));
            f.phone    = decode(uri.getQueryParameter("phone"));
            f.email    = decode(uri.getQueryParameter("email"));
            f.whatsapp = decode(uri.getQueryParameter("wa"));
            f.linkedin = decode(uri.getQueryParameter("li"));
            f.twitter  = decode(uri.getQueryParameter("tw"));
            f.github   = decode(uri.getQueryParameter("gh"));
            f.initials = decode(uri.getQueryParameter("initials"));
            f.color    = decode(uri.getQueryParameter("color"));
            f.accent   = decode(uri.getQueryParameter("accent"));
            if (f.initials == null || f.initials.isEmpty()) f.initials = makeInitials(f.name);
            if (f.color  == null || f.color.isEmpty())  f.color  = "#00F5FF";
            if (f.accent == null || f.accent.isEmpty()) f.accent = "#7C6FCD";
            return f;
        }

        private static String decode(String s) {
            if (s == null) return "";
            try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
        }

        private static String makeInitials(String name) {
            if (name == null || name.isEmpty()) return "?";
            String[] parts = name.trim().split("\\s+");
            if (parts.length == 1) return name.substring(0, Math.min(2, name.length())).toUpperCase();
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }

        public JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id",       id)
                    .put("name",     name)
                    .put("role",     role)
                    .put("phone",    phone)
                    .put("email",    email)
                    .put("whatsapp", whatsapp)
                    .put("linkedin", linkedin)
                    .put("twitter",  twitter)
                    .put("github",   github)
                    .put("initials", initials)
                    .put("color",    color)
                    .put("accent",   accent)
                    .put("addedAt",  addedAt);
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    public static List<Friend> load(Context ctx) {
        List<Friend> list = new ArrayList<>();
        try {
            String json = prefs(ctx).getString(KEY, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) list.add(new Friend(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return list;
    }

    public static void save(Context ctx, List<Friend> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Friend f : list) arr.put(f.toJson());
            prefs(ctx).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Add a friend (no duplicates by name+phone). Returns true if actually added. */
    public static boolean addFriend(Context ctx, Friend f) {
        List<Friend> list = load(ctx);
        for (Friend existing : list) {
            boolean sameName  = existing.name.equalsIgnoreCase(f.name);
            boolean samePhone = !f.phone.isEmpty() && existing.phone.equals(f.phone);
            boolean sameEmail = !f.email.isEmpty() && existing.email.equalsIgnoreCase(f.email);
            if (sameName || samePhone || sameEmail) return false; // duplicate
        }
        list.add(0, f); // newest first
        save(ctx, list);
        return true;
    }

    public static void deleteFriend(Context ctx, String friendId) {
        List<Friend> list = load(ctx);
        list.removeIf(f -> f.id.equals(friendId));
        save(ctx, list);
    }

    public static int count(Context ctx) {
        return load(ctx).size();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}