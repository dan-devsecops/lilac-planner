package com.lilac.planner.controller;

import com.lilac.planner.service.Sticker;
import com.lilac.planner.service.StickerCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stickers")
public class StickerController {

    private final StickerCatalog catalog;

    public StickerController(StickerCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<Sticker> all() {
        return catalog.all();
    }
}
