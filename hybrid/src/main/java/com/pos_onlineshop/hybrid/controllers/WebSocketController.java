package com.pos_onlineshop.hybrid.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    @MessageMapping("/inventory-update")
    @SendTo("/topic/inventory")
    public Object handleInventoryUpdate(Object update) {
        return update;
    }

    @MessageMapping("/order-notification")
    @SendTo("/topic/orders")
    public Object handleOrderNotification(Object order) {
        return order;
    }
}