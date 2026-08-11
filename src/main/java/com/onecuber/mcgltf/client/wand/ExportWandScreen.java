package com.onecuber.mcgltf.client.wand;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.job.ExportSummary;
import com.onecuber.mcgltf.job.ExportTelemetry;
import com.onecuber.mcgltf.network.ExportWandRequestPayload;
import com.onecuber.mcgltf.network.ToggleWandOverlayPayload;
import com.onecuber.mcgltf.network.ToggleWandIncludePlayersPayload;
import com.onecuber.mcgltf.network.UpdateWandEndpointPayload;
import com.onecuber.mcgltf.network.UpdateWandExportNamePayload;
import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.wand.Axis;
import com.onecuber.mcgltf.wand.Endpoint;
import com.onecuber.mcgltf.wand.ExportWandMenu;
import com.onecuber.mcgltf.wand.ExportWandSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class ExportWandScreen extends AbstractContainerScreen<ExportWandMenu> {
    private static final int ORANGE = 0xFFED741C;
    private static final int BLUE = 0xFF3488D8;
    private static final int MID = 0xFF7F8790;
    private static final int LIGHT = 0xFFE0E4E8;
    private static final int RED = 0xFFE05555;

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean intersects(Rect other) {
            return x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }
    }

    private record NineSliceStyle(int physicalBorder) {
        private NineSliceStyle {
            if (physicalBorder < 0) {
                throw new IllegalArgumentException("physicalBorder must not be negative");
            }
        }
    }

    private static NineSliceStyle panelStyle() {
        return new NineSliceStyle(8);
    }

    private static NineSliceStyle fieldStyle() {
        return new NineSliceStyle(8);
    }

    private static NineSliceStyle fieldStyleFor(int index) {
        return fieldStyle();
    }

    private static NineSliceStyle buttonStyle() {
        return new NineSliceStyle(8);
    }

    private static NineSliceStyle titleStyle() {
        return new NineSliceStyle(8);
    }

    private static NineSliceStyle lineStyle() {
        return new NineSliceStyle(0);
    }

    private static NineSliceStyle progressStyle() {
        return new NineSliceStyle(8);
    }

    public static final class Layout {
        public static final Rect HEADER = new Rect(0, 0, 384, 20);
        public static final Rect LEFT = new Rect(4, 24, 208, 166);
        public static final Rect RIGHT = new Rect(216, 24, 164, 166);
        public static final Rect LOG = new Rect(4, 194, 376, 18);

        static Rect endpointTitle(Endpoint endpoint) {
            int y = LEFT.y() + (endpoint == Endpoint.POS1 ? 8 : 74);
            return new Rect(LEFT.x() + 12, y, 116, 10);
        }

        static Rect coordinateField(int index) {
            int group = index / 3;
            int row = index % 3;
            return new Rect(LEFT.x() + 32,
                    LEFT.y() + 22 + group * 66 + row * 16,
                    116, 14);
        }

        static Rect stepUp(int index) {
            Rect field = coordinateField(index);
            return new Rect(LEFT.x() + 154, field.y() - 1, 16, 16);
        }

        static Rect stepDown(int index) {
            Rect field = coordinateField(index);
            return new Rect(LEFT.x() + 172, field.y() - 1, 16, 16);
        }

        static Rect overlayButton() {
            return new Rect(LEFT.x() + 12, LEFT.y() + 144, 92, 16);
        }

        static Rect includePlayersButton() {
            return new Rect(LEFT.x() + 108, LEFT.y() + 144, 92, 16);
        }

        static Rect nameField() {
            return new Rect(RIGHT.x() + 8, RIGHT.y() + 34, 148, 16);
        }

        static Rect exportButton() {
            return new Rect(RIGHT.x() + 8, RIGHT.y() + 58, 148, 16);
        }

        static Rect cancelButton() {
            return new Rect(RIGHT.x() + 8, RIGHT.y() + 78, 148, 16);
        }

        private Layout() {
        }
    }

    private final ExportWandController controller;
    private final List<CoordinateEditorModel> coordinateModels = new ArrayList<>();
    private final List<EditBox> coordinateFields = new ArrayList<>();
    private EditBox nameField;
    private Button exportButton;
    private Button cancelButton;
    private Button overlayButton;
    private Button includePlayersButton;
    private boolean overlayVisible;
    private boolean includePlayers;
    private boolean controllerBound;
    private boolean nameWasFocused;
    private final boolean[] coordinateWasFocused = new boolean[6];
    private String statusLine = "";

    public ExportWandScreen(
            ExportWandMenu menu,
            Inventory inventory,
            Component title,
            ExportWandController controller) {
        super(menu, inventory, title);
        this.controller = controller;
        this.imageWidth = 384;
        this.imageHeight = 216;
    }

    @Override
    protected void init() {
        super.init();
        if (!controllerBound) {
            controller.bind(menu.binding().wandId(), currentDimension());
            controllerBound = true;
        }
        coordinateModels.clear();
        coordinateFields.clear();
        createCoordinateWidgets();
        createActionWidgets();
        refreshFromMenu();
        syncOverlayFromMenu();
        statusLine = "";
    }

    private void createCoordinateWidgets() {
        for (Endpoint endpoint : Endpoint.values()) {
            for (Axis axis : Axis.values()) {
                int modelIndex = index(endpoint, axis);
                CoordinateEditorModel model = new CoordinateEditorModel();
                coordinateModels.add(model);
                Rect skin = Layout.coordinateField(modelIndex);
                EditBox field = new EditBox(font,
                        screenX(skin.x() + 4), screenY(skin.y() + 1),
                        skin.width() - 8, skin.height() - 2,
                        Component.literal(axis.name()));
                field.setBordered(false);
                field.setTextColor(LIGHT);
                field.setTextColorUneditable(MID);
                field.setMaxLength(11);
                field.setFilter(text -> text.matches("-?\\d*"));
                field.setResponder(model::setText);
                addRenderableWidget(field);
                coordinateFields.add(field);

                addRenderableWidget(skinnedButton(Component.literal("增加 1"),
                        Layout.stepUp(modelIndex),
                        pressed -> step(endpoint, axis, 1, false),
                        ExportWandTextures.GUI_041,
                        ExportWandTextures.GUI_042,
                        ExportWandTextures.GUI_045,
                        null, () -> false, false));
                addRenderableWidget(skinnedButton(Component.literal("减少 1"),
                        Layout.stepDown(modelIndex),
                        pressed -> step(endpoint, axis, -1, false),
                        ExportWandTextures.GUI_047,
                        ExportWandTextures.GUI_048,
                        ExportWandTextures.GUI_046,
                        null, () -> false, false));
            }
        }
        overlayButton = addRenderableWidget(skinnedToggleButton(
                Component.literal("选区显示：关"),
                Layout.overlayButton(), pressed -> toggleOverlay(),
                ExportWandTextures.GUI_043,
                ExportWandTextures.GUI_044,
                ExportWandTextures.GUI_045,
                ExportWandTextures.GUI_059,
                ExportWandTextures.GUI_060,
                () -> overlayVisible));
        includePlayersButton = addRenderableWidget(skinnedToggleButton(
                Component.literal("导出玩家：关"),
                Layout.includePlayersButton(), pressed -> toggleIncludePlayers(),
                ExportWandTextures.GUI_043,
                ExportWandTextures.GUI_044,
                ExportWandTextures.GUI_045,
                ExportWandTextures.GUI_059,
                ExportWandTextures.GUI_060,
                () -> includePlayers));
    }

    private void createActionWidgets() {
        Rect nameSkin = Layout.nameField();
        nameField = new EditBox(font,
                screenX(nameSkin.x() + 5), screenY(nameSkin.y() + 2),
                nameSkin.width() - 10, nameSkin.height() - 4,
                Component.literal("导出名"));
        nameField.setBordered(false);
        nameField.setTextColor(LIGHT);
        nameField.setTextColorUneditable(MID);
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);

        exportButton = addRenderableWidget(skinnedButton(
                Component.literal("导出"), Layout.exportButton(),
                pressed -> requestExport(),
                ExportWandTextures.GUI_035,
                ExportWandTextures.GUI_036,
                ExportWandTextures.GUI_045,
                ExportWandTextures.GUI_037,
                () -> controller.state() == ExportWandController.State.EXPORTING,
                true));
        cancelButton = addRenderableWidget(skinnedButton(
                Component.literal("取消"), Layout.cancelButton(),
                pressed -> closeScreen(),
                ExportWandTextures.GUI_032,
                ExportWandTextures.GUI_033,
                ExportWandTextures.GUI_046,
                ExportWandTextures.GUI_034,
                () -> controller.state() == ExportWandController.State.CANCELLED,
                true));
        cancelButton.active = false;
    }

    private Button skinnedButton(
            Component label, Rect rect, Button.OnPress onPress,
            ExportWandTextures.Texture normal,
            ExportWandTextures.Texture hovered,
            ExportWandTextures.Texture disabled,
            ExportWandTextures.Texture selected,
            BooleanSupplier selectedState,
            boolean drawMessage) {
        Button.Builder builder = Button.builder(label, onPress)
                .bounds(screenX(rect.x()), screenY(rect.y()), rect.width(), rect.height());
        return new SkinnedButton(builder, font, normal, hovered, disabled,
                selected, selectedState, drawMessage, null, null);
    }

    private Button skinnedToggleButton(
            Component label, Rect rect, Button.OnPress onPress,
            ExportWandTextures.Texture normal,
            ExportWandTextures.Texture hovered,
            ExportWandTextures.Texture disabled,
            ExportWandTextures.Texture indicatorOff,
            ExportWandTextures.Texture indicatorOn,
            BooleanSupplier selectedState) {
        Button.Builder builder = Button.builder(label, onPress)
                .bounds(screenX(rect.x()), screenY(rect.y()), rect.width(), rect.height());
        return new SkinnedButton(builder, font, normal, hovered, disabled,
                null, selectedState, true, indicatorOff, indicatorOn);
    }

    private void refreshFromMenu() {
        ExportWandSelection selection = menu.selection();
        setEndpoint(Endpoint.POS1, selection.pos1().orElse(null));
        setEndpoint(Endpoint.POS2, selection.pos2().orElse(null));
        if (!nameField.isFocused()) {
            nameField.setValue(selection.exportName());
        }
    }

    private void setEndpoint(Endpoint endpoint, net.minecraft.core.BlockPos position) {
        int base = endpoint == Endpoint.POS1 ? 0 : 3;
        if (position == null) {
            for (int offset = 0; offset < 3; offset++) {
                EditBox field = coordinateFields.get(base + offset);
                if (!field.isFocused()) {
                    field.setValue("");
                }
            }
            return;
        }
        setCoordinate(base, position.getX());
        setCoordinate(base + 1, position.getY());
        setCoordinate(base + 2, position.getZ());
    }

    private void setCoordinate(int index, int value) {
        CoordinateEditorModel model = coordinateModels.get(index);
        model.serverValue(value);
        EditBox field = coordinateFields.get(index);
        if (!field.isFocused()) {
            field.setValue(Integer.toString(value));
        }
    }

    private void step(Endpoint endpoint, Axis axis, int direction, boolean shift) {
        int modelIndex = index(endpoint, axis);
        CoordinateEditorModel model = coordinateModels.get(modelIndex);
        model.beginEdit();
        int value = model.step(direction, shift);
        model.endEdit();
        coordinateFields.get(modelIndex).setValue(Integer.toString(value));
        commitEndpoint(endpoint);
    }

    private void toggleOverlay() {
        overlayVisible = !overlayVisible;
        send(new ToggleWandOverlayPayload(overlayVisible));
        updateOverlayButtonText();
    }

    private void syncOverlayFromMenu() {
        overlayVisible = menu.selection().overlayEnabled();
        includePlayers = menu.selection().includePlayers();
        updateOverlayButtonText();
        updateIncludePlayersButtonText();
    }

    private void toggleIncludePlayers() {
        includePlayers = !includePlayers;
        send(new ToggleWandIncludePlayersPayload(includePlayers));
        updateIncludePlayersButtonText();
    }

    private void updateOverlayButtonText() {
        if (overlayButton != null) {
            overlayButton.setMessage(Component.literal(
                    overlayVisible ? "选区显示：开" : "选区显示：关"));
        }
    }

    private void updateIncludePlayersButtonText() {
        if (includePlayersButton != null) {
            includePlayersButton.setMessage(Component.literal(
                    includePlayers ? "导出玩家：开" : "导出玩家：关"));
        }
    }

    private String currentDimension() {
        return minecraft.level.dimension().location().toString();
    }

    private void requestExport() {
        String rawName = nameField.getValue();
        try {
            ExportName name = ExportName.parse(rawName);
            commitExportName();
            send(new ExportWandRequestPayload(name.value()));
            controller.requested(name.value());
            statusLine = "等待服务端授权";
        } catch (IllegalArgumentException exception) {
            statusLine = exception.getMessage();
        }
    }

    private void closeScreen() {
        this.onClose();
    }

    private void commitEndpoint(Endpoint endpoint) {
        int base = endpoint == Endpoint.POS1 ? 0 : 3;
        java.util.OptionalInt x = coordinateModels.get(base).commit();
        java.util.OptionalInt y = coordinateModels.get(base + 1).commit();
        java.util.OptionalInt z = coordinateModels.get(base + 2).commit();
        if (x.isPresent() && y.isPresent() && z.isPresent()) {
            send(new UpdateWandEndpointPayload(endpoint,
                    new net.minecraft.core.BlockPos(
                            x.getAsInt(), y.getAsInt(), z.getAsInt())));
        }
    }

    private void commitExportName() {
        ExportName name = ExportName.parse(nameField.getValue());
        send(new UpdateWandExportNamePayload(name.value()));
    }

    private void send(CustomPacketPayload payload) {
        net.minecraft.client.multiplayer.ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            connection.send(payload);
        }
    }

    private static int index(Endpoint endpoint, Axis axis) {
        return (endpoint == Endpoint.POS1 ? 0 : 3) + axis.ordinal();
    }

    private static Endpoint endpoint(int index) {
        return index < 3 ? Endpoint.POS1 : Endpoint.POS2;
    }

    private static Axis axis(int index) {
        return Axis.values()[index % 3];
    }

    @Override
    public void containerTick() {
        super.containerTick();
        controller.tick();
        commitFocusLosses();
        boolean exporting = controller.state() == ExportWandController.State.EXPORTING;
        boolean nameValid = isNameValid();
        exportButton.active = nameValid && !exporting
                && controller.state() != ExportWandController.State.WAITING_FOR_GRANT;
        cancelButton.active = exporting;
        for (int index = 0; index < coordinateFields.size(); index++) {
            coordinateFields.get(index).setTextColor(
                    coordinateModels.get(index).isInvalid() ? RED : LIGHT);
        }
        nameField.setTextColor(nameValid ? LIGHT : RED);
        updateStatusLine();
    }

    private boolean commitFocusLosses() {
        boolean committed = false;
        for (int index = 0; index < coordinateFields.size(); index++) {
            boolean focused = coordinateFields.get(index).isFocused();
            if (coordinateWasFocused[index] && !focused) {
                commitEndpoint(endpoint(index));
                committed = true;
            }
            coordinateWasFocused[index] = focused;
        }
        boolean focused = nameField.isFocused();
        if (nameWasFocused && !focused && isNameValid()) {
            commitExportName();
            committed = true;
        }
        nameWasFocused = focused;
        return committed;
    }

    private boolean isNameValid() {
        try {
            ExportName.parse(nameField.getValue());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasActiveEdit() {
        for (EditBox field : coordinateFields) {
            if (field.isFocused()) {
                return true;
            }
        }
        return nameField.isFocused();
    }

    private void updateStatusLine() {
        switch (controller.state()) {
            case READY -> statusLine = "就绪";
            case WAITING_FOR_GRANT -> statusLine = "等待服务端授权";
            case EXPORTING -> {
                ExportTelemetry.Snapshot snapshot = controller.telemetry().snapshot();
                statusLine = snapshot.percent() + "% " + snapshot.stageKey();
            }
            case COMPLETED -> statusLine = "导出完成";
            case FAILED -> statusLine = "失败";
            case CANCELLED -> statusLine = "已取消";
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            for (int index = 0; index < coordinateFields.size(); index++) {
                if (coordinateFields.get(index).isFocused()) {
                    commitEndpoint(endpoint(index));
                    return true;
                }
            }
            if (nameField.isFocused()) {
                if (isNameValid()) {
                    commitExportName();
                }
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        super.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        super.keyReleased(keyCode, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        super.charTyped(character, modifiers);
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        for (int index = 0; index < coordinateFields.size(); index++) {
            EditBox field = coordinateFields.get(index);
            if (field.isHovered() || field.isFocused()) {
                step(endpoint(index), axis(index), verticalAmount > 0 ? 1 : -1, hasShiftDown());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawChrome(graphics);
        drawInputSkins(graphics);
        drawHeader(graphics);
        drawLabels(graphics);
    }

    private void drawChrome(GuiGraphics graphics) {
        blitNineSlice(graphics, ExportWandTextures.GUI_016, titleStyle(),
                screenX(Layout.HEADER.x()), screenY(Layout.HEADER.y()),
                Layout.HEADER.width(), Layout.HEADER.height());
        blitNineSlice(graphics, ExportWandTextures.GUI_010, panelStyle(),
                screenX(Layout.LEFT.x()), screenY(Layout.LEFT.y()),
                Layout.LEFT.width(), Layout.LEFT.height());
        blitNineSlice(graphics, ExportWandTextures.GUI_011, panelStyle(),
                screenX(Layout.RIGHT.x()), screenY(Layout.RIGHT.y()),
                Layout.RIGHT.width(), Layout.RIGHT.height());
        blitNineSlice(graphics, ExportWandTextures.GUI_063, titleStyle(),
                screenX(Layout.LOG.x()), screenY(Layout.LOG.y()),
                Layout.LOG.width(), Layout.LOG.height());

        blitNatural(graphics, ExportWandTextures.GUI_001,
                screenX(Layout.HEADER.x() + 3), screenY(Layout.HEADER.y() + 3));
        blitNatural(graphics, ExportWandTextures.GUI_003,
                screenX(Layout.HEADER.right() - 18), screenY(Layout.HEADER.y() + 3));
        blitNineSlice(graphics, ExportWandTextures.GUI_017, titleStyle(),
                screenX(Layout.LEFT.x() + 7), screenY(Layout.LEFT.y() + 5), 125, 15);
        blitNineSlice(graphics, ExportWandTextures.GUI_018, titleStyle(),
                screenX(Layout.LEFT.x() + 7), screenY(Layout.LEFT.y() + 71), 125, 15);
        blitNineSlice(graphics, ExportWandTextures.GUI_019, titleStyle(),
                screenX(Layout.RIGHT.x() + 6), screenY(Layout.RIGHT.y() + 5), 152, 15);
        blitNineSlice(graphics, ExportWandTextures.GUI_070, lineStyle(),
                screenX(Layout.LEFT.x() + 10), screenY(Layout.LEFT.y() + 69), 192, 3);
        blitNineSlice(graphics, ExportWandTextures.GUI_070, lineStyle(),
                screenX(Layout.RIGHT.x() + 10), screenY(Layout.RIGHT.y() + 106), 144, 3);
    }

    private void drawInputSkins(GuiGraphics graphics) {
        for (int index = 0; index < coordinateFields.size(); index++) {
            ExportWandTextures.Texture skin = coordinateModels.get(index).isInvalid()
                    ? ExportWandTextures.GUI_032
                    : coordinateFields.get(index).isFocused()
                    ? ExportWandTextures.GUI_029
                    : ExportWandTextures.GUI_043;
            Rect rect = Layout.coordinateField(index);
            blitNineSlice(graphics, skin, fieldStyleFor(index),
                    screenX(rect.x()), screenY(rect.y()),
                    rect.width(), rect.height());
        }
        ExportWandTextures.Texture nameSkin = !isNameValid()
                ? ExportWandTextures.GUI_032
                : nameField.isFocused()
                ? ExportWandTextures.GUI_030
                : ExportWandTextures.GUI_044;
        Rect nameRect = Layout.nameField();
        blitNineSlice(graphics, nameSkin, fieldStyle(),
                screenX(nameRect.x()), screenY(nameRect.y()),
                nameRect.width(), nameRect.height());
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.drawString(font, Component.literal("导出魔杖"),
                screenX(Layout.HEADER.x() + 22), screenY(Layout.HEADER.y() + 6), LIGHT, false);
        graphics.drawString(font, Component.literal(McGltf.DISPLAY_NAME + " " + McGltf.VERSION),
                screenX(Layout.HEADER.right() - 112), screenY(Layout.HEADER.y() + 6), MID, false);
    }

    private void drawLabels(GuiGraphics graphics) {
        Rect firstTitle = Layout.endpointTitle(Endpoint.POS1);
        Rect secondTitle = Layout.endpointTitle(Endpoint.POS2);
        blitNatural(graphics, ExportWandTextures.GUI_068,
                screenX(firstTitle.x()), screenY(firstTitle.y() - 1));
        graphics.drawString(font, Component.literal("起点 FIRST"),
                screenX(firstTitle.x() + 16), screenY(firstTitle.y() + 1), ORANGE, false);
        blitNatural(graphics, ExportWandTextures.GUI_069,
                screenX(secondTitle.x()), screenY(secondTitle.y() - 1));
        graphics.drawString(font, Component.literal("终点 SECOND"),
                screenX(secondTitle.x() + 16), screenY(secondTitle.y() + 1), BLUE, false);

        for (int index = 0; index < coordinateFields.size(); index++) {
            Rect field = Layout.coordinateField(index);
            graphics.drawString(font, Component.literal(axis(index).name()),
                    screenX(Layout.LEFT.x() + 15), screenY(field.y() + 3), LIGHT, false);
        }

        blitNatural(graphics, ExportWandTextures.GUI_073,
                screenX(Layout.RIGHT.x() + 8), screenY(Layout.RIGHT.y() + 5));
        graphics.drawString(font, Component.literal("导出控制"),
                screenX(Layout.RIGHT.x() + 34), screenY(Layout.RIGHT.y() + 10), BLUE, false);
        graphics.drawString(font, Component.literal("导出名"),
                screenX(Layout.RIGHT.x() + 8), screenY(Layout.RIGHT.y() + 24), LIGHT, false);
        drawProgress(graphics);
        drawSummary(graphics);
        drawStatusIcon(graphics);
    }

    private void drawProgress(GuiGraphics graphics) {
        int right = Layout.RIGHT.x();
        int top = Layout.RIGHT.y();
        ExportWandController.State state = controller.state();
        int percent = state == ExportWandController.State.EXPORTING
                ? controller.telemetry().snapshot().percent()
                : state == ExportWandController.State.COMPLETED ? 100 : 0;
        blitNineSlice(graphics, ExportWandTextures.GUI_066, progressStyle(),
                screenX(right + 8), screenY(top + 114), 140, 9);
        if (percent > 0) {
            blitNineSlice(graphics, ExportWandTextures.GUI_067, lineStyle(),
                    screenX(right + 8), screenY(top + 114),
                    Math.max(2, 140 * percent / 100), 9);
        }
        if (state == ExportWandController.State.EXPORTING) {
            ExportTelemetry.Snapshot snapshot = controller.telemetry().snapshot();
            graphics.drawString(font, Component.literal(
                            snapshot.percent() + "% · " + snapshot.stageKey()),
                    screenX(right + 8), screenY(top + 128), BLUE, false);
            graphics.drawString(font, Component.literal(snapshot.currentObjectId()),
                    screenX(right + 8), screenY(top + 140), MID, false);
            graphics.drawString(font, Component.literal("队列 " + snapshot.queueDepth()),
                    screenX(right + 8), screenY(top + 152), MID, false);
        }
    }

    private void drawSummary(GuiGraphics graphics) {
        int right = Layout.RIGHT.x();
        int top = Layout.RIGHT.y();
        ExportSummary summary = controller.summary().orElse(null);
        if (summary == null) {
            return;
        }
        int color = switch (controller.state()) {
            case COMPLETED -> ORANGE;
            case CANCELLED -> MID;
            default -> RED;
        };
        blitNatural(graphics, ExportWandTextures.GUI_075,
                screenX(right + 8), screenY(top + 127));
        graphics.drawString(font, Component.literal(summary.status()),
                screenX(right + 30), screenY(top + 128), color, false);
        summary.outputDirectory().ifPresent(directory -> graphics.drawString(font,
                Component.literal(directory.getFileName().toString()),
                screenX(right + 30), screenY(top + 140), LIGHT, false));
        graphics.drawString(font, Component.literal(
                        summary.nodeCount() + " 对象 · " + summary.primitiveCount() + " 基元"),
                screenX(right + 8), screenY(top + 153), LIGHT, false);
    }

    private void drawStatusIcon(GuiGraphics graphics) {
        ExportWandTextures.Texture icon = switch (controller.state()) {
            case COMPLETED -> ExportWandTextures.GUI_076;
            case FAILED -> ExportWandTextures.GUI_074;
            case CANCELLED -> ExportWandTextures.GUI_077;
            case EXPORTING, WAITING_FOR_GRANT -> ExportWandTextures.GUI_071;
            default -> ExportWandTextures.GUI_072;
        };
        blitNatural(graphics, icon,
                screenX(Layout.LOG.right() - icon.width() - 5),
                screenY(Layout.LOG.y() + (Layout.LOG.height() - icon.height()) / 2));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, statusLine,
                Layout.LOG.x() + 8, Layout.LOG.y() + 5, LIGHT, false);
    }

    @Override
    public void onClose() {
        if (nameField != null && isNameValid()) {
            commitExportName();
        }
        controller.screenClosed();
        controller.unbind();
        controllerBound = false;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int screenX(int localX) {
        return leftPos + localX;
    }

    private int screenY(int localY) {
        return topPos + localY;
    }

    private static void blitNatural(
            GuiGraphics graphics, ExportWandTextures.Texture texture, int x, int y) {
        graphics.blit(texture.location(), x, y, 0.0F, 0.0F,
                texture.width(), texture.height(), texture.width(), texture.height());
    }

    private static void blitNineSlice(
            GuiGraphics graphics, ExportWandTextures.Texture texture,
            NineSliceStyle style, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int border = ExportWandBorderPolicy.logicalBorder(
                style.physicalBorder(),
                Minecraft.getInstance().getWindow().getGuiScale(),
                texture.width(), texture.height(), width, height);
        int borderX = border;
        int borderY = border;
        int sourceCenterWidth = texture.width() - borderX * 2;
        int sourceCenterHeight = texture.height() - borderY * 2;
        int centerWidth = width - borderX * 2;
        int centerHeight = height - borderY * 2;

        blitRegion(graphics, texture, x, y,
                0, 0, borderX, borderY);
        blitRegion(graphics, texture, x + width - borderX, y,
                texture.width() - borderX, 0, borderX, borderY);
        blitRegion(graphics, texture, x, y + height - borderY,
                0, texture.height() - borderY, borderX, borderY);
        blitRegion(graphics, texture, x + width - borderX, y + height - borderY,
                texture.width() - borderX, texture.height() - borderY,
                borderX, borderY);

        blitStretchedRegion(graphics, texture,
                x + borderX, y, centerWidth, borderY,
                borderX, 0, sourceCenterWidth, borderY);
        blitStretchedRegion(graphics, texture,
                x + borderX, y + height - borderY, centerWidth, borderY,
                borderX, texture.height() - borderY, sourceCenterWidth, borderY);
        blitStretchedRegion(graphics, texture,
                x, y + borderY, borderX, centerHeight,
                0, borderY, borderX, sourceCenterHeight);
        blitStretchedRegion(graphics, texture,
                x + width - borderX, y + borderY, borderX, centerHeight,
                texture.width() - borderX, borderY, borderX, sourceCenterHeight);
        blitStretchedRegion(graphics, texture,
                x + borderX, y + borderY, centerWidth, centerHeight,
                borderX, borderY, sourceCenterWidth, sourceCenterHeight);
    }

    private static void blitStretchedRegion(
            GuiGraphics graphics, ExportWandTextures.Texture texture,
            int destinationX, int destinationY, int destinationWidth, int destinationHeight,
            int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        if (destinationWidth <= 0 || destinationHeight <= 0
                || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(destinationX, destinationY, 0.0F);
        graphics.pose().scale(
                (float) destinationWidth / sourceWidth,
                (float) destinationHeight / sourceHeight,
                1.0F);
        graphics.blit(texture.location(), 0, 0,
                (float) sourceX, (float) sourceY,
                sourceWidth, sourceHeight, texture.width(), texture.height());
        graphics.pose().popPose();
    }

    private static void blitRegion(
            GuiGraphics graphics, ExportWandTextures.Texture texture,
            int destinationX, int destinationY,
            int sourceX, int sourceY, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.blit(texture.location(), destinationX, destinationY,
                (float) sourceX, (float) sourceY, width, height,
                texture.width(), texture.height());
    }

    private static final class SkinnedButton extends Button {
        private final Font font;
        private final ExportWandTextures.Texture normal;
        private final ExportWandTextures.Texture hovered;
        private final ExportWandTextures.Texture disabled;
        private final ExportWandTextures.Texture selected;
        private final BooleanSupplier selectedState;
        private final boolean drawMessage;
        private final ExportWandTextures.Texture indicatorOff;
        private final ExportWandTextures.Texture indicatorOn;

        private SkinnedButton(
                Builder builder, Font font,
                ExportWandTextures.Texture normal,
                ExportWandTextures.Texture hovered,
                ExportWandTextures.Texture disabled,
                ExportWandTextures.Texture selected,
                BooleanSupplier selectedState,
                boolean drawMessage,
                ExportWandTextures.Texture indicatorOff,
                ExportWandTextures.Texture indicatorOn) {
            super(builder);
            this.font = font;
            this.normal = normal;
            this.hovered = hovered;
            this.disabled = disabled;
            this.selected = selected;
            this.selectedState = selectedState;
            this.drawMessage = drawMessage;
            this.indicatorOff = indicatorOff;
            this.indicatorOn = indicatorOn;
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ExportWandTextures.Texture texture;
            if (!active) {
                texture = disabled;
            } else if (selected != null && selectedState.getAsBoolean()) {
                texture = selected;
            } else if (isHoveredOrFocused()) {
                texture = hovered;
            } else {
                texture = normal;
            }
            blitNineSlice(graphics, texture, buttonStyle(),
                    getX(), getY(), getWidth(), getHeight());
            ExportWandTextures.Texture indicator = selectedState.getAsBoolean()
                    ? indicatorOn : indicatorOff;
            if (indicator != null) {
                blitNatural(graphics, indicator,
                        getX() + getWidth() - indicator.width() - 3,
                        getY() + (getHeight() - indicator.height()) / 2);
            }
            if (drawMessage) {
                int color = !active ? MID
                        : selectedState.getAsBoolean() ? BLUE : LIGHT;
                int indicatorOffset = indicator == null ? 0 : 14;
                graphics.drawCenteredString(font, getMessage(),
                        getX() + getWidth() / 2 - indicatorOffset,
                        getY() + (getHeight() - 8) / 2,
                        color);
            }
        }
    }
}
