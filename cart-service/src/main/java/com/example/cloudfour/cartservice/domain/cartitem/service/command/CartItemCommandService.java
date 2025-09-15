package com.example.cloudfour.cartservice.domain.cartitem.service.command;

import com.example.cloudfour.cartservice.client.StoreClient;
import com.example.cloudfour.cartservice.domain.cart.entity.Cart;
import com.example.cloudfour.cartservice.domain.cart.exception.CartErrorCode;
import com.example.cloudfour.cartservice.domain.cart.exception.CartException;
import com.example.cloudfour.cartservice.domain.cart.repository.CartRepository;
import com.example.cloudfour.cartservice.domain.cartitem.converter.CartItemConverter;
import com.example.cloudfour.cartservice.domain.cartitem.dto.CartItemRequestDTO;
import com.example.cloudfour.cartservice.domain.cartitem.dto.CartItemResponseDTO;
import com.example.cloudfour.cartservice.domain.cartitem.entity.CartItem;
import com.example.cloudfour.cartservice.domain.cartitem.entity.CartItemOption;
import com.example.cloudfour.cartservice.domain.cartitem.exception.CartItemErrorCode;
import com.example.cloudfour.cartservice.domain.cartitem.exception.CartItemException;
import com.example.cloudfour.cartservice.domain.cartitem.repository.CartItemRepository;
import com.example.cloudfour.cartservice.commondto.MenuOptionResponseDTO;
import com.example.cloudfour.cartservice.commondto.MenuResponseDTO;
import com.example.cloudfour.modulecommon.dto.Passport;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CartItemCommandService {

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final StoreClient storeClient;

    public CartItemResponseDTO.CartItemAddResponseDTO CreateCartItem(
            CartItemRequestDTO.CartItemAddRequestDTO req,
            UUID cartId,
            Passport passport
    ) {
        if (passport == null) {
            log.warn("장바구니 아이템 추가 권한 없음");
            throw new CartItemException(CartItemErrorCode.UNAUTHORIZED_ACCESS);
        }

        Cart cart = cartRepository.findByIdAndUser(cartId, passport.getUserId())
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 장바구니");
                    return new CartException(CartErrorCode.NOT_FOUND);
                });

        if (!storeClient.existMenu(req.getMenuId())) {
            throw new CartException(CartErrorCode.MENU_NOT_FOUND);
        }

        MenuResponseDTO menu = storeClient.menuById(req.getMenuId());

        List<UUID> selectedOptionIds = dedup(req.getMenuOptionIds());
        List<MenuOptionResponseDTO> options = fetchOptionsOnce(selectedOptionIds);

        validateOptionsBelongToMenu(options, menu.getMenuId());

        int unitPrice = calcUnitPrice(menu.getPrice(), options);
        int quantity = 1;
        int totalPrice = unitPrice * quantity;

        List<CartItem> existing = cartItemRepository.findByCartIdAndMenuId(cartId, menu.getMenuId());
        for (CartItem e : existing) {
            if (isSameOptions(e.getOptions(), options)) {
                int newQty = e.getQuantity() + quantity;
                int newTotal = unitPrice * newQty;
                e.update(newQty, newTotal);
                cartItemRepository.save(e);
                log.info("기존 장바구니 아이템 수량 증가 (cartItemId={}, {} -> {})", e.getId(), newQty - quantity, newQty);
                return CartItemConverter.toCartItemAddResponseDTO(e);
            }
        }

        CartItem item = CartItemConverter.createCartItem(quantity, totalPrice);
        item.setCart(cart);
        item.setMenu(menu.getMenuId());
        attachOptions(item, options);

        cartItemRepository.save(item);
        log.info("새로운 장바구니 아이템 추가 (cartId={}, itemId={})", cart.getId(), item.getId());

        return cartItemRepository.findByIdWithOptions(item.getId())
                .map(CartItemConverter::toCartItemAddResponseDTO)
                .orElseGet(() -> CartItemConverter.toCartItemAddResponseDTO(item));
    }

    public CartItemResponseDTO.CartItemAddResponseDTO AddCartItem(
            CartItemRequestDTO.CartItemAddRequestDTO req,
            UUID cartId,
            Passport passport
    ) {
        if (passport == null) {
            log.warn("장바구니 아이템 생성 권한 없음");
            throw new CartItemException(CartItemErrorCode.UNAUTHORIZED_ACCESS);
        }

        Cart cart = cartRepository.findByIdAndUser(cartId, passport.getUserId())
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 장바구니");
                    return new CartException(CartErrorCode.NOT_FOUND);
                });

        MenuResponseDTO menu = storeClient.menuById(req.getMenuId());
        log.info("메뉴 데이터: {}", menu.getMenuId());

        List<UUID> selectedOptionIds = dedup(req.getMenuOptionIds());
        List<MenuOptionResponseDTO> options = fetchOptionsOnce(selectedOptionIds);

        validateOptionsBelongToMenu(options, menu.getMenuId());

        int quantity = 1;
        if (quantity <= 0) {
            log.warn("수량이 0 이하: {}", quantity);
            throw new CartItemException(CartItemErrorCode.INVALID_QUANTITY);
        }

        int unitPrice = calcUnitPrice(menu.getPrice(), options);
        int totalPrice = unitPrice * quantity;

        List<CartItem> existing = cartItemRepository.findByCartIdAndMenuId(cartId, menu.getMenuId());
        for (CartItem e : existing) {
            if (isSameOptions(e.getOptions(), options)) {
                int newQty = e.getQuantity() + quantity;
                int newTotal = unitPrice * newQty;
                e.update(newQty, newTotal);
                cartItemRepository.save(e);
                log.info("기존 장바구니 아이템 수량 증가 (cartItemId={}, {} -> {})", e.getId(), newQty - quantity, newQty);
                return CartItemConverter.toCartItemAddResponseDTO(e);
            }
        }

        CartItem item = CartItemConverter.createCartItem(quantity, totalPrice);
        item.setCart(cart);
        item.setMenu(menu.getMenuId());
        attachOptions(item, options);

        cartItemRepository.save(item);
        log.info("새로운 장바구니 아이템 생성 완료 (cartId={}, itemId={})", cart.getId(), item.getId());

        return cartItemRepository.findByIdWithOptions(item.getId())
                .map(CartItemConverter::toCartItemAddResponseDTO)
                .orElseGet(() -> CartItemConverter.toCartItemAddResponseDTO(item));
    }

    public CartItemResponseDTO.CartItemUpdateResponseDTO updateCartItem(
            CartItemRequestDTO.CartItemUpdateRequestDTO req,
            UUID cartItemId,
            Passport passport
    ) {
        CartItem cartItem = cartItemRepository.findById(cartItemId).orElseThrow(() -> {
            log.warn("존재하지 않는 장바구니 아이템");
            return new CartItemException(CartItemErrorCode.NOT_FOUND);
        });

        if (passport == null || !cartItemRepository.existsByCartItemAndUser(cartItemId, passport.getUserId())) {
            log.warn("장바구니 아이템 수정 권한 없음");
            throw new CartItemException(CartItemErrorCode.UNAUTHORIZED_ACCESS);
        }

        List<UUID> selectedOptionIds = dedup(req.getMenuOptionIds());
        List<MenuOptionResponseDTO> options = fetchOptionsOnce(selectedOptionIds);

        MenuResponseDTO menu = storeClient.menuById(cartItem.getMenu());

        Integer quantity = req.getQuantity();
        if (quantity == null || quantity <= 0) {
            log.warn("수량이 0 이하: {}", quantity);
            throw new CartItemException(CartItemErrorCode.INVALID_QUANTITY);
        }

        if (!options.isEmpty()) {
            validateOptionsBelongToMenu(options, menu.getMenuId());
        }

        int unitPrice = calcUnitPrice(menu.getPrice(), options);
        int totalPrice = unitPrice * quantity;

        cartItem.update(quantity, totalPrice);

        int before = cartItem.getOptions().size();
        cartItem.getOptions().clear();
        attachOptions(cartItem, options);
        log.info("옵션 교체: {} -> {}", before, cartItem.getOptions().size());

        cartItemRepository.save(cartItem);

        CartItem loaded = cartItemRepository.findByIdWithOptions(cartItemId)
                .orElseThrow(() -> new CartItemException(CartItemErrorCode.NOT_FOUND));
        return CartItemConverter.toCartItemUpdateResponseDTO(loaded);
    }

    public void deleteCartItem(UUID cartItemId, Passport passport) {
        CartItem cartItem = cartItemRepository.findById(cartItemId).orElseThrow(() -> {
            log.warn("존재하지 않는 장바구니 아이템");
            return new CartItemException(CartItemErrorCode.NOT_FOUND);
        });

        Cart cart = cartRepository.findById(cartItem.getCart().getId()).orElseThrow(() -> {
            log.warn("존재하지 않는 장바구니");
            return new CartException(CartErrorCode.NOT_FOUND);
        });

        if (passport == null || !cartItemRepository.existsByCartItemAndUser(cartItemId, passport.getUserId())) {
            log.warn("장바구니 아이템 삭제 권한 없음");
            throw new CartItemException(CartItemErrorCode.UNAUTHORIZED_ACCESS);
        }

        cartItemRepository.delete(cartItem);
        cartItemRepository.flush();

        if (cart.getCartItems().isEmpty()) {
            cartRepository.delete(cart);
        }
        log.info("장바구니 아이템 삭제 완료");
    }


    private List<UUID> dedup(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private List<MenuOptionResponseDTO> fetchOptionsOnce(List<UUID> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) return List.of();
        return storeClient.menuOptionsByIds(optionIds);
    }

    private void validateOptionsBelongToMenu(List<MenuOptionResponseDTO> options, UUID menuId) {
        boolean invalid = options.stream().anyMatch(o -> !menuId.equals(o.getMenuId()));
        if (invalid) {
            log.warn("요청한 옵션 중 메뉴와 소속이 다른 옵션 존재");
            throw new CartItemException(CartItemErrorCode.INVALID_OPTION);
        }
    }

    private int calcUnitPrice(int basePrice, List<MenuOptionResponseDTO> options) {
        int additional = options.stream().mapToInt(MenuOptionResponseDTO::getAdditionalPrice).sum();
        return basePrice + additional;
    }

    private void attachOptions(CartItem item, List<MenuOptionResponseDTO> options) {
        if (options == null || options.isEmpty()) return;
        for (MenuOptionResponseDTO o : options) {
            CartItemOption opt = CartItemConverter.createCartItemOption(
                    o.getMenuOptionId(),
                    o.getAdditionalPrice(),
                    o.getOptionName()
            );
            item.addOption(opt);
        }
    }

    private boolean isSameOptions(List<CartItemOption> existingOptions, List<MenuOptionResponseDTO> newOptions) {
        if (existingOptions == null) return newOptions == null || newOptions.isEmpty();
        if (newOptions == null) return existingOptions.isEmpty();
        if (existingOptions.size() != newOptions.size()) return false;

        List<UUID> a = existingOptions.stream()
                .map(CartItemOption::getMenuOptionId)
                .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
                .toList();

        List<UUID> b = newOptions.stream()
                .map(MenuOptionResponseDTO::getMenuOptionId)
                .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
                .toList();

        return a.equals(b);
    }
}
