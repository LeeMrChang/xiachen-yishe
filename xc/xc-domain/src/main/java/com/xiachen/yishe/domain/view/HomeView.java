package com.xiachen.yishe.domain.view;

import java.util.List;

public record HomeView(Theme theme, List<Module> modules) {

    public record Theme(Long id, String name, String copyText, String coverUrl) {
    }

    public record Module(String type, String imageUrl, String title, String subtitle,
                         List<ProductCard> products, List<LookCard> looks, StoreView store) {
    }
}
