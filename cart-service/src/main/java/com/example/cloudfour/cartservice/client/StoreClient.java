package com.example.cloudfour.cartservice.client;

import com.example.cloudfour.cartservice.commondto.MenuOptionResponseDTO;
import com.example.cloudfour.cartservice.commondto.MenuQuantityResponseDTO;
import com.example.cloudfour.cartservice.commondto.MenuResponseDTO;
import com.example.cloudfour.cartservice.commondto.StoreResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreClient {
    private final StoreFeignClient storeClient;

    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Boolean existStore(UUID storeId) {
        if (storeId == null) {
            log.warn("Store ID가 null입니다");
            return false;
        }

        try {
            storeClient.checkStoreExists(storeId);
            log.info("스토어 존재 확인 완료: {}", storeId);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            log.info("스토어가 존재하지 않음: {}", storeId);
            return false;
        } catch (Exception e) {
            log.error("스토어 존재 여부 확인 실패: {}", storeId, e);
            throw e;
        }
    }

    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Boolean existMenu(UUID menuId) {
        if (menuId == null) {
            log.warn("Menu ID가 null입니다");
            return false;
        }

        try {
            storeClient.checkMenuExists(menuId);
            log.info("메뉴 존재 확인 완료: {}", menuId);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            log.info("메뉴가 존재하지 않음: {}", menuId);
            return false;
        } catch (Exception e) {
            log.error("메뉴 존재 여부 확인 실패: {}", menuId, e);
            throw e;
        }
    }

    @Cacheable(value = "stores", key = "#storeId")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public StoreResponseDTO storeById(UUID storeId) {
        if (storeId == null) {
            log.warn("Store ID가 null입니다");
            return null;
        }

        try {
            StoreResponseDTO store = storeClient.storeById(storeId);
            log.info("스토어 정보 조회 완료: {}", storeId);
            return store;
        } catch (Exception e) {
            log.error("스토어 정보 조회 실패: {}", storeId, e);
            throw e;
        }
    }
    @Cacheable(value = "menus", key = "#menuId")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public MenuResponseDTO menuById(UUID menuId) {
        if (menuId == null) {
            log.warn("Menu ID가 null입니다");
            return null;
        }

        try {
            MenuResponseDTO menu = storeClient.menuById(menuId);
            log.info("메뉴 정보 조회 완료: {}", menuId);
            return menu;
        } catch (Exception e) {
            log.error("메뉴 정보 조회 실패: {}", menuId, e);
            throw e;
        }
    }

    @Cacheable(value = "menuOptions", key = "#menuOptionId")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public MenuOptionResponseDTO menuOptionById(UUID menuOptionId) {
        if (menuOptionId == null) {
            log.warn("Menu Option ID가 null입니다");
            return null;
        }

        try {
            MenuOptionResponseDTO option = storeClient.menuOptionById(menuOptionId);
            log.info("메뉴 옵션 정보 조회 완료: {}", menuOptionId);
            return option;
        } catch (Exception e) {
            log.error("메뉴 옵션 정보 조회 실패: {}", menuOptionId, e);
            throw e;
        }
    }

    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public MenuQuantityResponseDTO getMenuStock(UUID menuId) {
        if (menuId == null) {
            log.warn("Menu ID가 null입니다");
            return null;
        }

        try {
            log.debug("재고 조회 요청: menuId={}", menuId);
            MenuQuantityResponseDTO response = storeClient.getMenuStock(menuId);
            
            if (response != null) {
                log.info("메뉴 재고 정보 조회 완료: menuId={}, quantity={}", menuId, response.getQuantity());
                return response;
            }
            
            log.warn("재고 정보가 없습니다: menuId={}", menuId);
            return null;
        } catch (Exception e) {
            log.error("메뉴 재고 정보 조회 실패: {}", menuId, e);
            throw e;
        }
    }

    public List<MenuOptionResponseDTO> menuOptionsByIds(List<UUID> menuOptionIds) {
        if (menuOptionIds == null || menuOptionIds.isEmpty()) {
            return List.of();
        }

        log.debug("메뉴 옵션 배치 조회 시작: {} 개", menuOptionIds.size());

        List<MenuOptionResponseDTO> results = menuOptionIds.stream()
                .map(this::safeGetMenuOption)
                .filter(option -> option != null)
                .toList();

        log.debug("메뉴 옵션 배치 조회 완료: {}/{} 개 성공", results.size(), menuOptionIds.size());
        return results;
    }

    private MenuOptionResponseDTO safeGetMenuOption(UUID menuOptionId) {
        try {
            return menuOptionById(menuOptionId);
        } catch (Exception e) {
            log.warn("메뉴 옵션 조회 실패: {}, 오류: {}", menuOptionId, e.getMessage());
            return null;
        }
    }
}
