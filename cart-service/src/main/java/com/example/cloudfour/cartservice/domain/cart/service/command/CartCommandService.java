package com.example.cloudfour.cartservice.domain.cart.service.command;

import com.example.cloudfour.cartservice.client.StoreClient;
import com.example.cloudfour.cartservice.domain.cart.converter.CartConverter;
import com.example.cloudfour.cartservice.domain.cart.dto.CartRequestDTO;
import com.example.cloudfour.cartservice.domain.cart.dto.CartResponseDTO;
import com.example.cloudfour.cartservice.domain.cart.entity.Cart;
import com.example.cloudfour.cartservice.domain.cart.exception.CartErrorCode;
import com.example.cloudfour.cartservice.domain.cart.exception.CartException;
import com.example.cloudfour.cartservice.domain.cart.repository.CartRepository;
import com.example.cloudfour.cartservice.domain.cartitem.converter.CartItemConverter;
import com.example.cloudfour.cartservice.domain.cartitem.dto.CartItemRequestDTO;
import com.example.cloudfour.cartservice.domain.cartitem.dto.CartItemResponseDTO;
import com.example.cloudfour.cartservice.domain.cartitem.service.command.CartItemCommandService;
import com.example.cloudfour.cartservice.commondto.MenuResponseDTO;
import com.example.cloudfour.modulecommon.dto.Passport;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CartCommandService {

    private final CartRepository cartRepository;
    private final CartItemCommandService cartItemCommandService;
    private final StoreClient storeClient;

    public CartResponseDTO.CartCreateResponseDTO createCart(
            CartRequestDTO.CartCreateRequestDTO req, 
            Passport passport
    ) {
        validateUser(passport);
        validateStoreExists(req.getStoreId());
        validateNoDuplicateCart(passport.getUserId(), req.getStoreId());

        Cart cart = createCartEntity(passport.getUserId(), req.getStoreId());
        Cart savedCart = cartRepository.save(cart);

        MenuResponseDTO menu = storeClient.menuById(req.getMenuId());
        CartItemRequestDTO.CartItemAddRequestDTO cartItemReq = 
            CartItemConverter.toCartItemAddRequestDTO(req, menu.getPrice());
        
        CartItemResponseDTO.CartItemAddResponseDTO cartItemResponse = 
            cartItemCommandService.CreateCartItem(cartItemReq, savedCart.getId(), passport);

        log.info("장바구니 생성 완료 (cartId={}, cartItemId={})", 
            savedCart.getId(), cartItemResponse.getCartItemCommonResponseDTO().getCartItemId());
        
        return CartConverter.toCartCreateResponseDTO(
            savedCart, 
            cartItemResponse.getCartItemCommonResponseDTO().getCartItemId()
        );
    }

    @CacheEvict(value = {"stores", "menus", "menuOptions"}, allEntries = true)
    public void deleteCart(UUID cartId, Passport passport) {
        validateUser(passport);
        
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 장바구니: {}", cartId);
                    return new CartException(CartErrorCode.NOT_FOUND);
                });

        validateCartOwnership(cart, passport.getUserId());
        
        cartRepository.delete(cart);
        log.info("장바구니 삭제 완료 (cartId={})", cartId);
    }

    private void validateUser(Passport passport) {
        if (passport == null) {
            log.warn("유효하지 않은 사용자");
            throw new CartException(CartErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private void validateStoreExists(UUID storeId) {
        if (storeId == null) {
            log.warn("Store ID가 null입니다");
            throw new CartException(CartErrorCode.STORE_NOT_FOUND);
        }
        
        if (!storeClient.existStore(storeId)) {
            log.warn("존재하지 않는 스토어: {}", storeId);
            throw new CartException(CartErrorCode.STORE_NOT_FOUND);
        }
    }

    private void validateNoDuplicateCart(UUID userId, UUID storeId) {
        if (cartRepository.existsByUserAndStore(userId, storeId)) {
            log.warn("이미 존재하는 장바구니 (userId={}, storeId={})", userId, storeId);
            throw new CartException(CartErrorCode.ALREADY_ADD);
        }
    }

    private Cart createCartEntity(UUID userId, UUID storeId) {
        Cart cart = CartConverter.createEmptyCart();
        cart.setUser(userId);
        cart.setStore(storeId);
        return cart;
    }

    private void validateCartOwnership(Cart cart, UUID userId) {
        if (!userId.equals(cart.getUser())) {
            log.warn("장바구니 접근 권한 없음 (cartId={}, userId={}, ownerId={})", 
                cart.getId(), userId, cart.getUser());
            throw new CartException(CartErrorCode.UNAUTHORIZED_ACCESS);
        }
    }
}
