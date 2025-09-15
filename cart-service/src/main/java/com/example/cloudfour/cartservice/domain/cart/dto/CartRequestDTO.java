package com.example.cloudfour.cartservice.domain.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.UUID;

public class CartRequestDTO {
    @Getter
    @Builder
    @Jacksonized
    public static class CartCreateRequestDTO {
        
        @NotNull(message = "스토어 ID는 필수입니다")
        private UUID storeId;
        
        @NotNull(message = "메뉴 ID는 필수입니다")
        private UUID menuId;
        
        @Size(max = 10, message = "메뉴 옵션은 최대 10개까지 선택할 수 있습니다")
        private List<UUID> menuOptionIds;
    }
}
