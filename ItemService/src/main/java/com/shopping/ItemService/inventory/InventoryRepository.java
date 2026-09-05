package com.shopping.ItemService.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {
    @Modifying
    @Transactional
    @Query("update Inventory i set i.availableQuantity = i.availableQuantity - :quantity, i.version = i.version + 1 " +
            "where i.itemId = :itemId and i.availableQuantity >= :quantity")
    int reserve(@Param("itemId") String itemId, @Param("quantity") int quantity);

    @Modifying
    @Transactional
    @Query("update Inventory i set i.availableQuantity = i.availableQuantity + :quantity, i.version = i.version + 1 " +
            "where i.itemId = :itemId")
    int release(@Param("itemId") String itemId, @Param("quantity") int quantity);
}
