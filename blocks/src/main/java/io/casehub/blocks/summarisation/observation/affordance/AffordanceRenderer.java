package io.casehub.blocks.summarisation.observation.affordance;

import java.util.List;
import java.util.function.Function;

public class AffordanceRenderer {

    private static final Function<String, String> DEFAULT_HEADER_FORMATTER =
            header -> "== " + header + " ==";

    private final Function<String, String> headerFormatter;

    public AffordanceRenderer() {
        this(DEFAULT_HEADER_FORMATTER);
    }

    public AffordanceRenderer(Function<String, String> headerFormatter) {
        this.headerFormatter = headerFormatter;
    }

    public AffordanceRenderer withHeaderFormatter(
            Function<String, String> headerFormatter) {
        return new AffordanceRenderer(headerFormatter);
    }

    public String renderEntities(List<ObservableEntity> entities) {
        if (entities.isEmpty()) return "";
        var sb = new StringBuilder();
        for (int i = 0; i < entities.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(renderEntity(entities.get(i)));
        }
        return sb.toString();
    }

    public String renderEntities(List<ObservableEntity> entities,
                                 String emptyMessage) {
        if (entities.isEmpty()) return emptyMessage;
        return renderEntities(entities);
    }

    public String renderObservation(List<ObservationSection> sections) {
        if (sections.isEmpty()) {return "";}
        var     sb    = new StringBuilder();
        boolean first = true;
        for (var section : sections) {
            String rendered = renderSection(section);
            if (rendered == null) {continue;}
            if (!first) {sb.append("\n\n");}
            first = false;
            sb.append(headerFormatter.apply(section.header())).append('\n');
            sb.append(rendered);
        }
        return sb.toString();
    }

    public String renderActionVocabulary(String header,
                                         List<ActionDescriptor> actions) {
        if (actions.isEmpty()) {return header;}
        var sb = new StringBuilder(header);
        for (var action : actions) {
            sb.append('\n').append("- ").append(action.actionType());
            if (action.parameterFormat() != null) {
                sb.append(' ').append(action.parameterFormat());
            }
            sb.append(": ").append(action.description());
        }
        return sb.toString();
    }

    private String renderSection(ObservationSection section) {
        return switch (section) {
            case ObservationSection.EntityGroup eg -> {
                if (eg.entities().isEmpty()) {yield eg.emptyMessage();}
                yield renderEntities(eg.entities());
            }
            case ObservationSection.TextBlock tb -> tb.content();
            case ObservationSection.ItemList il -> {
                if (il.items().isEmpty()) {yield il.emptyMessage();}
                var items = new StringBuilder();
                for (int i = 0; i < il.items().size(); i++) {
                    if (i > 0) {items.append('\n');}
                    items.append("- ").append(il.items().get(i));
                }
                yield items.toString();
            }
        };
    }


    private String renderEntity(ObservableEntity entity) {
        var sb = new StringBuilder("- ")
                .append(entity.displayName())
                .append(" [id: ").append(entity.id()).append(']');
        if (entity.description() != null) {
            sb.append(": ").append(entity.description());
        }
        for (var affordance : entity.affordances()) {
            sb.append(' ').append(renderAffordance(affordance));
        }
        return sb.toString();
    }

    private String renderAffordance(Affordance affordance) {
        var sb = new StringBuilder("[").append(affordance.actionType());
        if (affordance.label() != null) {
            sb.append(' ').append(affordance.label());
        }
        if (affordance.requiredItem() != null) {
            sb.append(", requires: ").append(affordance.requiredItem());
        }
        if (!affordance.acceptsItems().isEmpty()) {
            sb.append(", with: ").append(
                    String.join(", ", affordance.acceptsItems()));
        }
        sb.append(']');
        return sb.toString();
    }
}
