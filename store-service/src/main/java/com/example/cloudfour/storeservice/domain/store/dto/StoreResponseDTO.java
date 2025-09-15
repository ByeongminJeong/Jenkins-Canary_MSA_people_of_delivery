package com.example.cloudfour.storeservice.domain.store.dto;

import com.example.cloudfour.storeservice.domain.menu.dto.MenuResponseDTO;
import com.example.cloudfour.storeservice.domain.review.dto.ReviewResponseDTO;
import com.example.cloudfour.storeservice.domain.store.controller.StoreCommonResponseDTO;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class StoreResponseDTO {

    @Getter
    @SuperBuilder
    public static abstract class StoreBaseResponseDTO {
        @JsonUnwrapped
        StoreCommonResponseDTO.StoreCommonsBaseResponseDTO storeCommonsBaseResponseDTO;
    }

    @Getter
    @SuperBuilder
    public static class StoreCreateResponseDTO extends StoreBaseResponseDTO {
        @JsonUnwrapped
        StoreCommonResponseDTO.StoreCommonMainResponseDTO storeCommonMainResponseDTO;
        private LocalDateTime createdAt;
        private UUID createdBy;
    }

    @Getter
    @SuperBuilder
    public static class StoreUpdateResponseDTO extends StoreBaseResponseDTO {
        private String category;
        private LocalDateTime updatedAt;
    }

    @Getter
    @SuperBuilder
    public static class StoreListResponseDTO extends StoreBaseResponseDTO {
        @JsonUnwrapped
        StoreCommonResponseDTO.StoreCommonOptionResponseDTO storeCommonOptionResponseDTO;
        private LocalDateTime createdAt;
    }

    @Getter
    @SuperBuilder
    public static class StoreCursorListResponseDTO {
        private List<StoreListResponseDTO> storeList;
        private LocalDateTime nextCursor;

        public static StoreCursorListResponseDTO of(List<StoreListResponseDTO> storeList, LocalDateTime nextCursor) {
            return StoreCursorListResponseDTO.builder()
                    .storeList(storeList)
                    .nextCursor(nextCursor)
                    .build();
        }
    }

    @Getter
    @SuperBuilder
    public static class StoreDetailResponseDTO extends StoreBaseResponseDTO {
        UUID userId;
        @JsonUnwrapped
        StoreCommonResponseDTO.StoreCommonMainResponseDTO storeCommonMainResponseDTO;
        @JsonUnwrapped
        StoreCommonResponseDTO.StoreCommonOptionResponseDTO storeCommonOptionResponseDTO;
        List<ReviewResponseDTO.ReviewDocumentResponseDTO> reviewUserResponseDTOS;
        List<MenuResponseDTO.MenuListResponseDTO> menuListResponseDTOS;

    }
}
