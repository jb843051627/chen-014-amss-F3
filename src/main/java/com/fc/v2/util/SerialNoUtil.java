package com.fc.v2.util;

import java.util.Collection;

/**
 * 编号给号工具：名册编号一律由系统自动给号，
 * 站里不许谁手工另编一套。按「前缀+数字」取现存最大号顺推一位。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class SerialNoUtil {

    private SerialNoUtil() {
    }

    /**
     * 顺推下一号：在现存编号中找「前缀+纯数字」的最大数字，加一后按最小宽度补零。
     *
     * @param prefix   编号前缀（如 ZZ、JK）
     * @param minWidth 数字部分最小宽度（不足补零，超出自然增长）
     * @param existing 现存编号（可为 null/空集，此时给 1 号）
     * @return 新号
     */
    public static String next(String prefix, int minWidth, Collection<String> existing) {
        long max = 0;
        if (existing != null) {
            for (String no : existing) {
                if (no == null || !no.startsWith(prefix)) {
                    continue;
                }
                String digits = no.substring(prefix.length());
                if (digits.matches("\\d+")) {
                    max = Math.max(max, Long.parseLong(digits));
                }
            }
        }
        return prefix + String.format("%0" + minWidth + "d", max + 1);
    }
}
