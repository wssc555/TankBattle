package com.tankbattle.common.protocol;

/**
 * 文本协议编解码基元。
 * <p>
 * 全部为无状态纯函数，供解码器复用；解析失败一律抛 {@link IllegalArgumentException}，
 * 由调用方按"单条坏指令隔离"处理。
 */
public final class TextCodec {

    private TextCodec() {
    }

    /**
     * 按逗号切分指令字段；{@code -1} 保留尾部空串。
     *
     * @throws IllegalArgumentException 字段数与期望不符
     */
    public static String[] fields(String tokenBody, int expectedCount) {
        String[] parts = tokenBody.split(",", -1);
        if (parts.length != expectedCount) {
            throw new IllegalArgumentException(
                    "字段数不符: 期望 " + expectedCount + "，实际 " + parts.length + " [" + tokenBody + "]");
        }
        return parts;
    }

    /**
     * 解析定长整数字段组。
     *
     * @throws IllegalArgumentException 字段数不符或含非整数
     */
    public static int[] ints(String tokenBody, int count) {
        String[] parts = fields(tokenBody, count);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = Integer.parseInt(parts[i].strip());
        }
        return values;
    }

    /**
     * 解析位图串：字符 i 为 {@code 1} 时置位 {@code 1 << i}。
     *
     * @param width 期望长度（砖墙 16 / 钢墙 4）
     * @throws IllegalArgumentException 长度不符或出现 0/1 之外的字符
     */
    public static int bits(String value, int width) {
        if (value == null || value.length() != width) {
            throw new IllegalArgumentException(
                    "位图长度不符: 期望 " + width + "，实际 " + (value == null ? -1 : value.length()));
        }
        int result = 0;
        for (int i = 0; i < width; i++) {
            char c = value.charAt(i);
            if (c != '0' && c != '1') {
                throw new IllegalArgumentException("非法位: " + c);
            }
            if (c == '1') {
                result |= 1 << i;
            }
        }
        return result;
    }

    /**
     * 位图 → 协议串，索引 0..width-1 顺序输出。
     */
    public static String bitString(int bits, int width) {
        StringBuilder out = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            out.append((bits >>> i & 1) != 0 ? '1' : '0');
        }
        return out.toString();
    }
}
