package io.casehub.blocks.summarisation.observation.affordance;

import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface ObservationSection permits ObservationSection.EntityGroup, ObservationSection.TextBlock, ObservationSection.ItemList, AnnotatedSection {

    String header();

    record EntityGroup(
            String header,
            @Nullable String emptyMessage,
            List<ObservableEntity> entities) implements ObservationSection {
        public EntityGroup {
            if (header == null || header.isBlank())
                throw new IllegalArgumentException("header required");
            entities = List.copyOf(entities);
        }
    }

    record TextBlock(
            String header,
            String content) implements ObservationSection {
        public TextBlock {
            if (header == null || header.isBlank())
                throw new IllegalArgumentException("header required");
            if (content == null || content.isBlank())
                throw new IllegalArgumentException("content required");
        }
    }

    record ItemList(
            String header,
            @Nullable String emptyMessage,
            List<String> items) implements ObservationSection {
        public ItemList {
            if (header == null || header.isBlank())
                throw new IllegalArgumentException("header required");
            items = List.copyOf(items);
        }
    }

    static EntityGroup entities(String header, @Nullable String emptyMessage,
                                List<ObservableEntity> entities) {
        return new EntityGroup(header, emptyMessage, entities);
    }

    static TextBlock text(String header, String content) {
        return new TextBlock(header, content);
    }

    static ItemList items(String header, @Nullable String emptyMessage,
                          List<String> items) {
        return new ItemList(header, emptyMessage, items);
    }
}
