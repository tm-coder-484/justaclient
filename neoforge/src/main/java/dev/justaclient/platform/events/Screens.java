package dev.justaclient.platform.events;
import java.util.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
public final class Screens {
    public static List<AbstractWidget> getButtons(Screen s) {
        return new AbstractList<>() {
            private List<AbstractWidget> widgets() { return s.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList(); }
            public AbstractWidget get(int i) { return widgets().get(i); }
            public int size() { return widgets().size(); }
            public void add(int index, AbstractWidget widget) { ((dev.justaclient.mixin.ScreenAccessor)s).justaclient$addWidget(widget); }
        };
    }
}
