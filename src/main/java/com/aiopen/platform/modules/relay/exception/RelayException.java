package com.aiopen.platform.modules.relay.exception;

import lombok.Getter;

/**
 * relay 转发阶段(转发前的鉴权/校验/选路)抛出的异常。
 * 由 RelayController 本地 @ExceptionHandler 捕获并以 OpenAI 错误格式返回。
 * 注意:一旦开始写出响应(流式),不再抛此异常。
 */
@Getter
public class RelayException extends RuntimeException {

    private final int httpStatus;
    private final String type;

    public RelayException(int httpStatus, String type, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.type = type;
    }
}
