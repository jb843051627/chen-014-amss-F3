package com.fc.v2.common.exception.amss;

/**
 * 名册业务回执异常：立卡/改版/压死被业务规则拦下时抛出。
 * 回执必须点名列在哪一格出的岔，message 中带格子名（中文名+列名），
 * 不许笼统回一句"数不像话"。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class AmssBizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 出岔的格子（列名），无单一归属格时为 null */
    private final String field;

    public AmssBizException(String message) {
        this(null, message);
    }

    public AmssBizException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }

    /** 必填格缺失的统一回执：点名格子，不许笼统 */
    public static AmssBizException required(String field, String label) {
        return new AmssBizException(field, "必填项缺失：" + label + "（" + field + "）这一格非填不可");
    }

    /** 类别带动的必填格缺失：点名格子与作业类别 */
    public static AmssBizException requiredByType(String field, String label, String workType) {
        return new AmssBizException(field,
                "必填项缺失：" + label + "（" + field + "）——作业类别为「" + workType + "」时这一格非填不可");
    }
}
