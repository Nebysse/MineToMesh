package com.onecuber.mcgltf.client.workstation;

import com.onecuber.mcgltf.job.ExportSummary;
import com.onecuber.mcgltf.job.ExportTelemetry;
import com.onecuber.mcgltf.network.CaptureFeetPayload;
import com.onecuber.mcgltf.network.ExportRequestPayload;
import com.onecuber.mcgltf.network.UpdateCoordinatePayload;
import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.workstation.Axis;
import com.onecuber.mcgltf.workstation.Endpoint;
import com.onecuber.mcgltf.workstation.ExportWorkstationMenu;
import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

public class ExportWorkstationScreen extends AbstractContainerScreen<ExportWorkstationMenu> {
    private static final int ORANGE = 0xFFED741C;
    private static final int BLUE = 0xFF3488D8;
    private static final int DARK = 0xFF1B1E23;
    private static final int MID = 0xFF3C424A;
    private static final int LIGHT = 0xFFC8CDD3;
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

    public static final class Layout {
        public static final Rect HEADER = new Rect(0, 0, 384, 20);
        public static final Rect LEFT = new Rect(4, 24, 208, 166);
        public static final Rect RIGHT = new Rect(216, 24, 164, 166);
        public static final Rect LOG = new Rect(4, 194, 376, 18);

        private Layout() {
        }
    }

    private final WorkstationExportController controller;
    private final SelectionOverlayState overlayState;
    private final List<CoordinateEditorModel> coordinateModels = new ArrayList<>();
    private final List<EditBox> coordinateFields = new ArrayList<>();
    private EditBox nameField;
    private Button exportButton;
    private Button cancelButton;
    private Button overlayButton;
    private boolean overlayVisible;
    private String statusLine = "";

    public ExportWorkstationScreen(
            ExportWorkstationMenu menu,
            Inventory inventory,
            Component title,
            WorkstationExportController controller,
            SelectionOverlayState overlayState) {
        super(menu, inventory, title);
        this.controller = controller;
        this.overlayState = overlayState;
        this.imageWidth = 384;
        this.imageHeight = 216;
    }

    @Override
    protected void init() {
        super.init();
        coordinateModels.clear();
        coordinateFields.clear();
        createCoordinateWidgets();
        createActionWidgets();
        refreshFromMenu();
        statusLine = "";
    }

    private void createCoordinateWidgets() {
        int left = Layout.LEFT.x();
        int top = Layout.LEFT.y();
        int row = 0;
        for (Endpoint endpoint : Endpoint.values()) {
            for (Axis axis : Axis.values()) {
                CoordinateEditorModel model = new CoordinateEditorModel();
                coordinateModels.add(model);
                int fieldX = left + 40;
                int fieldY = top + 40 + row * 16;
                EditBox field = new EditBox(font, fieldX, fieldY, 72, 14,
                        Component.literal(axis.name()));
                field.setMaxLength(11);
                field.setFilter(text -> text.matches("-?\\d*"));
                field.setResponder(model::setText);
                addRenderableWidget(field);
                coordinateFields.add(field);
                int buttonY = fieldY - 2;
                addRenderableWidget(Button.builder(Component.literal("+"),
                                pressed -> step(endpoint, axis, 1, false))
                        .bounds(left + 118, buttonY, 16, 16)
                        .build());
                addRenderableWidget(Button.builder(Component.literal("-"),
                                pressed -> step(endpoint, axis, -1, false))
                        .bounds(left + 136, buttonY, 16, 16)
                        .build());
                row++;
            }
            addRenderableWidget(Button.builder(Component.literal("脚下"),
                            pressed -> captureFeet(endpoint))
                    .bounds(left + 158, top + 40 + (row - 3) * 16 + 2, 42, 12)
                    .build());
        }
        overlayButton = addRenderableWidget(Button.builder(Component.literal("选区显示"),
                        pressed -> toggleOverlay())
                .bounds(left + 12, top + 128, 188, 16)
                .build());
    }

