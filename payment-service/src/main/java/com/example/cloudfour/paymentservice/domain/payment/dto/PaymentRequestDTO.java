package com.example.cloudfour.paymentservice.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

public class PaymentRequestDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentConfirmRequestDTO {
        @NotBlank(message = "결제키는 필수입니다")
        @Size(max = 200, message = "결제키는 200자를 초과할 수 없습니다")
        private String paymentKey;
        
        @NotBlank(message = "주문ID는 필수입니다")
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
                message = "올바른 UUID 형식이어야 합니다")
        private String orderId;
        
        @NotNull(message = "결제 금액은 필수입니다")
        @Min(value = 1000, message = "최소 결제 금액은 1000원입니다")
        @Max(value = 10000000, message = "최대 결제 금액은 10,000,000원입니다")
        private Integer amount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentCancelRequestDTO {
        @NotBlank(message = "취소 사유는 필수입니다")
        @Size(min = 2, max = 500, message = "취소 사유는 2자 이상 500자 이하여야 합니다")
        private String cancelReason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentConfirmFromFrontendRequestDTO {
        @NotBlank(message = "주문ID는 필수입니다")
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", 
                message = "올바른 UUID 형식이어야 합니다")
        private String orderId;
        
        @NotNull(message = "결제 금액은 필수입니다")
        @Min(value = 1000, message = "최소 결제 금액은 1000원입니다")
        @Max(value = 10000000, message = "최대 결제 금액은 10,000,000원입니다")
        private Integer amount;
    }


    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TossApproveRequest{
        @NotBlank(message = "결제키는 필수입니다")
        @Size(max = 200, message = "결제키는 200자를 초과할 수 없습니다")
        private String paymentKey;
        
        @NotBlank(message = "주문ID는 필수입니다")
        @Pattern(regexp = "^[0-9a-zA-Z\\-_]{6,64}$", 
                message = "주문ID는 영문 대소문자, 숫자, 특수문자 -, _로 이루어진 6자 이상 64자 이하의 문자열이어야 합니다")
        private String orderId;
        
        @NotNull(message = "결제 금액은 필수입니다")
        @Min(value = 1000, message = "최소 결제 금액은 1000원입니다")
        @Max(value = 10000000, message = "최대 결제 금액은 10,000,000원입니다")
        private Integer amount;
    }
}
