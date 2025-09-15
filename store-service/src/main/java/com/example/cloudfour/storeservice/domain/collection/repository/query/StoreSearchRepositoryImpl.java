package com.example.cloudfour.storeservice.domain.collection.repository.query;

import com.example.cloudfour.storeservice.domain.collection.document.StoreDocument;
import com.example.cloudfour.storeservice.domain.menu.enums.MenuStatus;
import com.querydsl.core.BooleanBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.UnsetOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.QuerydslRepositorySupport;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.cloudfour.storeservice.domain.collection.document.QStoreDocument.storeDocument;

@Repository
public class StoreSearchRepositoryImpl extends QuerydslRepositorySupport implements StoreSearchRepository {

    private final MongoTemplate mongoTemplate;

    public StoreSearchRepositoryImpl(@Qualifier("mongoTemplate")MongoOperations operations, MongoTemplate mongoTemplate){
        super(operations);
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<StoreDocument> findStoreByStoreId(UUID storeId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("storeId").is(storeId)),
                Aggregation.project("id", "storeId", "userId", "name", "address", "phone", "content",
                                "minPrice", "deliveryTip", "rating", "likeCount", "reviewCount",
                                "OperationHours", "closedDays", "siDo", "siGunGu", "eupMyeonDong",
                                "pictureURL", "createdAt", "storeCategory", "menus", "reviews")
                        .and(ArrayOperators.Filter.filter("menus")
                                .as("m")
                                .by(ComparisonOperators.Ne.valueOf("m.menuStatus")
                                        .notEqualTo(MenuStatus.숨김.name())))
                        .as("menus"),
                UnsetOperation.unset("menus.menuOptions")
        );

