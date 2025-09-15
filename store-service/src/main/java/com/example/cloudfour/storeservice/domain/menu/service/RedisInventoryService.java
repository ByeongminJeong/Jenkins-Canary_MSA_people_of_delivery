package com.example.cloudfour.storeservice.domain.menu.service;

import com.example.cloudfour.storeservice.domain.menu.converter.StockConverter;
import com.example.cloudfour.storeservice.domain.menu.dto.StockResponseDTO;
import com.example.cloudfour.storeservice.domain.menu.entity.Menu;
import com.example.cloudfour.storeservice.domain.menu.entity.Stock;
import com.example.cloudfour.storeservice.domain.menu.exception.MenuException;
import com.example.cloudfour.storeservice.domain.menu.exception.MenuErrorCode;
import com.example.cloudfour.storeservice.domain.menu.exception.StockException;
import com.example.cloudfour.storeservice.domain.menu.exception.StockErrorCode;
import com.example.cloudfour.storeservice.domain.menu.repository.MenuRepository;
import com.example.cloudfour.storeservice.domain.menu.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class RedisInventoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final MenuRepository menuRepository;
    private final StockRepository stockRepository;
    private final DefaultRedisScript<List> reserveStockScript;
    private final DefaultRedisScript<List> releaseStockScript;
    private final DefaultRedisScript<List> availabilityScript;

    private static final String STOCK_KEY_PREFIX = "invn:menu:";
    private static final String RESERVATION_KEY_PREFIX = "rsrv:menu:";
    private static final String ORDER_RESERVATION_KEY_PREFIX = "order:rsrv:";

    public RedisInventoryService(RedisTemplate<String, String> redisTemplate, 
                               MenuRepository menuRepository, 
                               StockRepository stockRepository) {
        this.redisTemplate = redisTemplate;
        this.menuRepository = menuRepository;
        this.stockRepository = stockRepository;
        this.reserveStockScript = new DefaultRedisScript<>();
        this.reserveStockScript.setScriptText(getReserveStockScript());
        this.reserveStockScript.setResultType(List.class);

        this.releaseStockScript = new DefaultRedisScript<>();
        this.releaseStockScript.setScriptText(getReleaseStockScript());
        this.releaseStockScript.setResultType(List.class);

        this.availabilityScript = new DefaultRedisScript<>();
        this.availabilityScript.setScriptText(getAvailabilityScript());
        this.availabilityScript.setResultType(List.class);
    }

    public StockResponseDTO.StockReserveResponseDTO reserveStock(UUID menuId, Long quantity, UUID orderId) {
        String stockKey = STOCK_KEY_PREFIX + menuId;
        String reservationKey = RESERVATION_KEY_PREFIX + menuId;
        String orderReservationKey = ORDER_RESERVATION_KEY_PREFIX + orderId;

        if (!redisTemplate.hasKey(stockKey)) {
            ensureStockInRedis(menuId);
        }

        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redisTemplate.execute(
                    reserveStockScript,
                    Arrays.asList(stockKey, reservationKey, orderReservationKey),
                    String.valueOf(quantity),
                    orderId.toString(),
                    String.valueOf(System.currentTimeMillis() + 300000)
            );

            int code = ((Number) result.get(0)).intValue();
            String message = (String) result.get(1);

            switch (code) {
                case 0:
                    Long remainingStock = ((Number) result.get(2)).longValue();
                    log.info("재고 예약 성공 - MenuId: {}, Quantity: {}, Remaining: {}, OrderId: {}",
                            menuId, quantity, remainingStock, orderId);
                    return StockConverter.success(remainingStock);

                case -1:
                    log.warn("메뉴를 찾을 수 없음 - MenuId: {}", menuId);
                    return StockConverter.menuNotFound();

                case -2:
                    Long currentStock = ((Number) result.get(2)).longValue();
                    log.warn("재고 부족 - MenuId: {}, Required: {}, Available: {}",
                            menuId, quantity, currentStock);
                    return StockConverter.insufficientStock(currentStock);

                default:
                    log.error("Internal Error - Code: {}, Message: {}", code, message);
                    return StockConverter.error(message);
            }
        } catch (Exception e) {
            log.error("Redis 재고 예약 중 오류 발생 - MenuId: {}, OrderId: {}", menuId, orderId, e);
            return StockConverter.error("Redis operation failed");
        }
    }

    public void releaseReservation(UUID orderId) {
        String orderReservationKey = ORDER_RESERVATION_KEY_PREFIX + orderId;

        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redisTemplate.execute(
                    releaseStockScript,
                    Arrays.asList(orderReservationKey),
                    orderId.toString()
            );

            int code = ((Number) result.get(0)).intValue();
            if (code == 0) {
                log.info("재고 예약 해제 성공 - OrderId: {}", orderId);
            } else {
                log.error("재고 예약 해제 실패 - OrderId: {}, Code: {}", orderId, code);
            }
        } catch (Exception e) {
            log.error("예약 해제 중 오류 발생 - OrderId: {}", orderId, e);
        }
    }


    public StockResponseDTO.StockAvailabilityResponseDTO getAvailability(UUID menuId) {
        String stockKey = STOCK_KEY_PREFIX + menuId;
        String reservationKey = RESERVATION_KEY_PREFIX + menuId;
        try {
            @SuppressWarnings("unchecked")
            List<Object> result = redisTemplate.execute(
                    availabilityScript,
                    Arrays.asList(stockKey, reservationKey)
            );
            int code = ((Number) result.get(0)).intValue();
            if (code == -1) {
                log.warn("메뉴를 찾을 수 없음(가용 조회) - MenuId: {}", menuId);
                return StockResponseDTO.StockAvailabilityResponseDTO.builder()
                        .menuId(menuId)
                        .baseQuantity(null)
                        .availableQuantity(null)
                        .reservedQuantity(null)
                        .build();
            }
            Long available = ((Number) result.get(1)).longValue();
            Long base = ((Number) result.get(2)).longValue();
            Long reserved = ((Number) result.get(3)).longValue();
            return StockResponseDTO.StockAvailabilityResponseDTO.builder()
                    .menuId(menuId)
                    .baseQuantity(base)
                    .availableQuantity(available)
                    .reservedQuantity(reserved)
                    .build();
        } catch (Exception e) {
            log.error("Redis 가용 재고 조회 오류 - MenuId: {}", menuId, e);
            return StockResponseDTO.StockAvailabilityResponseDTO.builder()
                    .menuId(menuId)
                    .baseQuantity(null)
                    .availableQuantity(null)
                    .reservedQuantity(null)
                    .build();
        }
    }

    private String getReserveStockScript() {
        return """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local orderReservationKey = KEYS[3]
            local quantity = tonumber(ARGV[1])
            local orderId = ARGV[2]
            local expireTime = ARGV[3]
        
            -- 현재 재고 확인
            local currentStock = redis.call('GET', stockKey)
            if not currentStock then
                return {-1, "MENU_NOT_FOUND"}
            end
        
            currentStock = tonumber(currentStock)
        
            -- 현재 예약 수량 확인
            local reservedStock = 0
            local reservations = redis.call('HGETALL', reservationKey)
            for i = 2, #reservations, 2 do
                reservedStock = reservedStock + tonumber(reservations[i])
            end
        
            -- 사용 가능한 재고 계산
            local availableStock = currentStock - reservedStock
            
            if availableStock < quantity then
                return {-2, "INSUFFICIENT_STOCK", availableStock}
            end
            
            -- 메뉴별 예약에 주문 수량 누적
            redis.call('HINCRBY', reservationKey, orderId, quantity)
            redis.call('EXPIRE', reservationKey, 7200)
            
            -- menuId를 stockKey에서 추출
            local menuId = string.match(stockKey, 'invn:menu:(.+)')
            
            -- 주문별 아이템 해시에 메뉴ID를 필드로 수량 누적
            redis.call('HINCRBY', orderReservationKey, menuId, quantity)
            redis.call('HSET', orderReservationKey .. ':meta', 'expireTime', expireTime)
            redis.call('EXPIRE', orderReservationKey, 300)
            redis.call('EXPIRE', orderReservationKey .. ':meta', 300)
            
            local newAvailableStock = availableStock - quantity
            return {0, "SUCCESS", newAvailableStock}
        """;
    }

    private String getReleaseStockScript() {
        return """
            -- KEYS[1] = order:rsrv:<orderId>
            -- ARGV[1] = orderId
            local orderReservationKey = KEYS[1]
            local orderId = ARGV[1]
            
            local orderItems = redis.call('HGETALL', orderReservationKey)
            if #orderItems == 0 then
              return {0, "NO_RESERVATION"}
            end
            
            for i = 1, #orderItems, 2 do
              local menuId = orderItems[i]
              local qty = tonumber(orderItems[i+1])
              local reservationKey = 'rsrv:menu:' .. menuId
              local current = redis.call('HGET', reservationKey, orderId)
              if current then
                local remain = tonumber(current) - qty
                if remain > 0 then
                  redis.call('HSET', reservationKey, orderId, remain)
                else
                  redis.call('HDEL', reservationKey, orderId)
                end
              end
            end
            
            redis.call('DEL', orderReservationKey)
            return {0, "SUCCESS"}
        """;
    }

    private String getAvailabilityScript() {
        return """
            -- KEYS[1] = invn:menu:<menuId>
            -- KEYS[2] = rsrv:menu:<menuId>
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local current = redis.call('GET', stockKey)
            if not current then
                return {-1, 'MENU_NOT_FOUND'}
            end
            current = tonumber(current)
            local reserved = 0
            local entries = redis.call('HGETALL', reservationKey)
            for i = 2, #entries, 2 do
                reserved = reserved + tonumber(entries[i])
            end
            local available = current - reserved
            return {0, available, current, reserved}
        """;
    }

    private void ensureStockInRedis(UUID menuId) {
        try {
            log.info("Redis에 재고 정보가 없어서 DB에서 조회: menuId={}", menuId);
            
            Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new MenuException(MenuErrorCode.NOT_FOUND));
            
            Stock stock = stockRepository.findByIdWithOptimisticLock(menu.getStock().getId())
                .orElseThrow(() -> new StockException(StockErrorCode.NOT_FOUND));
            
            String stockKey = STOCK_KEY_PREFIX + menuId;
            redisTemplate.opsForValue().set(stockKey, String.valueOf(stock.getQuantity()));
            
            log.info("DB에서 조회한 재고 정보를 Redis에 저장: menuId={}, quantity={}", 
                    menuId, stock.getQuantity());
                    
        } catch (MenuException | StockException e) {
            log.error("재고 정보 조회 실패: menuId={}, error={}", menuId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Redis에 재고 정보 저장 실패: menuId={}, error={}", menuId, e.getMessage(), e);
            throw new StockException(StockErrorCode.INTERNAL_ERROR);
        }
    }
}