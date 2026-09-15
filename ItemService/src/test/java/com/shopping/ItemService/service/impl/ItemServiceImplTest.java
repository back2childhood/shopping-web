package com.shopping.ItemService.service.impl;

import com.shopping.ItemService.dao.InventoryRepository;
import com.shopping.ItemService.dao.ItemProjectionRepository;
import com.shopping.ItemService.dao.ItemRepository;
import com.shopping.ItemService.entity.Inventory;
import com.shopping.ItemService.entity.Item;
import com.shopping.ItemService.event.Event;
import com.shopping.ItemService.payload.ItemRequestDto;
import com.shopping.ItemService.payload.ItemResponseDto;
import com.shopping.ItemService.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemProjectionRepository itemProjectionRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private OutboxService outboxService;

    private ItemServiceImpl itemService;

    @BeforeEach
    void setUp() {
        itemService = new ItemServiceImpl(
                itemRepository,
                itemProjectionRepository,
                inventoryRepository,
                outboxService,
                "item.events.v1"
        );
    }

    @Test
    void createItemUsesAuthenticatedUserAndEnqueuesOutboxEvent() {
        when(itemRepository.saveAndFlush(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setVersion(0L);
            return item;
        });
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ItemResponseDto response = itemService.createItem(request(), 6L);

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).saveAndFlush(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getUserId()).isEqualTo(6L);
        assertThat(response.getUserId()).isEqualTo(6L);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(outboxService).enqueue(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(6L);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("ITEM_CREATED");
    }

    @Test
    void sellerCannotUpdateAnotherSellersItem() {
        Item item = item(9L);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> itemService.updateItem(item.getId(), request(), 6L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");

        verify(itemRepository, never()).saveAndFlush(any(Item.class));
        verify(outboxService, never()).enqueue(any(Event.class));
    }

    private ItemRequestDto request() {
        return new ItemRequestDto("Keyboard", "Mechanical keyboard", new BigDecimal("99.90"), "USD", 10);
    }

    private Item item(Long userId) {
        Instant now = Instant.now();
        return new Item(
                "item-id",
                userId,
                "Keyboard",
                "Mechanical keyboard",
                new BigDecimal("99.90"),
                "USD",
                now,
                now,
                0L
        );
    }
}