        AggregationResults<StoreDocument> results = mongoTemplate.aggregate(aggregation, "store-service", StoreDocument.class);
        StoreDocument mapped = results.getUniqueMappedResult();
        return Optional.ofNullable(mapped);
    }

    @Override
    public Slice<StoreDocument> findAllStoreByCategoryAndCursor(UUID categoryId, LocalDateTime cursor, Pageable pageable, String siDo, String siGunGu, String eupMyeongDong) {
        BooleanBuilder regionBuilder = new BooleanBuilder();
        if(siDo!=null){
            regionBuilder.or(storeDocument.siDo.containsIgnoreCase(siDo));
        }
        if(siGunGu!=null){
            regionBuilder.or(storeDocument.siGunGu.containsIgnoreCase(siGunGu));
        }
        if(eupMyeongDong!=null){
            regionBuilder.or(storeDocument.eupMyeonDong.containsIgnoreCase(eupMyeongDong));
        }

        int pageSize = pageable.getPageSize();
        List<StoreDocument> stores = from(storeDocument)
                .where(storeDocument.storeCategory.id.eq(categoryId), regionBuilder
                        , storeDocument.createdAt.lt(cursor)).orderBy(storeDocument.createdAt.desc()).limit(pageSize+1)
                .fetch();

        boolean hasNext = stores.size() > pageSize;
        if(hasNext){
            stores.remove(pageSize);
        }
        return new SliceImpl<>(stores,pageable,hasNext);
    }

    @Override
    public Slice<StoreDocument> findAllStoreByKeyWordAndRegion(String keyword, LocalDateTime cursor, Pageable pageable, String siDo, String siGunGu, String eupMyeongDong) {
        int pageSize = pageable.getPageSize();

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(storeDocument.createdAt.lt(cursor));

        if(keyword!=null && !keyword.isEmpty()){
            builder.and(storeDocument.name.containsIgnoreCase(keyword)
                    .or(storeDocument.storeCategory.storeCategoryName.containsIgnoreCase(keyword)));
        }

        BooleanBuilder regionBuilder = new BooleanBuilder();
        if(siDo!=null){
            regionBuilder.or(storeDocument.siDo.containsIgnoreCase(siDo));
        }
        if(siGunGu!=null){
            regionBuilder.or(storeDocument.siGunGu.containsIgnoreCase(siGunGu));
        }
        if(eupMyeongDong!=null){
            regionBuilder.or(storeDocument.eupMyeonDong.containsIgnoreCase(eupMyeongDong));
        }
        builder.and(regionBuilder);

        List<StoreDocument> stores = from(storeDocument).where(builder)
                .orderBy(storeDocument.createdAt.desc()).limit(pageSize+1).fetch();
        boolean hasNext = stores.size() > pageSize;
        if(hasNext){
            stores.remove(pageSize);
        }
        return new SliceImpl<>(stores,pageable,hasNext);
    }

    @Override
    public List<StoreDocument.Menu> findMenuByStoreId(UUID storeId) {

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("storeId").is(storeId)),
                Aggregation.unwind("menus"),
                Aggregation.match(Criteria.where("menus.menuStatus").ne(MenuStatus.숨김.name())),
                Aggregation.replaceRoot("menus")
        );

        AggregationResults<StoreDocument.Menu> results = mongoTemplate.aggregate(
                aggregation, "store-service", StoreDocument.Menu.class);

        return results.getMappedResults();
    }

    @Override
    public List<StoreDocument.Menu> findMenuByStoreIdAndMenuCategoryId(UUID storeId, UUID categoryId) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("storeId").is(storeId),
                Criteria.where("menus.menuCategoryId").is(categoryId)
        );

        Query mongoQuery = new Query(criteria);
        mongoQuery.fields().include("menus");

        StoreDocument result = mongoTemplate.findOne(mongoQuery,StoreDocument.class);

        if (result != null && result.getMenus() != null) {
            return result.getMenus().stream()
                    .filter(menu -> menu.getMenuCategory().getId().equals(categoryId))
                    .filter(menu -> menu.getMenuStatus() != MenuStatus.숨김)
                    .collect(Collectors.toList());
        }


        return Collections.emptyList();
    }

    @Override
    public Optional<StoreDocument.Menu> findMenuByMenuId(UUID menuId) {
        Criteria criteria = Criteria.where("menus.id").is(menuId)
                .and("menus.menuStatus").ne(MenuStatus.숨김.name());
        Query mongoQuery = new Query(criteria);
        mongoQuery.fields().include("menus.$");

        StoreDocument result = mongoTemplate.findOne(mongoQuery, StoreDocument.class);

        if(result != null && result.getMenus() != null && !result.getMenus().isEmpty()){
            return Optional.of(result.getMenus().getFirst());
        }

        return Optional.empty();
    }

    @Override
    public List<StoreDocument.MenuOption> findMenuOptionByMenuIdOrderByAdditionalPrice(UUID menuId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("menus.id").is(menuId)),
                Aggregation.unwind("menus"),
                Aggregation.match(Criteria.where("menus.id").is(menuId)),
                Aggregation.unwind("menus.menuOptions"),
                Aggregation.sort(Sort.by("menus.menuOptions.additionalPrice").ascending()),
                Aggregation.replaceRoot("menus.menuOptions")
        );

        AggregationResults<StoreDocument.MenuOption> results = mongoTemplate.aggregate(
                aggregation, StoreDocument.class, StoreDocument.MenuOption.class);

        return results.getMappedResults();
    }

    @Override
    public Optional<StoreDocument.MenuOption> findMenuOptionByMenuOptionId(UUID menuOptionId) {
        Criteria criteria = Criteria.where("menus.menuOptions.id").is(menuOptionId);
        Query mongoQuery = new Query(criteria);
        mongoQuery.fields().include("menus");

        StoreDocument result = mongoTemplate.findOne(mongoQuery, StoreDocument.class);

        if (result != null && result.getMenus() != null) {
            return result.getMenus().stream()
                    .filter(menu -> menu.getMenuOptions() != null)
                    .flatMap(menu -> menu.getMenuOptions().stream())
                    .filter(option -> option.getId().equals(menuOptionId))
                    .findFirst();
        }

        return Optional.empty();
    }
}

