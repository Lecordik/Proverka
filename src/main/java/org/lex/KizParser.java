package org.lex;


public class KizParser {

    public static ParseResult parse(String raw) {
        String s = raw;

        if (s.startsWith("]d2") || s.startsWith("]D2") ||
            s.startsWith("]C1") || s.startsWith("]e0") ||
            s.startsWith("]Q3")) {
            s = s.substring(3);
        }

        if (s.matches("\\d{13,14}")) {
            ParseResult r = new ParseResult();
            r.gtin = s.length() == 13 ? "0" + s : s;
            r.valid = true;
            r.productName = "EAN штрихкод";
            return r;
        }

        if (s.startsWith("01") && s.length() >= 18) {
            ParseResult r = new ParseResult();

            String gtin14 = s.substring(2, 16);
            if (!gtin14.matches("\\d{14}")) return error("Некорректный GTIN: " + gtin14);
            r.gtin = gtin14;

            if (!s.substring(16).startsWith("21")) return error("Отсутствует AI 21");
            String afterSerial = s.substring(18);

            int gsPos   = afterSerial.indexOf('\u001d');
            int ai91pos = findAI(afterSerial, "91");
            int ai93pos = findAI(afterSerial, "93");
            int endSerial;
            if      (gsPos   >= 0) endSerial = gsPos;
            else if (ai91pos >  0) endSerial = ai91pos;
            else if (ai93pos >  0) endSerial = ai93pos;
            else                   endSerial = Math.min(afterSerial.length(), 13);

            r.serial = afterSerial.substring(0, endSerial);
            if (r.serial.isEmpty() || r.serial.length() > 13)
                return error("Некорректный серийный номер");

            String rest = afterSerial.substring(endSerial);
            if (rest.startsWith("\u001d")) rest = rest.substring(1);

            if (rest.startsWith("91")) {
                r.verifyKey = rest.substring(2, Math.min(rest.length(), 6));
                rest = rest.length() > 6 ? rest.substring(6) : "";
                if (rest.startsWith("\u001d")) rest = rest.substring(1);
            }

            if (rest.startsWith("92"))
                r.verifyCode = rest.substring(2, Math.min(rest.length(), 46));
            else if (rest.startsWith("93"))
                r.verifyCode = rest.substring(2, Math.min(rest.length(), 6));

            r.valid = true;
            r.productName = "DataMatrix ЧЗ";
            return r;
        }

        return error("Неизвестный формат кода");
    }

    private static int findAI(String s, String ai) {
        for (int i = 2; i < s.length() - ai.length(); i++) {
            if (s.startsWith(ai, i) && Character.isDigit(s.charAt(i - 1))) return i;
        }
        return -1;
    }

    private static ParseResult error(String msg) {
        ParseResult r = new ParseResult();
        r.valid    = false;
        r.errorMsg = msg;
        return r;
    }

    public static class ParseResult {
        public boolean valid      = false;
        public String  errorMsg   = "Неизвестная ошибка";
        public String  gtin;
        public String  serial;
        public String  verifyKey;
        public String  verifyCode;
        public String  productName;
    }
}
