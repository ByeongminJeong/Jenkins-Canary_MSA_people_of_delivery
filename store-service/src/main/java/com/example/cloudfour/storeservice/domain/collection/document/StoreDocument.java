package com.example.cloudfour.storeservice.domain.collection.document;

import com.example.cloudfour.storeservice.domain.menu.enums.MenuStatus;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document("store-service")
@CompoundIndexes({
        @CompoundIndex(name = "category_region_created_idx", def = "{'storeCategory.id': 1, 'siDo': 1, 'siGunGu': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "category_created_idx", def = "{'storeCategory.id': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "region_created_idx", def = "{'siDo': 1, 'siGunGu': 1, 'eupMyeonDong': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "name_category_created_idx", def = "{'name': 1, 'storeCategory.storeCategoryName': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "menu_store_idx", def = "{'storeId': 1, 'menus.id': 1}"),
        @CompoundIndex(name = "menu_category_idx", def = "{'storeId': 1, 'menus.menuCategory.id': 1}"),
        @CompoundIndex(name = "menu_option_idx", def = "{'menus.id': 1, 'menus.menuOptions.id': 1}")
})
public class StoreDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    private UUID storeId;

    @Indexed
    private UUID userId;

    @TextIndexed(weight = 2.0f)
    private String name;

    @Indexed
    private String address;

    private String phone;

    @TextIndexed
    private String content;

    @Indexed
    private Integer minPrice;

    private Integer deliveryTip;

    @Indexed(direction = IndexDirection.DESCENDING)
    private Float rating;

    @Indexed(direction = IndexDirection.DESCENDING)
    private Integer likeCount;

    @Indexed(direction = IndexDirection.DESCENDING)
    private Integer reviewCount;

    private String OperationHours;
    private String closedDays;

    @Indexed
    private String siDo;

    @Indexed
    private String siGunGu;

    @Indexed
    private String eupMyeonDong;

    private String pictureURL;

    @Indexed
    private LocalDateTime createdAt;

    private StoreDocument.StoreCategory storeCategory;
    private List<StoreDocument.Menu> menus;
    private List<StoreDocument.Review> reviews;

    @Getter
    @Builder
    public static class StoreCategory{
        private UUID id;
        private String storeCategoryName;
    }

    @Getter
    @Builder
    public static class Menu {
        private UUID id;
        private String name;
        private String content;
        private Integer price;
        private String menuPicture;
        private MenuStatus menuStatus;
        private StoreDocument.Stock stock;
        private StoreDocument.MenuCategory menuCategory;
        private List<StoreDocument.MenuOption> menuOptions;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class MenuOption {
        private UUID id;
        private Integer additionalPrice;
        private String optionName;
    }

    @Getter
    @Builder
    public static class MenuCategory{
        private UUID id;
        private String menuCategoryName;
    }

    @Getter
    @Builder
    public static class Review {
        private UUID id;
        private Float score;
        private String content;
    }

    @Getter
    @Builder
    public static class Stock{
        private UUID id;
        private Long quantity;

        public void updateStock(UUID id, Long quantity){
            this.id = id;
            this.quantity = quantity;
        }
    }
}
