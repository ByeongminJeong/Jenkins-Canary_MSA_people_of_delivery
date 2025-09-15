package com.example.cloudfour.cartservice.domain.order.service.command;

import com.example.cloudfour.cartservice.client.StoreClient;
import com.example.cloudfour.cartservice.client.UserClient;
import com.example.cloudfour.cartservice.domain.cart.entity.Cart;
import com.example.cloudfour.cartservice.domain.cart.exception.CartErrorCode;
import com.example.cloudfour.cartservice.domain.cart.exception.CartException;
import com.example.cloudfour.cartservice.domain.cart.repository.CartRepository;
import com.example.cloudfour.cartservice.domain.cartitem.entity.CartItem;
import com.example.cloudfour.cartservice.domain.cartitem.exception.CartItemException;
import com.example.cloudfour.cartservice.domain.cartitem.exception.CartItemErrorCode;
import com.example.cloudfour.cartservice.commondto.UserAddressResponseDTO;
import com.example.cloudfour.cartservice.domain.order.converter.OrderConverter;
import com.example.cloudfour.cartservice.domain.order.converter.OrderItemConverter;
import com.example.cloudfour.cartservice.domain.order.dto.OrderRequestDTO;
import com.example.cloudfour.cartservice.domain.order.dto.OrderResponseDTO;
import com.example.cloudfour.cartservice.domain.order.entity.Order;
import com.example.cloudfour.cartservice.domain.order.entity.OrderItem;
import com.example.cloudfour.cartservice.domain.order.enums.OrderStatus;
import com.example.cloudfour.cartservice.domain.order.exception.OrderErrorCode;
import com.example.cloudfour.cartservice.domain.order.exception.OrderException;
import com.example.cloudfour.cartservice.domain.order.repository.OrderItemOptionRepository;
import com.example.cloudfour.cartservice.domain.order.repository.OrderItemRepository;
import com.example.cloudfour.cartservice.domain.order.repository.OrderRepository;
import com.example.cloudfour.cartservice.domain.order.service.event.OrderEventService;
import com.example.cloudfour.modulecommon.schedule.ScheduledTaskService;
import com.example.cloudfour.modulecommon.schedule.OrderTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import com.example.cloudfour.modulecommon.dto.Passport;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemOptionRepository orderItemOptionRepository;
    private final CartRepository cartRepository;
    private final StoreClient storeClient;
    private final UserClient userClient;
    private final OrderEventService orderEventService;
    private final ScheduledTaskService scheduledTaskService;
    private final OrderTimeoutHandler orderTimeoutHandler;
    
    @Value("${order.timeout.minutes:10}")
    private double orderTimeoutMinutes;


    public OrderResponseDTO.OrderCreateResponseDTO createOrder(
            OrderRequestDTO.OrderCreateRequestDTO req, 
            UUID cartId, 
            Passport passport
    ) {
        validateUser(passport);
        validateCartId(cartId);

        Cart cart = findCartWithOwnershipValidation(cartId, passport.getUserId());
        UserAddressResponseDTO userAddress = fetchUserAddress(passport.getUserId());
        validateStoreExists(cart.getStore());
        validateCartItemsNotEmpty(cart.getCartItems());

        int totalPrice = calculateTotalPrice(cart.getCartItems());
        
        Order order = createOrderEntity(req, totalPrice, userAddress.getAddress(), cart.getStore(), passport.getUserId());
        orderRepository.save(order);
        
        List<OrderItem> orderItems = createOrderItems(cart.getCartItems(), order);
        orderItemRepository.saveAll(orderItems);
        
        saveOrderItemOptions(orderItems);
        
        deleteCart(cart);

        orderEventService.publishOrderCreated(order);

        scheduleOrderTimeout(order.getId().toString());

        log.info("주문 생성 완료 (orderId={}, totalPrice={})", order.getId(), totalPrice);
        return OrderConverter.toOrderCreateResponseDTO(order);
    }

    private void scheduleOrderTimeout(String orderId) {
        try {
            scheduledTaskService.scheduleOrderTimeout(
                orderId, 
                orderTimeoutMinutes,
                () -> orderTimeoutHandler.handleOrderTimeout(orderId)
            );
            log.info("주문 타임아웃 스케줄 등록 완료: orderId={}", orderId);
        } catch (Exception e) {
            log.error("주문 타임아웃 스케줄 등록 실패: orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    public OrderResponseDTO.OrderUpdateResponseDTO updateOrder(
            OrderRequestDTO.OrderUpdateRequestDTO req, 
            UUID orderId,
            Passport passport
    ) {
        validateUser(passport);
        validateOrderId(orderId);
        validateOrderOwnership(orderId, passport.getUserId());

        Order order = findOrderById(orderId);
        OrderStatus prevStatus = order.getStatus();
        OrderStatus newStatus = req.getNewStatus();

        if (newStatus == OrderStatus.ORDER_CANCELED && prevStatus != OrderStatus.ORDER_CANCELED) {
            log.info("주문 취소 이벤트 발행: orderId={}", orderId);
            orderEventService.publishOrderCanceled(order, "사용자 요청에 의한 주문 취소");
        }
        
        order.updateOrderStatus(newStatus);
        orderRepository.save(order);
        
        log.info("주문 수정 완료 (orderId={}, status: {} -> {})", 
            orderId, prevStatus, order.getStatus());
        
        return OrderConverter.toOrderUpdateResponseDTO(order, prevStatus);
    }

    public void deleteOrder(UUID orderId, Passport passport) {
        validateUser(passport);
        validateOrderId(orderId);
        validateOrderOwnership(orderId, passport.getUserId());

        Order order = findOrderById(orderId);

        if (order.getStatus() != OrderStatus.ORDER_CANCELED) {
            log.info("주문 취소 이벤트 발행: orderId={}", orderId);
            orderEventService.publishOrderCanceled(order, "주문 삭제에 의한 주문 취소");
        }
        
        order.softDelete();
        
        log.info("주문 삭제 완료 (orderId={})", orderId);
    }


    private void validateUser(Passport passport) {
        if (passport == null || passport.getUserId() == null) {
            log.warn("유효하지 않은 사용자");
            throw new OrderException(OrderErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private void validateCartId(UUID cartId) {
        if (cartId == null) {
            log.warn("Cart ID가 null입니다");
            throw new OrderException(OrderErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private void validateOrderId(UUID orderId) {
        if (orderId == null) {
            log.warn("Order ID가 null입니다");
            throw new OrderException(OrderErrorCode.NOT_FOUND);
        }
    }

    private void validateOrderOwnership(UUID orderId, UUID userId) {
        if (!orderRepository.existsByOrderIdAndUserId(orderId, userId)) {
            log.warn("주문 접근 권한 없음 (orderId={}, userId={})", orderId, userId);
            throw new OrderException(OrderErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private Order findOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 주문: {}", orderId);
                    return new OrderException(OrderErrorCode.NOT_FOUND);
                });
    }

    private Cart findCartWithOwnershipValidation(UUID cartId, UUID userId) {
        Cart cart = cartRepository.findByIdAndUserWithCartItems(cartId, userId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 장바구니 또는 접근 권한 없음 (cartId={}, userId={})", cartId, userId);
                    return new CartException(CartErrorCode.NOT_FOUND);
                });

        if (!cartRepository.existsByUserAndCart(userId, cartId)) {
            log.warn("주문 생성 권한 없음 (cartId={}, userId={})", cartId, userId);
            throw new OrderException(OrderErrorCode.UNAUTHORIZED_ACCESS);
        }

        return cart;
    }

    private UserAddressResponseDTO fetchUserAddress(UUID userId) {
        try {
            return userClient.addressById(userId);
        } catch (Exception e) {
            log.error("사용자 주소 조회 실패 (userId={})", userId, e);
            throw new OrderException(OrderErrorCode.UNAUTHORIZED_ACCESS);
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

    private void validateCartItemsNotEmpty(List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            log.warn("장바구니에 아이템이 없습니다");
            throw new CartItemException(CartItemErrorCode.NOT_FOUND);
        }
    }

    private int calculateTotalPrice(List<CartItem> cartItems) {
        return cartItems.stream()
                .mapToInt(CartItem::getPrice)
                .sum();
    }

    private Order createOrderEntity(
            OrderRequestDTO.OrderCreateRequestDTO req, 
            int totalPrice, 
            String address, 
            UUID storeId, 
            UUID userId
    ) {
        Order order = OrderConverter.toOrder(req, totalPrice, address);
        order.setStore(storeId);
        order.setUser(userId);
        return order;
    }

    private List<OrderItem> createOrderItems(List<CartItem> cartItems, Order order) {
        return cartItems.stream()
                .map(cartItem -> OrderItemConverter.CartItemtoOrderItem(cartItem, order))
                .toList();
    }

    private void saveOrderItemOptions(List<OrderItem> orderItems) {
        orderItems.stream()
                .filter(orderItem -> orderItem.getOptions() != null && !orderItem.getOptions().isEmpty())
                .forEach(orderItem -> orderItemOptionRepository.saveAll(orderItem.getOptions()));
    }

    private void deleteCart(Cart cart) {
        try {
            cartRepository.delete(cart);
            log.debug("장바구니 삭제 완료 (cartId={})", cart.getId());
        } catch (Exception e) {
            log.error("장바구니 삭제 실패 (cartId={})", cart.getId(), e);
        }
    }
}
