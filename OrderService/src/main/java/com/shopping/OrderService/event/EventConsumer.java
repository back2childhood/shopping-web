//package com.shopping.OrderService.event;
//
//import com.alibaba.fastjson2.JSONObject;
//import com.shopping.OrderService.payload.OrderResponseDto;
//import com.shopping.OrderService.service.OrderService;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
//@Component
//public class EventConsumer {
//
//    private final OrderService orderService;
//
//    public EventConsumer(OrderService orderService) {
//        this.orderService = orderService;
//    }
//
//    @KafkaListener(topics = "order")
//    public void handleArticleMessage(String message) {
//        if (message == null) {
//            return;
//        }
//
//        Event event = JSONObject.parseObject(message, Event.class);
//
//        OrderResponseDto order = orderService.getArticleById(event.getEntityId());
//
//        Set<Tag> tags = article.getTags();
//
//        List<String> tagIds = new ArrayList<>();
////                tags.stream().map(Tag::getName).collect(Collectors.toList());
//
//        for (Tag tag : tags) {
////            System.out.println(tag.getName());
//            tagIds.add(tag.getName());
//        }
//
//        ArticleDocument esArticle = new ArticleDocument(
//                article.getId(),
//                article.getTitle(),
//                article.getUserId().toString(),
//                article.getContent(),
//                tagIds
//        );
//
//        articleSearchRepository.save(esArticle);  // Save to Elasticsearch
//    }
//}