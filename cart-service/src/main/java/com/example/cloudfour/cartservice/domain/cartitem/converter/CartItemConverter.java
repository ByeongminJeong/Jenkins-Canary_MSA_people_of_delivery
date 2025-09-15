package com.example.cloudfour.cartservice.domain.cartitem.converter;

import com.example.cloudfour.cartservice.domain.cart.dto.CartRequestDTO;
import com.example.cloudfour.cartservice.domain.cartitem.controller.CartItemCommonResponseDTO;
import com.example.cloudfour.cartservice.domain.cartitem.dto.CartItemRequestDTO;
import com.example.cloudfour.cartservice.domain.cartitem.dto.CartItemResponseDTO;
import com.example.cloudfour.cartservice.domain.cartitem.entity.CartItem;
import com.example.cloudfour.cartservice.domain.cartitem.entity.CartItemOption;

import java.util.Collections;
import java.util.List;
import java.util.UUID;


public final class CartItemConverter {

    private CartItemConverter() {
        throw new UnsupportedOperationException("유틸리티 클래스를 인스턴스화할 수 없습니다.");
    }

    public static CartItemResponseDTO.CartItemAddResponseDTO toCartItemAddResponseDTO(CartItem cartItem) {
        validateCartItemNotNull(cartItem, "toCartItemAddResponseDTO");

        return CartItemResponseDTO.CartItemAddResponseDTO.builder()
                .menuId(cartItem.getMenu())
                .cartItemCommonResponseDTO(toCartItemCommonResponseDTO(cartItem))
                .build();
    }

    public static CartItemResponseDTO.CartItemUpdateResponseDTO toCartItemUpdateResponseDTO(CartItem cartItem) {
        validateCartItemNotNull(cartItem, "toCartItemUpdateResponseDTO");

        return CartItemResponseDTO.CartItemUpdateResponseDTO.builder()
                .cartItemCommonResponseDTO(toCartItemCommonResponseDTO(cartItem))
                .build();
    }

    public static CartItemResponseDTO.CartItemListResponseDTO toCartItemListResponseDTO(CartItem cartItem) {
        validateCartItemNotNull(cartItem, "toCartItemListResponseDTO");

        return CartItemResponseDTO.CartItemListResponseDTO.builder()
                .menuId(cartItem.getMenu())
                .cartItemCommonResponseDTO(toCartItemCommonResponseDTO(cartItem))
                .build();
    }

    public static CartItemRequestDTO.CartItemAddRequestDTO toCartItemAddRequestDTO(
            CartRequestDTO.CartCreateRequestDTO cartCreateRequestDTO, 
            int price
    ) {
        if (cartCreateRequestDTO == null) {
            throw new IllegalArgumentException("CartCreateRequestDTO cannot be null");
        }

        List<UUID> menuOptionIds = safeGetMenuOptionIds(cartCreateRequestDTO.getMenuOptionIds());
        
        return CartItemRequestDTO.CartItemAddRequestDTO.builder()
                .menuId(cartCreateRequestDTO.getMenuId())
                .menuOptionIds(menuOptionIds)
                .build();
    }

    public static CartItemCommonResponseDTO toCartItemCommonResponseDTO(CartItem cartItem) {
        validateCartItemNotNull(cartItem, "toCartItemCommonResponseDTO");
        validateCartItemHasCart(cartItem);

        List<CartItemCommonResponseDTO.MenuOptionDto> menuOptions = 
            convertOptionsToDto(cartItem.getOptions());
        
        return CartItemCommonResponseDTO.builder()
                .cartItemId(cartItem.getId())
                .cartId(cartItem.getCart().getId())
                .menuOptions(menuOptions)
                .quantity(cartItem.getQuantity())
                .price(cartItem.getPrice())
                .build();
    }

    private static void validateCartItemNotNull(CartItem cartItem, String methodName) {
        if (cartItem == null) {
            throw new IllegalArgumentException(
                String.format("CartItem은 %s에서 null일 수 없습니다", methodName)
            );
        }
    }

    private static void validateCartItemHasCart(CartItem cartItem) {
        if (cartItem.getCart() == null) {
            throw new IllegalArgumentException("CartItem에는 관련된 Cart가 있어야 합니다");
        }
    }

    private static List<UUID> safeGetMenuOptionIds(List<UUID> menuOptionIds) {
        return menuOptionIds != null ? menuOptionIds : Collections.emptyList();
    }

    private static List<CartItemCommonResponseDTO.MenuOptionDto> convertOptionsToDto(
            List<CartItemOption> options
    ) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }

        return options.stream()
                .map(CartItemConverter::convertOptionToDto)
                .toList();
    }

    private static CartItemCommonResponseDTO.MenuOptionDto convertOptionToDto(CartItemOption option) {
        if (option == null) {
            return null;
        }

        return CartItemCommonResponseDTO.MenuOptionDto.builder()
                .id(option.getMenuOptionId())
                .additionalPrice(option.getAdditionalPrice())
                .optionName(option.getOptionName())
                .build();
    }

    public static CartItem createCartItem(int quantity, int totalPrice) {
        return CartItem.builder()
                .quantity(quantity)
                .price(totalPrice)
                .build();
    }

    public static CartItemOption createCartItemOption(UUID menuOptionId, int additionalPrice, String optionName) {
        return CartItemOption.builder()
                .menuOptionId(menuOptionId)
                .additionalPrice(additionalPrice)
                .optionName(optionName)
                .build();
    }
}