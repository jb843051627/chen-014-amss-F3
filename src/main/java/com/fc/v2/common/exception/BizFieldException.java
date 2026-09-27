package com.fc.v2.common.exception;

/**
 * 业务保存时某一格没过校验——回执必须点名列在哪一格，不许笼统回一句"数不像话"。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class BizFieldException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 出岔的那一格（表单字段名） */
    private final String field;

    public BizFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
