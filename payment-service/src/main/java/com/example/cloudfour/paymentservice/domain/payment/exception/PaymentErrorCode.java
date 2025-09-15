package com.example.cloudfour.paymentservice.domain.payment.exception;

import com.example.cloudfour.modulecommon.apiPayLoad.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {

    PAYMENT_APPROVAL_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT400_1", "결제 승인에 실패했습니다."),
    PAYMENT_ALREADY_APPROVED(HttpStatus.CONFLICT, "PAYMENT409_1", "이미 승인된 결제입니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT400_8", "결제 금액이 일치하지 않습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_4", "주문을 찾을 수 없습니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAYMENT409_3", "이미 처리된 결제입니다."),
    IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "PAYMENT400_9", "멱등키가 누락되었습니다."),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "PAYMENT400_10", "멱등키 형식이 올바르지 않습니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "PAYMENT409_4", "이전 요청이 아직 처리 중입니다."),
    IDEMPOTENCY_KEY_PAYLOAD_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT422_1", "재시도된 요청의 본문이 이전 요청과 다릅니다."),
    PAYMENT_CANCEL_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT400_2", "결제 취소에 실패했습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT400_3", "잘못된 결제 상태입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_1", "결제 정보를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_2", "사용자를 찾을 수 없습니다."),
    UNAUTHORIZED_PAYMENT_ACCESS(HttpStatus.FORBIDDEN, "PAYMENT403_1", "결제에 대한 접근 권한이 없습니다."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "PAYMENT401_1", "웹훅 서명이 유효하지 않습니다."),
    WEBHOOK_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT500_1", "웹훅 처리에 실패했습니다."),
    UNKNOWN_TOSS_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT400_4", "알 수 없는 토스 상태입니다."),
    TOSS_API_ERROR(HttpStatus.BAD_GATEWAY, "PAYMENT502_1", "토스페이먼츠 API 호출에 실패했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "PAYMENT400_0", "잘못된 입력값입니다."),
    USER_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT400_5", "사용자 검증에 실패했습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT404_3", "가게를 찾을 수 없습니다."),
    STORE_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT400_6", "가게 검증에 실패했습니다."),
    PAYMENT_REQUEST_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT400_7", "결제 요청 생성에 실패했습니다."),
    DUPLICATE_ORDER(HttpStatus.CONFLICT, "PAYMENT409_2", "이미 존재하는 주문입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT500_0", "내부 서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String message;

    @Override
    public HttpStatus getStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return errorCode;
    }
}
