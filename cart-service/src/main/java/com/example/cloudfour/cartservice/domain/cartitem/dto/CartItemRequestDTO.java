package com.example.cloudfour.cartservice.domain.cartitem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.UUID;

public class CartItemRequestDTO {
    
    @Getter
    @Builder
    @Jacksonized
    public static class CartItemCreateRequestDTO {
        
        @NotNull(message = "메뉴 ID는 필수입니다")
        private UUID menuId;
        
        @Size(max = 10, message = "메뉴 옵션은 최대 10개까지 선택할 수 있습니다")
        private List<UUID> menuOptionIds;
        
        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        @Max(value = 99, message = "수량은 99개 이하여야 합니다")
        private Integer quantity;
        
        @NotNull(message = "가격은 필수입니다")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다")
        private Integer price;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class CartItemAddRequestDTO {
        
        @NotNull(message = "메뉴 ID는 필수입니다")
        private UUID menuId;
        
        @Size(max = 10, message = "메뉴 옵션은 최대 10개까지 선택할 수 있습니다")
        private List<UUID> menuOptionIds;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class CartItemUpdateRequestDTO {
        
        @Size(max = 10, message = "메뉴 옵션은 최대 10개까지 선택할 수 있습니다")
        private List<UUID> menuOptionIds;
        
        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        @Max(value = 99, message = "수량은 99개 이하여야 합니다")
        private Integer quantity;
    }
}
