package com.lilac.planner.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StickerCatalog {

    private static final List<Sticker> CATALOG = List.of(
            new Sticker("kitty",     "🐱", "Sweet Kitty"),
            new Sticker("puppy",     "🐶", "Happy Puppy"),
            new Sticker("bunny",     "🐰", "Cuddle Bunny"),
            new Sticker("panda",     "🐼", "Bamboo Panda"),
            new Sticker("fox",       "🦊", "Clever Fox"),
            new Sticker("hedgehog",  "🦔", "Tiny Hedgehog"),
            new Sticker("koala",     "🐨", "Sleepy Koala"),
            new Sticker("otter",     "🦜", "Otter Friend"),
            new Sticker("hamster",   "🐹", "Pocket Hamster"),
            new Sticker("penguin",   "🐧", "Penguin Pal"),
            new Sticker("unicorn",   "🦄", "Magic Unicorn"),
            new Sticker("turtle",    "🐢", "Calm Turtle"),
            new Sticker("chick",     "🐥", "Spring Chick"),
            new Sticker("sloth",     "🦌", "Cozy Sloth"),
            new Sticker("bear",      "🐻", "Bear Hug"),
            new Sticker("butterfly", "🦋", "Soft Butterfly"),
            new Sticker("bee",       "🐝", "Sweet Bee"),
            new Sticker("raccoon",   "🦝", "Curious Raccoon"),
            new Sticker("frog",      "🐸", "Lily Frog"),
            new Sticker("duck",      "🦆", "Pond Duck")
    );

    private final Map<String, Sticker> byCode = CATALOG.stream()
            .collect(Collectors.toMap(Sticker::code, s -> s));

    public List<Sticker> all() {
        return CATALOG;
    }

    public Sticker byCode(String code) {
        return byCode.get(code);
    }

    public Sticker pickFor(long seed) {
        int idx = (int) Math.floorMod(seed, (long) CATALOG.size());
        return CATALOG.get(idx);
    }
}
