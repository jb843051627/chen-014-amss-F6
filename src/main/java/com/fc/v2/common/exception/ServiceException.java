package com.fc.v2.common.exception;

/**
 * 业务规则挡回异常：卡流转中被规则挡住（门禁不满足、一车二卡、已了结再动等）。
 * 由全局异常解析器 {@link GlobalExceptionResolver} 按 RuntimeException 折成 AjaxResult.error(msg)，
 * 挡回的原因要能原样摆到经办人屏幕上。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }
}
