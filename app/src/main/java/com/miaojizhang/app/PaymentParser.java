package com.miaojizhang.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentParser {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:人民币|RMB|CNY|¥|￥)?\\s*([0-9]{1,8}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*(?:元|块|圆)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[] INCOME_KEYS = new String[]{
            "收款", "到账", "入账", "转入", "收入", "退款", "退回", "已收款", "收到转账", "已到账", "来账", "收钱"
    };

    private static final String[] EXPENSE_KEYS = new String[]{
            "支付成功", "成功支付", "付款", "已支付", "消费", "扣款", "支出", "转出", "还款", "支付了", "银行卡消费", "交易支出", "订单支付"
    };

    private static final String[] NEGATIVE_KEYS = new String[]{
            "优惠券", "满减", "立减", "折扣", "活动", "促销", "秒杀", "报价", "价格", "待支付", "去支付", "账单已出", "月账单", "还款提醒", "余额不足", "可用额度", "积分", "年化", "收益率", "红包封面", "抽奖", "开通", "订阅提醒"
    };

    private PaymentParser() {}

    public static Suggestion parse(String packageName, String appName, String title, String text, long when) {
        String raw = join(title, text).trim();
        if (raw.length() == 0) return null;
        String normalized = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ");
        if (containsAny(normalized, NEGATIVE_KEYS)) return null;

        boolean incomeHit = containsAny(normalized, INCOME_KEYS);
        boolean expenseHit = containsAny(normalized, EXPENSE_KEYS);
        if (!incomeHit && !expenseHit) return null;

        double amount = findAmount(normalized);
        if (amount <= 0) return null;
        if (amount > 9999999) return null;

        String type;
        if (incomeHit && !expenseHit) type = "income";
        else if (!incomeHit) type = "expense";
        else if (containsAny(normalized, new String[]{"退款", "退回", "收款", "到账", "入账", "转入", "收入"})) type = "income";
        else type = "expense";

        Date d = new Date(when > 0 ? when : System.currentTimeMillis());
        Suggestion s = new Suggestion();
        s.id = sha1(packageName + "|" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(d) + "|" + normalized + "|" + String.format(Locale.US, "%.2f", amount));
        s.packageName = packageName == null ? "" : packageName;
        s.source = cleanSource(appName, packageName);
        s.type = type;
        s.amount = amount;
        s.title = safe(title);
        s.rawText = normalized;
        s.note = buildNote(s.source, normalized);
        s.categoryName = guessCategoryName(type, normalized);
        s.categoryIcon = guessCategoryIcon(type, s.categoryName);
        s.date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(d);
        s.time = new SimpleDateFormat("HH:mm", Locale.CHINA).format(d);
        s.createdAt = System.currentTimeMillis();
        return s;
    }

    private static String join(String a, String b) {
        String x = safe(a);
        String y = safe(b);
        if (x.length() == 0) return y;
        if (y.length() == 0 || x.equals(y)) return x;
        return x + " " + y;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean containsAny(String s, String[] keys) {
        for (String k : keys) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    private static double findAmount(String s) {
        Matcher m = AMOUNT_PATTERN.matcher(s);
        double best = -1;
        while (m.find()) {
            String token = m.group(1);
            if (token == null) continue;
            String before = s.substring(Math.max(0, m.start() - 3), m.start());
            String after = s.substring(m.end(), Math.min(s.length(), m.end() + 3));
            // 避免把日期、时间、尾号、验证码当金额。
            if (before.contains("尾号") || before.contains("验证码") || after.contains("年") || after.contains("月") || after.contains("日")) continue;
            try {
                double v = Double.parseDouble(token.replace(",", ""));
                if (v > 0 && v > best) best = v;
            } catch (Exception ignored) {}
        }
        return best;
    }

    private static String cleanSource(String appName, String packageName) {
        String n = safe(appName);
        if (n.length() > 0) return n;
        String p = safe(packageName);
        int idx = p.lastIndexOf('.');
        return idx >= 0 && idx < p.length() - 1 ? p.substring(idx + 1) : p;
    }

    private static String buildNote(String source, String text) {
        String s = text == null ? "" : text;
        s = s.replaceAll("(?:人民币|RMB|CNY|¥|￥)?\\s*[0-9]{1,8}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*(?:元|块|圆)?", "");
        s = s.replaceAll("支付成功|成功支付|付款|已支付|消费|扣款|支出|转出|还款|订单支付|收款|到账|入账|转入|收入|退款成功|退款|退回|已收款", "");
        s = s.replaceAll("你已|您已|向|给|来自|通过|尾号\\d+|账户|银行卡|零钱|余额", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 18) s = s.substring(0, 18).trim();
        if (s.length() == 0) s = source;
        if (source != null && source.length() > 0 && !s.contains(source)) return source + " · " + s;
        return s;
    }

    private static String guessCategoryName(String type, String text) {
        if ("income".equals(type)) {
            if (containsAny(text, new String[]{"退款", "退回"})) return "退款";
            if (containsAny(text, new String[]{"工资", "薪资", "薪水"})) return "工资";
            if (containsAny(text, new String[]{"红包"})) return "红包";
            if (containsAny(text, new String[]{"转账", "到账", "入账", "转入", "来账"})) return "转账";
            return "其他";
        }
        if (containsAny(text, new String[]{"美团", "饿了么", "餐", "饭", "咖啡", "奶茶", "外卖", "便利店", "盒马", "超市", "食堂"})) return "餐饮";
        if (containsAny(text, new String[]{"滴滴", "打车", "地铁", "公交", "高铁", "火车", "机票", "加油", "停车"})) return "出行";
        if (containsAny(text, new String[]{"淘宝", "京东", "拼多多", "天猫", "购物", "商城"})) return "购物";
        if (containsAny(text, new String[]{"医院", "药", "门诊", "医疗"})) return "医疗";
        if (containsAny(text, new String[]{"话费", "移动", "联通", "电信", "宽带"})) return "通讯";
        if (containsAny(text, new String[]{"还款", "信用卡"})) return "还款";
        return "其他";
    }

    private static String guessCategoryIcon(String type, String name) {
        if ("income".equals(type)) {
            if ("工资".equals(name)) return "💴";
            if ("红包".equals(name)) return "🧧";
            if ("退款".equals(name)) return "↩️";
            if ("转账".equals(name)) return "🏦";
            return "💰";
        }
        if ("餐饮".equals(name)) return "🍜";
        if ("出行".equals(name)) return "🚌";
        if ("购物".equals(name)) return "🛍️";
        if ("医疗".equals(name)) return "✚";
        if ("通讯".equals(name)) return "📱";
        if ("还款".equals(name)) return "💳";
        return "…";
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public static final class Suggestion {
        public String id;
        public String packageName;
        public String source;
        public String type;
        public double amount;
        public String title;
        public String rawText;
        public String note;
        public String categoryName;
        public String categoryIcon;
        public String date;
        public String time;
        public long createdAt;

        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("packageName", packageName);
            o.put("source", source);
            o.put("type", type);
            o.put("amount", amount);
            o.put("title", title);
            o.put("rawText", rawText);
            o.put("note", note);
            o.put("categoryName", categoryName);
            o.put("categoryIcon", categoryIcon);
            o.put("date", date);
            o.put("time", time);
            o.put("createdAt", createdAt);
            return o;
        }

        public String toJsonString() {
            try { return toJson().toString(); } catch (Exception e) { return "{}"; }
        }
    }
}