    private void createActionWidgets() {
        int right = Layout.RIGHT.x();
        int top = Layout.RIGHT.y();
        nameField = new EditBox(font, right + 8, top + 32, 148, 16,
                Component.literal("导出名"));
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);
        exportButton = addRenderableWidget(Button.builder(
                        Component.literal("导出"), pressed -> requestExport())
                .bounds(right + 8, top + 56, 148, 18)
                .build());
        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("取消"), pressed -> closeScreen())
                .bounds(right + 8, top + 78, 148, 18)
                .build());
        cancelButton.active = false;
    }

    private void refreshFromMenu() {
        WorkstationCoordinates coordinates = menu.coordinates();
        setCoordinate(0, coordinates.first().getX());
        setCoordinate(1, coordinates.first().getY());
        setCoordinate(2, coordinates.first().getZ());
        setCoordinate(3, coordinates.second().getX());
        setCoordinate(4, coordinates.second().getY());
        setCoordinate(5, coordinates.second().getZ());
        nameField.setValue(defaultName());
    }

    private void setCoordinate(int index, int value) {
        CoordinateEditorModel model = coordinateModels.get(index);
        model.serverValue(value);
        EditBox field = coordinateFields.get(index);
        field.setValue(Integer.toString(value));
    }

    private static String defaultName() {
        return "workstation_export";
    }

    private void step(Endpoint endpoint, Axis axis, int direction, boolean shift) {
        int modelIndex = index(endpoint, axis);
        CoordinateEditorModel model = coordinateModels.get(modelIndex);
        model.beginEdit();
        int value = model.step(direction, shift);
        model.endEdit();
        coordinateFields.get(modelIndex).setValue(Integer.toString(value));
        send(new UpdateCoordinatePayload(menu.stationPos(), endpoint, axis, value));
        refreshFromMenu();
    }

    private void captureFeet(Endpoint endpoint) {
        send(new CaptureFeetPayload(menu.stationPos(), endpoint));
    }

    private void toggleOverlay() {
        OverlayKey key = new OverlayKey(currentDimension(), menu.stationPos());
        WorkstationCoordinates coordinates = menu.coordinates();
        overlayState.toggle(key, coordinates);
        overlayVisible = overlayState.visible(key);
    }

    private String currentDimension() {
        return minecraft.level.dimension().location().toString();
    }

    private void requestExport() {
        String rawName = nameField.getValue();
        try {
            ExportName name = ExportName.parse(rawName);
            send(new ExportRequestPayload(menu.stationPos(), name.value()));
            controller.requested(name.value());
            statusLine = "等待服务端授权";
        } catch (IllegalArgumentException exception) {
            statusLine = exception.getMessage();
        }
    }

    private void closeScreen() {
        this.onClose();
    }

    private void commit(int modelIndex) {
        CoordinateEditorModel model = coordinateModels.get(modelIndex);
        model.beginEdit();
        model.commit().ifPresent(value -> {
            Endpoint endpoint = endpoint(modelIndex);
            Axis axis = axis(modelIndex);
            send(new UpdateCoordinatePayload(
                    menu.stationPos(), endpoint, axis, value));
        });
        model.endEdit();
    }

    private void send(CustomPacketPayload payload) {
        net.minecraft.client.multiplayer.ClientPacketListener connection =
                minecraft.getConnection();
        if (connection != null) {
            connection.send(payload);
        }
    }

    private static int index(Endpoint endpoint, Axis axis) {
        return (endpoint == Endpoint.FIRST ? 0 : 3) + axis.ordinal();
    }

    private static Endpoint endpoint(int index) {
        return index < 3 ? Endpoint.FIRST : Endpoint.SECOND;
    }

    private static Axis axis(int index) {
        return Axis.values()[index % 3];
    }

    @Override
    public void containerTick() {
        super.containerTick();
        controller.tick();
        if (!hasActiveEdit()) {
            refreshFromMenu();
        }
        boolean exporting = controller.state() == WorkstationExportController.State.EXPORTING;
        exportButton.active = !exporting
                && controller.state() != WorkstationExportController.State.WAITING_FOR_GRANT;
        cancelButton.active = exporting;
        updateStatusLine();
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
                    commit(index);
                    return true;
                }
            }
            if (nameField.isFocused()) {
                requestExport();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int index = 0; index < coordinateFields.size(); index++) {
            EditBox field = coordinateFields.get(index);
            if (field.isHovered() || field.isFocused()) {
                boolean shift = hasShiftDown();
                step(endpoint(index), axis(index),
                        verticalAmount > 0 ? 1 : -1, shift);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        fill(graphics, Layout.HEADER, DARK);
        fill(graphics, Layout.LEFT, 0xFF25292E);
        fill(graphics, Layout.RIGHT, 0xFF25292E);
        fill(graphics, Layout.LOG, 0xFF1B1E23);
        drawHeader(graphics);
        drawLabels(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.drawString(font, Component.literal("区域导出工作台"),
                Layout.HEADER.x() + 8, Layout.HEADER.y() + 6, LIGHT, false);
        graphics.drawString(font, Component.literal("MineToMesh 0.3.0"),
                Layout.HEADER.right() - 96, Layout.HEADER.y() + 6, MID, false);
    }

    private void drawLabels(GuiGraphics graphics) {
        int left = Layout.LEFT.x();
        int top = Layout.LEFT.y();
        graphics.drawString(font, Component.literal("起点 FIRST"),
                left + 12, top + 14, ORANGE, false);
        graphics.drawString(font, Component.literal("终点 SECOND"),
                left + 12, top + 62, BLUE, false);
        for (int index = 0; index < coordinateFields.size(); index++) {
            int row = index % 3;
            graphics.drawString(font, Component.literal(axis(index).name()),
                    left + 16, top + 40 + row * 16 + 3, LIGHT, false);
        }
        int right = Layout.RIGHT.x();
        graphics.drawString(font, Component.literal("导出名"),
                right + 8, top + 20, LIGHT, false);
        drawProgress(graphics);
        drawSummary(graphics);
    }

    private void drawProgress(GuiGraphics graphics) {
        int right = Layout.RIGHT.x();
        int top = Layout.RIGHT.y();
        WorkstationExportController.State state = controller.state();
        if (state == WorkstationExportController.State.EXPORTING) {
            ExportTelemetry.Snapshot snapshot = controller.telemetry().snapshot();
            graphics.drawString(font, Component.literal(
                            snapshot.percent() + "% · " + snapshot.stageKey()),
                    right + 8, top + 104, BLUE, false);
            graphics.drawString(font, Component.literal(snapshot.currentObjectId()),
                    right + 8, top + 116, MID, false);
            graphics.drawString(font, Component.literal(
                            "队列 " + snapshot.queueDepth()),
                    right + 8, top + 128, MID, false);
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
        graphics.drawString(font, Component.literal(summary.status()),
                right + 8, top + 104, color, false);
        summary.outputDirectory().ifPresent(directory ->
                graphics.drawString(font, Component.literal(
                                directory.getFileName().toString()),
                        right + 8, top + 116, LIGHT, false));
        graphics.drawString(font, Component.literal(
                        summary.nodeCount() + " 对象 · " + summary.primitiveCount() + " 基元"),
                right + 8, top + 128, LIGHT, false);
        graphics.drawString(font, Component.literal(
                        summary.textureCount() + " 纹理 · " + summary.warningCount() + " 警告"),
                right + 8, top + 140, LIGHT, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, statusLine,
                Layout.LOG.x() + 8, Layout.LOG.y() + 5, LIGHT, false);
    }

    @Override
    public void onClose() {
        controller.screenClosed();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void fill(GuiGraphics graphics, Rect rect, int color) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
    }
}
