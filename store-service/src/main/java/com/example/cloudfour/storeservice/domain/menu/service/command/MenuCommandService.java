package com.example.cloudfour.storeservice.domain.menu.service.command;

import com.example.cloudfour.modulecommon.dto.Passport;
import com.example.cloudfour.storeservice.domain.menu.converter.MenuConverter;
import com.example.cloudfour.storeservice.domain.menu.converter.MenuOptionConverter;
import com.example.cloudfour.storeservice.domain.menu.dto.MenuRequestDTO;
import com.example.cloudfour.storeservice.domain.menu.dto.MenuResponseDTO;
import com.example.cloudfour.storeservice.domain.menu.dto.MenuOptionResponseDTO;
import com.example.cloudfour.storeservice.domain.menu.entity.Menu;
import com.example.cloudfour.storeservice.domain.menu.entity.MenuCategory;
import com.example.cloudfour.storeservice.domain.menu.entity.MenuOption;
import com.example.cloudfour.storeservice.domain.menu.entity.Stock;
import com.example.cloudfour.storeservice.domain.menu.exception.MenuException;
import com.example.cloudfour.storeservice.domain.menu.exception.MenuErrorCode;
import com.example.cloudfour.storeservice.domain.menu.exception.MenuOptionErrorCode;
import com.example.cloudfour.storeservice.domain.menu.exception.MenuOptionException;
import com.example.cloudfour.storeservice.domain.menu.repository.MenuCategoryRepository;
import com.example.cloudfour.storeservice.domain.menu.repository.MenuRepository;
import com.example.cloudfour.storeservice.domain.menu.repository.MenuOptionRepository;
import com.example.cloudfour.storeservice.domain.store.entity.Store;
import com.example.cloudfour.storeservice.domain.store.exception.StoreErrorCode;
import com.example.cloudfour.storeservice.domain.store.exception.StoreException;
import com.example.cloudfour.storeservice.domain.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class MenuCommandService {

    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuOptionRepository menuOptionRepository;
    private final StockCommandService stockCommandService;

    public MenuResponseDTO.MenuDetailResponseDTO createMenu(
            MenuRequestDTO.MenuCreateRequestDTO requestDTO,
            UUID storeId,
            Passport passport
    ) {
        Store store = storeRepository.findByIdAndIsDeletedFalse(storeId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 가게");
                    return new StoreException(StoreErrorCode.NOT_FOUND);
                });

        if (passport == null || !store.getOwnerId().equals(passport.getUserId())) {
            log.warn("메뉴 생성 권한 없음");
            throw new MenuException(MenuErrorCode.UNAUTHORIZED_ACCESS);
        }

        log.info("메뉴 생성 권한 확인 성공");
        MenuCategory menuCategory = menuCategoryRepository.findByCategory(requestDTO.getMenuCommonMainRequestDTO().getCategory())
                .orElseGet(() -> menuCategoryRepository.save(
                        MenuCategory.builder().category(requestDTO.getMenuCommonMainRequestDTO().getCategory()).build()
                ));

        if (menuRepository.existsByNameAndStoreId(requestDTO.getMenuCommonMainRequestDTO().getName(), store.getId())) {
            log.warn("이미 존재하지 않는 메뉴");
            throw new MenuException(MenuErrorCode.ALREADY_ADD);
        }

        Stock stock = Stock.builder().quantity(requestDTO.getQuantity()).build();

        Menu menu = MenuConverter.toMenu(requestDTO);
        menu.setStore(store);
        menu.setMenuCategory(menuCategory);
        stock.setMenu(menu);

        Menu savedMenu = menuRepository.save(menu);
        log.info("메뉴 생성 완료");
        return MenuConverter.toMenuDetail1ResponseDTO(savedMenu);

    }

    
    public MenuResponseDTO.MenuDetailResponseDTO updateMenu(
            UUID menuId,
            MenuRequestDTO.MenuUpdateRequestDTO requestDTO,
            Passport passport
    ) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 메뉴");
                    return new MenuException(MenuErrorCode.NOT_FOUND);
                });

        if (passport == null || !menu.getStore().getOwnerId().equals(passport.getUserId())) {
            log.warn("메뉴 수정 권한 없음");
            throw new MenuException(MenuErrorCode.UNAUTHORIZED_ACCESS);
        }
        log.info("메뉴 수정 권한 확인 성공");
        MenuCategory menuCategory = menuCategoryRepository.findByCategory(requestDTO.getMenuCommonMainRequestDTO().getCategory())
                .orElseGet(() -> menuCategoryRepository.save(
                        MenuCategory.builder()
                                .category(requestDTO.getMenuCommonMainRequestDTO().getCategory())
                                .build()

                ));

        menu.updateMenuInfo(
                requestDTO.getMenuCommonMainRequestDTO().getContent(),
                requestDTO.getMenuCommonMainRequestDTO().getName(),
                requestDTO.getMenuCommonMainRequestDTO().getPrice(),
                requestDTO.getMenuCommonMainRequestDTO().getMenuPicture(),
                requestDTO.getMenuCommonMainRequestDTO().getStatus()
        );
        menu.setMenuCategory(menuCategory);

         Long quantity = requestDTO.getQuantity();
         UUID stockId = menu.getStock().getId();
         if(quantity>0){
             log.info("재고 증가");
             stockCommandService.increaseStock(stockId, quantity);
         }else{
             log.info("재고 감소");
             stockCommandService.decreaseStock(stockId, quantity);
         }
        Menu updatedMenu = menuRepository.save(menu);
        log.info("메뉴 수정 성공");
        return MenuConverter.toMenuDetail1ResponseDTO(updatedMenu);

    }

    
    public void deleteMenu(UUID menuId, Passport passport) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 메뉴");
                    return new MenuException(MenuErrorCode.NOT_FOUND);
                });

        if (passport == null || !menu.getStore().getOwnerId().equals(passport.getUserId())) {
            log.warn("메뉴 삭제 권한 없음");
            throw new MenuException(MenuErrorCode.UNAUTHORIZED_ACCESS);
        }
        log.info("메뉴 삭제 권한 성공");
        menu.softDelete();
        log.info("메뉴 ID: {}가 삭제되었습니다.", menuId);
    }

    public MenuOptionResponseDTO.MenuOptionSimpleResponseDTO createMenuOption(
            MenuRequestDTO.MenuOptionCreateRequestDTO requestDTO,
            Passport passport, UUID menuId
    ) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 메뉴");
                    return new MenuException(MenuErrorCode.NOT_FOUND);
                });


        if (passport == null || !menu.getStore().getOwnerId().equals(passport.getUserId())) {
            log.warn("메뉴 옵션 생성 권한 없음");
            throw new MenuOptionException(MenuOptionErrorCode.UNAUTHORIZED_ACCESS);
        }

        if (menuOptionRepository.existsByMenuIdAndOptionName(menu.getId(), requestDTO.getMenuOptionCommonRequestDTO().getOptionName())) {
            log.warn("이미 존재하는 메뉴옵션");
            throw new MenuOptionException(MenuOptionErrorCode.ALREADY_ADD);
        }

        log.info("메뉴옵션 생성 권한 확인 성공");
        MenuOption menuOption = MenuOption.builder()
                .optionName(requestDTO.getMenuOptionCommonRequestDTO().getOptionName())
                .additionalPrice(requestDTO.getMenuOptionCommonRequestDTO().getAdditionalPrice())
                .build();

        menuOption.setMenu(menu);
        menuOptionRepository.save(menuOption);
        log.info("메뉴옵션 생성 완료");
        return MenuOptionConverter.toMenuOptionSimpleResponseDTO(menuOption);
    }
    
    public MenuOptionResponseDTO.MenuOptionSimpleResponseDTO updateMenuOption(
            UUID optionId,
            MenuRequestDTO.MenuOptionUpdateRequestDTO requestDTO,
            Passport passport
    ) {
        MenuOption menuOption = menuOptionRepository.findByIdWithMenu(optionId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 메뉴옵션");
                    return new MenuOptionException(MenuOptionErrorCode.NOT_FOUND);
                });

        if (passport ==null || !menuOption.getMenu().getStore().getOwnerId().equals(passport.getUserId())) {
            log.warn("메뉴 옵션 수정 권한 없음");
            throw new MenuOptionException(MenuOptionErrorCode.UNAUTHORIZED_ACCESS);
        }

        if (!menuOption.getOptionName().equals(requestDTO.getMenuOptionCommonRequestDTO().getOptionName()) &&
                menuOptionRepository.existsByMenuIdAndOptionName(menuOption.getMenu().getId(), requestDTO.getMenuOptionCommonRequestDTO().getOptionName())) {
            log.warn("이미 존재하는 메뉴옵션 이름");
            throw new MenuOptionException(MenuOptionErrorCode.ALREADY_ADD);
        }
        log.info("메뉴옵션 수정 권한 확인 성공");
        menuOption.updateOptionInfo(requestDTO.getMenuOptionCommonRequestDTO().getOptionName(), requestDTO.getMenuOptionCommonRequestDTO().getAdditionalPrice());
        MenuOption savedOption = menuOptionRepository.save(menuOption);
        log.info("메뉴옵션 수정 완료");
        return MenuOptionConverter.toMenuOptionSimpleResponseDTO(savedOption);
    }

    
    public void deleteMenuOption(UUID optionId, Passport passport) {
        MenuOption menuOption = menuOptionRepository.findByIdWithMenu(optionId)
                .orElseThrow(() -> {
                    log.warn("존재하지 않는 메뉴옵션");
                    return new MenuOptionException(MenuOptionErrorCode.NOT_FOUND);
                });

        if (passport == null || !menuOption.getMenu().getStore().getOwnerId().equals(passport.getUserId())) {
            log.warn("메뉴 옵션 삭제 권한 없음");
            throw new MenuException(MenuErrorCode.UNAUTHORIZED_ACCESS);
        }
        log.info("메뉴옵션 삭제 권한 확인 성공");
        menuOption.softDelete();
        log.info("메뉴 옵션 ID: {}가 삭제되었습니다.", optionId);
    }
}
