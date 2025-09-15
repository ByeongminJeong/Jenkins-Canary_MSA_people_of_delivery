package com.example.cloudfour.cartservice.domain.cart.converter;

import com.example.cloudfour.cartservice.domain.cart.controller.CartCommonResponseDTO;
import com.example.cloudfour.cartservice.domain.cart.dto.CartResponseDTO;
import com.example.cloudfour.cartservice.domain.cart.entity.Cart;
import com.example.cloudfour.cartservice.domain.cartitem.converter.CartItemConverter;
import com.example.cloudfour.cartservice.domain.cartitem.dto.CartItemResponseDTO;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CartConverter {

    private CartConverter() {
        throw new UnsupportedOperationException("유틸리티 클래스를 인스턴스화할 수 없습니다");
    }

    public static CartResponseDTO.CartDetailResponseDTO toCartDetailResponseDTO(Cart cart) {
        validateCartNotNull(cart, "toCartDetailResponseDTO");
        
        List<CartItemResponseDTO.CartItemListResponseDTO> cartItemDtos = 
            convertCartItemsToDto(cart);

        return CartResponseDTO.CartDetailResponseDTO.builder()
                .cartCommonResponseDTO(toCartCommonResponseDTO(cart))
                .cartItems(cartItemDtos)
                .build();
    }

    public static CartResponseDTO.CartCreateResponseDTO toCartCreateResponseDTO(Cart cart, UUID cartItemId) {
        validateCartNotNull(cart, "toCartCreateResponseDTO");
        validateCartItemIdNotNull(cartItemId);

        return CartResponseDTO.CartCreateResponseDTO.builder()
                .cartCommonResponseDTO(toCartCommonResponseDTO(cart))
                .cartItemId(cartItemId)
                .createdAt(cart.getCreatedAt())
                .build();
    }

    public static CartCommonResponseDTO toCartCommonResponseDTO(Cart cart) {
        validateCartNotNull(cart, "toCartCommonResponseDTO");

        return CartCommonResponseDTO.builder()
                .cartId(cart.getId())
                .userId(cart.getUser())
                .storeId(cart.getStore())
                .build();
    }

    private static void validateCartNotNull(Cart cart, String methodName) {
        if (cart == null) {
            throw new IllegalArgumentException(
                String.format("카트는 %s 단위로 null일 수 없습니다", methodName)
            );
        }
    }

    private static void validateCartItemIdNotNull(UUID cartItemId) {
        if (cartItemId == null) {
            throw new IllegalArgumentException("CartItem ID는 null일 수 없습니다");
        }
    }

    private static List<CartItemResponseDTO.CartItemListResponseDTO> convertCartItemsToDto(Cart cart) {
        if (cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            return Collections.emptyList();
        }

        return cart.getCartItems().stream()
                .map(CartItemConverter::toCartItemListResponseDTO)
                .toList();
    }

    public static Cart createEmptyCart() {
        return Cart.builder().build();
    }
}
