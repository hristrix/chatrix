package com.hristrix.chatrix.ui.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.font.TextRenderer;
import org.jetbrains.annotations.Nullable;
import com.hristrix.chatrix.config.MessageSpawnMode;
import com.hristrix.chatrix.config.HudAnchor;
import com.hristrix.chatrix.config.UnpinMode;
import com.hristrix.chatrix.config.ConfigManager;
import com.hristrix.chatrix.config.ModConfig;
import com.hristrix.chatrix.config.ModConfigData;
import com.hristrix.chatrix.integration.chat.ChatConnectionManager;
import com.hristrix.chatrix.ChatrixClient;
import com.hristrix.chatrix.utils.ScreenNavigationCompat;
import com.hristrix.chatrix.ui.gui.GuiScrollbar;
import com.hristrix.chatrix.ui.gui.ModUiTheme;
import com.hristrix.chatrix.ui.widget.ChanceForSpawnSliderWidget;
import com.hristrix.chatrix.ui.widget.ConfigIntTextFieldWidget;
import com.hristrix.chatrix.ui.widget.ConfigIntSliderWidget;
import com.hristrix.chatrix.ui.widget.MessageSoundVolumeSliderWidget;
import com.hristrix.chatrix.ui.gui.ConfigUiHelper;
import com.hristrix.chatrix.ui.gui.ChatConnectToggleHelper;
import com.hristrix.chatrix.ui.gui.ScreenUiHelper;
import com.hristrix.chatrix.ui.widget.ColorFieldRow;
import com.hristrix.chatrix.ui.widget.PlatformSettingsCard;
import com.hristrix.chatrix.ui.widget.ToggleWithSettingsRow;
import java.util.ArrayList;
import java.util.List;

public class ModConfigScreen extends Screen {
    private final @Nullable Screen parent;
    private String hoveredDescriptionKey;
    
    // Settings categories
    private enum ConfigCategory {
        GENERAL("chatrix.config.category.general", "icon_tab_general"),
        MESSAGES("chatrix.config.category.messages", "icon_tab_messages"),
        LAYOUT("chatrix.config.category.layout", "icon_tab_world");
        
        private final String translationKey;
        private final Identifier icon;
        
        ConfigCategory(String translationKey, String iconName) {
            this.translationKey = translationKey;
            this.icon = Identifier.of("chatrix", "textures/gui/" + iconName + ".png");
        }
        
        public Text getText() {
            return Text.translatable(translationKey);
        }

        public Identifier getIcon() {
            return icon;
        }
    }
    
    private static final Identifier ICON_HISTORY =
        Identifier.of("chatrix", "textures/gui/icon_history.png");
    private static final Identifier ICON_SETTINGS =
        Identifier.of("chatrix", "textures/gui/icon_settings.png");
    private static final int BUTTON_ICON_SIZE = 10;
    private static final int BUTTON_ICON_GAP = 3;

    private ConfigCategory currentCategory = ConfigCategory.GENERAL;
    private List<ButtonWidget> categoryButtons = new ArrayList<>();
    private List<ConfigEntry> configEntries = new ArrayList<>();
    /** Last 3D mode, retained when switching back from HUD to Around or In front. */
    private static MessageSpawnMode lastWorldSpawnMode = MessageSpawnMode.FRONT_OF_PLAYER;
    private ButtonWidget displayModeButton;
    private ButtonWidget worldPlacementButton;
    private ButtonWidget historyButton;
    private ButtonWidget chatToggleButton;
    private ButtonWidget doneButton;
    private ButtonWidget resetButton;
    private boolean resetArmed;
    
    // Interface settings
    private static final int TITLE_Y = 6;
    private static final int CATEGORY_Y = 38;
    private static final int MAIN_PANEL_BOTTOM_MARGIN = 12;
    private static final int SCROLL_TO_DESCRIPTION_GAP = 4;
    private static final int DESCRIPTION_TO_BUTTON_GAP = 3;
    private static final int CATEGORY_BUTTON_HEIGHT = 22;
    private static final int ENTRY_HEIGHT = 24;
    private static final int ENTRY_SPACING = 6;
    private static final int CONTENT_PADDING = 10;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int FOOTER_BUTTON_BOTTOM_PADDING = 10;
    private static final int FOOTER_ZONE_HEIGHT = FOOTER_BUTTON_HEIGHT + FOOTER_BUTTON_BOTTOM_PADDING;
    private static final int FOOTER_BUTTON_PADDING = 12;
    private static final int FOOTER_BUTTON_GAP = 8;
    private static final int CATEGORY_TO_CONTENT_GAP = 6;
    private static final int SIDE_MARGIN = 24;
    private static final int CONTROL_WIDTH = 170;
    private static final int TOGGLE_BUTTON_WIDTH = 44;
    private static final int CONTROL_HEIGHT = 20;
    private static final int DESCRIPTION_HEIGHT = 20;
    private static final int CARD_HEIGHT = 32;
    private static final int FEATURE_HEIGHT = 46;
    private static final int FEATURE_BUTTON_HEIGHT = 24;
    private static final int CARD_GAP = 6;
    private static final int CARD_INNER_PADDING = 6;
    private static final int CARD_SETTINGS_WIDTH = 22;
    private static final int COLOR_PICKER_SIZE = 20;

    private int scrollOffset = 0;

    // Configuration entry model
    private static class ConfigEntry {
        public final String labelKey;
        public final String descriptionKey;
        public final ConfigEntryType type;
        public final Object widget;
        public final ConfigCategory category;
        /** Preview color for {@link ConfigEntryType#COLOR_FIELD}. */
        public final java.util.function.IntSupplier colorPreview;
        /** The row is visible only when its prerequisite is met. */
        private java.util.function.BooleanSupplier visibleWhen;
        /** The row is enabled only when its prerequisite is met. */
        private java.util.function.BooleanSupplier enabledWhen;
        /** Row position calculated by {@link #layoutEntries()}. */
        public int rowX;
        public int rowY;
        public int rowWidth;

        public ConfigEntry(String labelKey, String descriptionKey, ConfigEntryType type, Object widget, ConfigCategory category) {
            this(labelKey, descriptionKey, type, widget, category, null);
        }

        public ConfigEntry(
            String labelKey,
            String descriptionKey,
            ConfigEntryType type,
            Object widget,
            ConfigCategory category,
            java.util.function.IntSupplier colorPreview
        ) {
            this.labelKey = labelKey;
            this.descriptionKey = descriptionKey;
            this.type = type;
            this.widget = widget;
            this.category = category;
            this.colorPreview = colorPreview;
        }

        /** Row height; platform cards are taller than ordinary settings rows. */
        public int rowHeight() {
            if (type == ConfigEntryType.FEATURE) {
                return FEATURE_HEIGHT;
            }
            return type == ConfigEntryType.PLATFORM_CARD ? CARD_HEIGHT : ENTRY_HEIGHT;
        }

        public ConfigEntry visibleWhen(java.util.function.BooleanSupplier condition) {
            this.visibleWhen = condition;
            return this;
        }

        public ConfigEntry enabledWhen(java.util.function.BooleanSupplier condition) {
            this.enabledWhen = condition;
            return this;
        }

        public boolean isVisible() {
            return visibleWhen == null || visibleWhen.getAsBoolean();
        }

        public boolean isEnabled() {
            return enabledWhen == null || enabledWhen.getAsBoolean();
        }
    }

    private enum ConfigEntryType {
        TEXT_FIELD, BUTTON, DANGER_BUTTON, TOGGLE, SLIDER, COLOR_FIELD, PLATFORM_CARD, TOGGLE_WITH_SETTINGS, FEATURE
    }

    public ModConfigScreen() {
        this(null);
    }

    public ModConfigScreen(@Nullable Screen parent) {
        super(Text.translatable("chatrix.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Create category buttons.
        createCategoryButtons();
        
        // Create configuration widgets.
        createConfigEntries();
        
        // Create bottom-screen buttons.
        createBottomButtons();
        
        // Update widget visibility for the current category.
        updateCategoryVisibility();
    }
    
    private void createCategoryButtons() {
        categoryButtons.clear();
        int count = ConfigCategory.values().length;
        int buttonSpacing = 6;
        int availableWidth = this.width - SIDE_MARGIN * 2 - buttonSpacing * (count - 1);
        int buttonWidth = Math.max(86, Math.min(140, availableWidth / count));
        int totalWidth = count * buttonWidth + buttonSpacing * (count - 1);
        int startX = (this.width - totalWidth) / 2;
        int y = CATEGORY_Y;
        
        for (int i = 0; i < count; i++) {
            ConfigCategory category = ConfigCategory.values()[i];
            ButtonWidget button = ButtonWidget.builder(
                category.getText(),
                btn -> {
                    if (currentCategory != category) {
                        disarmResetButton();
                    }
                    currentCategory = category;
                    updateCategoryVisibility();
                }
            ).dimensions(startX + i * (buttonWidth + buttonSpacing), y, buttonWidth, CATEGORY_BUTTON_HEIGHT).build();
            
            categoryButtons.add(button);
            this.addDrawableChild(button);
        }
    }
    
    private void createConfigEntries() {
        configEntries.clear();
        rememberWorldModeIfNeeded();
        TextRenderer textRenderer = this.textRenderer;

        // Platforms: two cards per row, with detailed settings on separate pages.
        for (com.hristrix.chatrix.integration.chat.ChatPlatform platform
            : com.hristrix.chatrix.integration.chat.ChatPlatform.values()) {
            addPlatformCard(platform);
        }

        addToggleEntry(
            "chatrix.config.auto_connect_irc",
            "chatrix.config.auto_connect_irc.desc",
            ModConfig::isAUTO_CONNECT_IRC_ON_JOIN,
            value -> ModConfig.setAUTO_CONNECT_IRC_ON_JOIN(value),
            ConfigCategory.GENERAL
        );

        ChanceForSpawnSliderWidget chanceForSpawnSlider = new ChanceForSpawnSliderWidget(0, 0, CONTROL_WIDTH, 20);
        this.addDrawableChild(chanceForSpawnSlider);
        configEntries.add(new ConfigEntry("chatrix.config.chance_for_spawn", "chatrix.config.chance_for_spawn.desc", ConfigEntryType.SLIDER, chanceForSpawnSlider, ConfigCategory.GENERAL));

        addToggleWithSettingsEntry(
            "chatrix.config.automoderation",
            "chatrix.config.automoderation.desc",
            ModConfig::isENABLE_AUTOMODERATION,
            ModConfig::setENABLE_AUTOMODERATION,
            () -> ScreenNavigationCompat.open(this.client, new BanwordConfigScreen(this))
        );

        addToggleWithSettingsEntry(
            "chatrix.config.username_blocklist",
            "chatrix.config.username_blocklist.desc",
            ModConfig::isENABLE_USERNAME_BLOCKLIST,
            ModConfig::setENABLE_USERNAME_BLOCKLIST,
            () -> ScreenNavigationCompat.open(this.client, new BlockedUsernameConfigScreen(this))
        );

        ButtonWidget regexpButton = ButtonWidget.builder(
            Text.translatable("chatrix.config.regexps_config"),
            btn -> ScreenNavigationCompat.open(this.client, new RegexpConfigScreen(this))
        ).dimensions(0, 0, CONTROL_WIDTH, 20).build();
        this.addDrawableChild(regexpButton);
        configEntries.add(new ConfigEntry("chatrix.config.regexps", "chatrix.config.regexps.desc", ConfigEntryType.BUTTON, regexpButton, ConfigCategory.GENERAL));

        resetArmed = false;
        resetButton = ButtonWidget.builder(
            Text.translatable("chatrix.config.reset_defaults.button"),
            btn -> onResetDefaultsClicked()
        ).dimensions(0, 0, CONTROL_WIDTH, 20).build();
        this.addDrawableChild(resetButton);
        configEntries.add(new ConfigEntry(
            "chatrix.config.reset_defaults",
            "chatrix.config.reset_defaults.desc",
            ConfigEntryType.DANGER_BUTTON,
            resetButton,
            ConfigCategory.GENERAL
        ));

        // The visual editor is the tab's primary feature, followed by detailed settings.
        var client = this.client;
        boolean hasWorld = client != null && client.world != null;
        ButtonWidget editorButton = ButtonWidget.builder(
            Text.translatable("chatrix.editor.open"),
            btn -> ScreenNavigationCompat.open(this.client, new MessageEditorScreen(this))
        ).dimensions(0, 0, CONTROL_WIDTH, FEATURE_BUTTON_HEIGHT).build();
        editorButton.active = hasWorld;
        this.addDrawableChild(editorButton);
        configEntries.add(new ConfigEntry(
            "chatrix.editor.title",
            hasWorld
                ? "chatrix.editor.desc"
                : "chatrix.editor.desc_no_world",
            ConfigEntryType.FEATURE,
            editorButton,
            ConfigCategory.MESSAGES
        ).enabledWhen(this::hasWorldClient));

        // Appearance: world or HUD first, then general bubble styling.
        displayModeButton = ButtonWidget.builder(
            getDisplayModeButtonText(),
            btn -> toggleDisplayKind()
        ).dimensions(0, 0, CONTROL_WIDTH, 20).build();
        this.addDrawableChild(displayModeButton);
        configEntries.add(new ConfigEntry(
            "chatrix.config.display_mode",
            "chatrix.config.display_mode.desc",
            ConfigEntryType.BUTTON,
            displayModeButton,
            ConfigCategory.MESSAGES
        ));

        ConfigIntSliderWidget messageSizeSlider = createIntSlider(
            "messageSizePercent", ModConfigData.MIN_SIZE_PERCENT, ModConfigData.MAX_SIZE_PERCENT,
            ModConfigData.SIZE_PERCENT_STEP, 100, value -> Text.literal(value + "%")
        );
        addEntry(new ConfigEntry("chatrix.config.message_scale", "chatrix.config.message_scale.desc", ConfigEntryType.SLIDER, messageSizeSlider, ConfigCategory.MESSAGES))
            .visibleWhen(ModConfigScreen::isWorldMode);

        ConfigIntSliderWidget hudSizeSlider = createIntSlider(
            "hudSizePercent", ModConfigData.MIN_SIZE_PERCENT, ModConfigData.MAX_SIZE_PERCENT,
            ModConfigData.SIZE_PERCENT_STEP, 100, value -> Text.literal(value + "%")
        );
        addEntry(new ConfigEntry("chatrix.config.hud_scale", "chatrix.config.hud_scale.desc", ConfigEntryType.SLIDER, hudSizeSlider, ConfigCategory.MESSAGES))
            .visibleWhen(ModConfigScreen::isHudWidgetMode);

        ConfigIntSliderWidget wrapWidthSlider = createIntSlider(
            "messageWrapWidth", ModConfigData.MIN_WRAP_WIDTH, ModConfigData.MAX_WRAP_WIDTH,
            ModConfigData.WRAP_WIDTH_STEP, ModConfigData.DEFAULT_WRAP_WIDTH,
            value -> Text.translatable("chatrix.config.message_wrap_width.value", value)
        );
        addEntry(new ConfigEntry("chatrix.config.message_wrap_width", "chatrix.config.message_wrap_width.desc", ConfigEntryType.SLIDER, wrapWidthSlider, ConfigCategory.MESSAGES));

        ConfigIntTextFieldWidget messageLifetimeField = new ConfigIntTextFieldWidget(
            textRenderer,
            0,
            0,
            CONTROL_WIDTH,
            20,
            Text.translatable("chatrix.config.message_lifetime_seconds"),
            "messageLifetimeSeconds",
            1,
            600
        );
        this.addDrawableChild(messageLifetimeField);
        configEntries.add(new ConfigEntry("chatrix.config.message_lifetime_seconds", "chatrix.config.message_lifetime_seconds.desc", ConfigEntryType.TEXT_FIELD, messageLifetimeField, ConfigCategory.MESSAGES));

        ButtonWidget showBgButton = createToggleButton(ModConfig::isSHOW_MESSAGE_BACKGROUND, ModConfig::setSHOW_MESSAGE_BACKGROUND);
        this.addDrawableChild(showBgButton);
        configEntries.add(new ConfigEntry("chatrix.config.show_message_bg", "chatrix.config.show_message_bg.desc", ConfigEntryType.TOGGLE, showBgButton, ConfigCategory.MESSAGES));

        addColorEntry(
            "chatrix.config.panel_base_color",
            "chatrix.config.panel_base_color.desc",
            ModConfig::getPANEL_BASE_COLOR_RGB,
            ModConfig::setPANEL_BASE_COLOR_RGB
        ).enabledWhen(ModConfig::isSHOW_MESSAGE_BACKGROUND);

        addToggleEntry(
            "chatrix.config.panel_border_from_platform",
            "chatrix.config.panel_border_from_platform.desc",
            ModConfig::isPANEL_BORDER_FROM_PLATFORM,
            ModConfig::setPANEL_BORDER_FROM_PLATFORM,
            ConfigCategory.MESSAGES
        ).enabledWhen(ModConfig::isSHOW_MESSAGE_BACKGROUND);

        addColorEntry(
            "chatrix.config.panel_border_color",
            "chatrix.config.panel_border_color.desc",
            ModConfig::getPANEL_BORDER_COLOR_RGB,
            ModConfig::setPANEL_BORDER_COLOR_RGB
        ).enabledWhen(() -> ModConfig.isSHOW_MESSAGE_BACKGROUND() && !ModConfig.isPANEL_BORDER_FROM_PLATFORM());

        addToggleEntry(
            "chatrix.config.show_role_badges",
            "chatrix.config.show_role_badges.desc",
            ModConfig::isSHOW_ROLE_BADGES,
            ModConfig::setSHOW_ROLE_BADGES,
            ConfigCategory.MESSAGES
        );

        ButtonWidget colorEmojiButton = createToggleButton(ModConfig::isENABLE_COLOR_EMOJIS, ModConfig::setENABLE_COLOR_EMOJIS);
        this.addDrawableChild(colorEmojiButton);
        configEntries.add(new ConfigEntry("chatrix.config.color_emojis", "chatrix.config.color_emojis.desc", ConfigEntryType.TOGGLE, colorEmojiButton, ConfigCategory.MESSAGES));

        ButtonWidget messageSoundButton = createToggleButton(ModConfig::isENABLE_MESSAGE_SOUND, ModConfig::setENABLE_MESSAGE_SOUND);
        this.addDrawableChild(messageSoundButton);
        configEntries.add(new ConfigEntry("chatrix.config.message_sound", "chatrix.config.message_sound.desc", ConfigEntryType.TOGGLE, messageSoundButton, ConfigCategory.MESSAGES));

        MessageSoundVolumeSliderWidget messageSoundVolumeSlider = new MessageSoundVolumeSliderWidget(0, 0, CONTROL_WIDTH, 20);
        this.addDrawableChild(messageSoundVolumeSlider);
        addEntry(new ConfigEntry("chatrix.config.message_sound_volume", "chatrix.config.message_sound_volume.desc", ConfigEntryType.SLIDER, messageSoundVolumeSlider, ConfigCategory.MESSAGES))
            .enabledWhen(ModConfig::isENABLE_MESSAGE_SOUND);

        ConfigIntTextFieldWidget messageHistoryMaxField = new ConfigIntTextFieldWidget(
            textRenderer,
            0,
            0,
            CONTROL_WIDTH,
            20,
            Text.translatable("chatrix.config.message_history_max"),
            "messageHistoryMaxSize",
            10,
            500
        );
        this.addDrawableChild(messageHistoryMaxField);
        configEntries.add(new ConfigEntry("chatrix.config.message_history_max", "chatrix.config.message_history_max.desc", ConfigEntryType.TEXT_FIELD, messageHistoryMaxField, ConfigCategory.MESSAGES));

        // Placement: HUD overlay or world-space 3D, according to the current display mode.
        worldPlacementButton = ButtonWidget.builder(
            getWorldPlacementButtonText(),
            btn -> toggleWorldPlacement()
        ).dimensions(0, 0, CONTROL_WIDTH, 20).build();
        this.addDrawableChild(worldPlacementButton);
        addEntry(new ConfigEntry(
            "chatrix.config.world_placement",
            "chatrix.config.world_placement.desc",
            ConfigEntryType.BUTTON,
            worldPlacementButton,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode);

        ButtonWidget hudAnchorButton = ButtonWidget.builder(
            getHudAnchorButtonText(),
            btn -> {
                HudAnchor nextAnchor = ModConfig.getHUD_ANCHOR().next();
                ModConfig.setHUD_ANCHOR(nextAnchor);
                btn.setMessage(getHudAnchorButtonText());
            }
        ).dimensions(0, 0, CONTROL_WIDTH, 20).build();
        this.addDrawableChild(hudAnchorButton);
        addEntry(new ConfigEntry(
            "chatrix.config.hud_anchor",
            "chatrix.config.hud_anchor.desc",
            ConfigEntryType.BUTTON,
            hudAnchorButton,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isHudWidgetMode);

        ConfigIntTextFieldWidget hudOffsetXField = new ConfigIntTextFieldWidget(
            textRenderer,
            0,
            0,
            CONTROL_WIDTH,
            20,
            Text.translatable("chatrix.config.hud_offset_x"),
            "hudOffsetX",
            0,
            400
        );
        this.addDrawableChild(hudOffsetXField);
        addEntry(new ConfigEntry(
            "chatrix.config.hud_offset_x",
            "chatrix.config.hud_offset_x.desc",
            ConfigEntryType.TEXT_FIELD,
            hudOffsetXField,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isHudWidgetMode);

        ConfigIntTextFieldWidget hudOffsetYField = new ConfigIntTextFieldWidget(
            textRenderer,
            0,
            0,
            CONTROL_WIDTH,
            20,
            Text.translatable("chatrix.config.hud_offset_y"),
            "hudOffsetY",
            0,
            400
        );
        this.addDrawableChild(hudOffsetYField);
        addEntry(new ConfigEntry(
            "chatrix.config.hud_offset_y",
            "chatrix.config.hud_offset_y.desc",
            ConfigEntryType.TEXT_FIELD,
            hudOffsetYField,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isHudWidgetMode);

        ConfigIntTextFieldWidget spawnMinDistanceField = new ConfigIntTextFieldWidget(
            textRenderer,
            0,
            0,
            CONTROL_WIDTH,
            20,
            Text.translatable("chatrix.config.spawn_min_distance"),
            "messageSpawnMinDistance",
            1,
            64
        );
        this.addDrawableChild(spawnMinDistanceField);
        addEntry(new ConfigEntry(
            "chatrix.config.spawn_min_distance",
            "chatrix.config.spawn_min_distance.desc",
            ConfigEntryType.TEXT_FIELD,
            spawnMinDistanceField,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode);

        ConfigIntTextFieldWidget spawnMaxDistanceField = new ConfigIntTextFieldWidget(
            textRenderer,
            0,
            0,
            CONTROL_WIDTH,
            20,
            Text.translatable("chatrix.config.spawn_max_distance"),
            "messageSpawnMaxDistance",
            1,
            64
        );
        this.addDrawableChild(spawnMaxDistanceField);
        addEntry(new ConfigEntry(
            "chatrix.config.spawn_max_distance",
            "chatrix.config.spawn_max_distance.desc",
            ConfigEntryType.TEXT_FIELD,
            spawnMaxDistanceField,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode);

        ButtonWidget freezingButton = createToggleButton(ModConfig::isENABLE_FREEZING_ON_VIEW, ModConfig::setENABLE_FREEZING_ON_VIEW);
        this.addDrawableChild(freezingButton);
        addEntry(new ConfigEntry(
            "chatrix.config.freezing_on_view",
            "chatrix.config.freezing_on_view.desc",
            ConfigEntryType.TOGGLE,
            freezingButton,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode);

        ConfigIntTextFieldWidget maxFreezeDistanceField = new ConfigIntTextFieldWidget(
            textRenderer,
            0,
            0,
            CONTROL_WIDTH,
            20,
            Text.translatable("chatrix.config.max_freeze_distance"),
            "maxFreezeDistance",
            1,
            128
        );
        this.addDrawableChild(maxFreezeDistanceField);
        addEntry(new ConfigEntry(
            "chatrix.config.max_freeze_distance",
            "chatrix.config.max_freeze_distance.desc",
            ConfigEntryType.TEXT_FIELD,
            maxFreezeDistanceField,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode).enabledWhen(ModConfig::isENABLE_FREEZING_ON_VIEW);

        ButtonWidget followPlayerButton = createToggleButton(ModConfig::isFOLLOW_PLAYER, ModConfig::setFOLLOW_PLAYER);
        this.addDrawableChild(followPlayerButton);
        addEntry(new ConfigEntry(
            "chatrix.config.follow_player",
            "chatrix.config.follow_player.desc",
            ConfigEntryType.TOGGLE,
            followPlayerButton,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode);

        ButtonWidget clickToRemoveButton = createToggleButton(ModConfig::isENABLE_CLICK_TO_REMOVE, ModConfig::setENABLE_CLICK_TO_REMOVE);
        this.addDrawableChild(clickToRemoveButton);
        addEntry(new ConfigEntry(
            "chatrix.config.click_to_remove",
            "chatrix.config.click_to_remove.desc",
            ConfigEntryType.TOGGLE,
            clickToRemoveButton,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode);

        ButtonWidget unpinModeButton = ButtonWidget.builder(
            getUnpinModeButtonText(),
            btn -> {
                UnpinMode nextMode = ModConfig.getUNPIN_MODE().next();
                ModConfig.setUNPIN_MODE(nextMode);
                btn.setMessage(getUnpinModeButtonText());
            }
        ).dimensions(0, 0, CONTROL_WIDTH, 20).build();
        this.addDrawableChild(unpinModeButton);
        addEntry(new ConfigEntry(
            "chatrix.config.unpin_mode",
            "chatrix.config.unpin_mode.desc",
            ConfigEntryType.BUTTON,
            unpinModeButton,
            ConfigCategory.LAYOUT
        )).visibleWhen(ModConfigScreen::isWorldMode);
    }

    private ButtonWidget createToggleButton(
        java.util.function.BooleanSupplier getter,
        java.util.function.Consumer<Boolean> setter
    ) {
        return ButtonWidget.builder(
            ConfigUiHelper.onOffText(getter.getAsBoolean()),
            btn -> {
                setter.accept(!getter.getAsBoolean());
                btn.setMessage(ConfigUiHelper.onOffText(getter.getAsBoolean()));
            }
        ).dimensions(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT).build();
    }

    private ConfigEntry addEntry(ConfigEntry entry) {
        configEntries.add(entry);
        return entry;
    }

    private ConfigIntSliderWidget createIntSlider(
        String configKey, int min, int max, int step, int fallback,
        java.util.function.IntFunction<Text> label
    ) {
        ConfigIntSliderWidget slider = new ConfigIntSliderWidget(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, configKey, min, max, step, fallback, label);
        this.addDrawableChild(slider);
        return slider;
    }

    private static boolean isHudWidgetMode() {
        MessageSpawnMode mode = ModConfig.getMESSAGE_SPAWN_MODE();
        return mode != null && mode.isHud();
    }

    private static boolean isWorldMode() {
        return !isHudWidgetMode();
    }

    private static void rememberWorldModeIfNeeded() {
        MessageSpawnMode current = ModConfig.getMESSAGE_SPAWN_MODE();
        if (current != null && !current.isHud()) {
            lastWorldSpawnMode = current;
        }
    }

    private void toggleDisplayKind() {
        MessageSpawnMode current = ModConfig.getMESSAGE_SPAWN_MODE();
        if (current.isHud()) {
            MessageSpawnMode restored = lastWorldSpawnMode != null && !lastWorldSpawnMode.isHud()
                ? lastWorldSpawnMode
                : MessageSpawnMode.FRONT_OF_PLAYER;
            ModConfig.setMESSAGE_SPAWN_MODE(restored);
        } else {
            lastWorldSpawnMode = current;
            ModConfig.setMESSAGE_SPAWN_MODE(MessageSpawnMode.HUD_WIDGET);
        }
        refreshModeButtons();
        updateCategoryVisibility();
    }

    private void toggleWorldPlacement() {
        MessageSpawnMode current = ModConfig.getMESSAGE_SPAWN_MODE();
        if (current.isHud()) {
            return;
        }
        lastWorldSpawnMode = current.nextWorldPlacement();
        ModConfig.setMESSAGE_SPAWN_MODE(lastWorldSpawnMode);
        refreshModeButtons();
    }

    private void refreshModeButtons() {
        if (displayModeButton != null) {
            displayModeButton.setMessage(getDisplayModeButtonText());
        }
        if (worldPlacementButton != null) {
            worldPlacementButton.setMessage(getWorldPlacementButtonText());
        }
    }

    private ConfigEntry addToggleEntry(
        String labelKey,
        String descriptionKey,
        java.util.function.BooleanSupplier getter,
        java.util.function.Consumer<Boolean> setter,
        ConfigCategory category
    ) {
        ButtonWidget button = createToggleButton(getter, setter);
        this.addDrawableChild(button);
        return addEntry(new ConfigEntry(labelKey, descriptionKey, ConfigEntryType.TOGGLE, button, category));
    }

    /** Row with a feature enable toggle and its detailed settings button. */
    private ConfigEntry addToggleWithSettingsEntry(
        String labelKey,
        String descriptionKey,
        java.util.function.BooleanSupplier getter,
        java.util.function.Consumer<Boolean> setter,
        Runnable openSettings
    ) {
        ButtonWidget toggle = ButtonWidget.builder(
            ConfigUiHelper.onOffText(getter.getAsBoolean()),
            btn -> {
                setter.accept(!getter.getAsBoolean());
                btn.setMessage(ConfigUiHelper.onOffText(getter.getAsBoolean()));
            }
        ).dimensions(0, 0, CONTROL_WIDTH - CARD_SETTINGS_WIDTH - CARD_GAP, CONTROL_HEIGHT).build();
        this.addDrawableChild(toggle);

        ButtonWidget settings = ButtonWidget.builder(
            Text.empty(),
            btn -> openSettings.run()
        ).dimensions(0, 0, CARD_SETTINGS_WIDTH, CONTROL_HEIGHT).build();
        this.addDrawableChild(settings);

        return addEntry(new ConfigEntry(
            labelKey,
            descriptionKey,
            ConfigEntryType.TOGGLE_WITH_SETTINGS,
            new ToggleWithSettingsRow(toggle, settings),
            ConfigCategory.GENERAL
        ));
    }

    /** HEX color field with a button opening the HSV color picker. */
    private ConfigEntry addColorEntry(
        String labelKey,
        String descriptionKey,
        java.util.function.IntSupplier getter,
        java.util.function.IntConsumer setter
    ) {
        TextFieldWidget field = new TextFieldWidget(
            this.textRenderer,
            0,
            0,
            CONTROL_WIDTH - COLOR_PICKER_SIZE - CARD_GAP,
            CONTROL_HEIGHT,
            Text.translatable(labelKey)
        );
        field.setMaxLength(7);
        field.setText(String.format("%06X", getter.getAsInt() & 0xFFFFFF));
        field.setChangedListener(value -> {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            if (hex.matches("(?i)[0-9a-f]{6}")) {
                setter.accept(Integer.parseInt(hex, 16));
            }
        });
        this.addDrawableChild(field);

        ButtonWidget picker = ButtonWidget.builder(
            Text.empty(),
            btn -> ScreenNavigationCompat.open(
                this.client,
                new ColorPickerScreen(
                    this,
                    Text.translatable("chatrix.config.color_picker.title"),
                    getter.getAsInt(),
                    chosen -> {
                        setter.accept(chosen);
                        field.setText(String.format("%06X", chosen & 0xFFFFFF));
                    }
                )
            )
        ).dimensions(0, 0, COLOR_PICKER_SIZE, CONTROL_HEIGHT).build();
        this.addDrawableChild(picker);

        return addEntry(new ConfigEntry(
            labelKey,
            descriptionKey,
            ConfigEntryType.COLOR_FIELD,
            new ColorFieldRow(field, picker),
            ConfigCategory.MESSAGES,
            getter
        ));
    }

    private void addPlatformCard(com.hristrix.chatrix.integration.chat.ChatPlatform platform) {
        String platformKey = platform.getIconKey();
        boolean hasChannel = !platformChannelValue(platform).trim().isEmpty();
        if (!hasChannel && ModConfig.isPLATFORM_ENABLED(platformKey)) {
            ModConfig.setPLATFORM_ENABLED(platformKey, false);
        }

        ButtonWidget toggle = ButtonWidget.builder(
            ConfigUiHelper.onOffText(ModConfig.isPLATFORM_ENABLED(platformKey)),
            btn -> {
                boolean enabled = !ModConfig.isPLATFORM_ENABLED(platformKey);
                ModConfig.setPLATFORM_ENABLED(platformKey, enabled);
                btn.setMessage(ConfigUiHelper.onOffText(enabled));
                applyConnectionSettingsFromConfig();
            }
        ).dimensions(0, 0, TOGGLE_BUTTON_WIDTH, CONTROL_HEIGHT).build();
        // A platform requires a nonempty username, configured on its settings page.
        toggle.active = hasChannel;
        this.addDrawableChild(toggle);

        ButtonWidget settings = ButtonWidget.builder(
            Text.empty(),
            btn -> ScreenNavigationCompat.open(this.client, new PlatformConfigScreen(this, platform))
        ).dimensions(0, 0, CARD_SETTINGS_WIDTH, CONTROL_HEIGHT).build();
        this.addDrawableChild(settings);

        PlatformSettingsCard card = new PlatformSettingsCard(platform, toggle, settings);
        configEntries.add(new ConfigEntry(
            "chatrix.config.platform_card",
            "chatrix.config.platform_card.desc",
            ConfigEntryType.PLATFORM_CARD,
            card,
            ConfigCategory.GENERAL
        ));
    }

    private static String platformChannelValue(com.hristrix.chatrix.integration.chat.ChatPlatform platform) {
        Object value = ConfigManager.getInstance()
            .getConfigValue(PlatformConfigScreen.channelConfigKey(platform));
        return value instanceof String s ? s : "";
    }
    
    private void updateCategoryVisibility() {
        clampScrollOffset();
        for (ConfigEntry entry : configEntries) {
            setWidgetVisible(entry.widget, isEntryShown(entry));
        }
        updateEntryPositions();
    }

    /** Current-category row whose dependency conditions are satisfied. */
    private boolean isEntryShown(ConfigEntry entry) {
        return entry.category == currentCategory && entry.isVisible();
    }
    
    /**
     * Lays out the current category: platform cards appear two per row,
     * other settings use the full width. Returns the total content height.
     */
    private int layoutEntries() {
        int rowLeft = CONTENT_PADDING + 4;
        int rowRight = this.width - CONTENT_PADDING - 4;
        int fullWidth = rowRight - rowLeft;
        int halfWidth = (fullWidth - CARD_GAP) / 2;
        int startY = getMainPanelTop() + CONTENT_PADDING - scrollOffset;
        int y = startY;
        int column = 0;

        for (ConfigEntry entry : configEntries) {
            if (!isEntryShown(entry)) {
                continue;
            }

            if (entry.type == ConfigEntryType.PLATFORM_CARD) {
                entry.rowX = rowLeft + column * (halfWidth + CARD_GAP);
                entry.rowY = y;
                entry.rowWidth = halfWidth;
                column++;
                if (column == 2) {
                    column = 0;
                    y += CARD_HEIGHT + ENTRY_SPACING;
                }
                continue;
            }

            if (column != 0) {
                column = 0;
                y += CARD_HEIGHT + ENTRY_SPACING;
            }
            entry.rowX = rowLeft;
            entry.rowY = y;
            entry.rowWidth = fullWidth;
            y += entry.rowHeight() + ENTRY_SPACING;
        }
        if (column != 0) {
            y += CARD_HEIGHT + ENTRY_SPACING;
        }
        if (y == startY) {
            return CONTENT_PADDING * 2;
        }
        return y - startY - ENTRY_SPACING + CONTENT_PADDING * 2;
    }

    private void updateEntryPositions() {
        clampScrollOffset();
        layoutEntries();
        int contentTop = getMainPanelTop();
        int contentBottom = getScrollBottom();
        int rightX = this.width - SIDE_MARGIN - CONTROL_WIDTH;

        for (ConfigEntry entry : configEntries) {
            if (!isEntryShown(entry)) {
                setWidgetVisible(entry.widget, false);
                continue;
            }

            if (entry.widget instanceof PlatformSettingsCard card) {
                positionPlatformCard(card, entry);
            } else if (entry.widget instanceof ToggleWithSettingsRow row) {
                positionToggleWithSettingsRow(row, rightX, entry.rowY);
            } else if (entry.widget instanceof ColorFieldRow row) {
                positionColorFieldRow(row, rightX, entry.rowY);
            } else if (entry.type == ConfigEntryType.FEATURE && entry.widget instanceof ButtonWidget button) {
                button.setPosition(rightX, entry.rowY + (FEATURE_HEIGHT - FEATURE_BUTTON_HEIGHT) / 2);
            } else {
                setWidgetPosition(entry.widget, rightX, entry.rowY);
            }
            setWidgetVisible(entry.widget, isElementVisible(entry.rowY, entry.rowHeight(), contentTop, contentBottom));
            setWidgetEnabled(entry.widget, entry.isEnabled());
        }
    }

    private void positionToggleWithSettingsRow(ToggleWithSettingsRow row, int rightX, int rowY) {
        int controlsY = rowY + (ENTRY_HEIGHT - CONTROL_HEIGHT) / 2;
        row.toggle.setPosition(rightX, controlsY);
        row.settings.setPosition(rightX + CONTROL_WIDTH - CARD_SETTINGS_WIDTH, controlsY);
    }

    private void positionColorFieldRow(ColorFieldRow row, int rightX, int rowY) {
        int controlsY = rowY + (ENTRY_HEIGHT - CONTROL_HEIGHT) / 2;
        row.field.setPosition(rightX, controlsY);
        row.picker.setPosition(rightX + CONTROL_WIDTH - COLOR_PICKER_SIZE, controlsY);
    }

    private void positionPlatformCard(PlatformSettingsCard card, ConfigEntry entry) {
        int controlsY = entry.rowY + (CARD_HEIGHT - CONTROL_HEIGHT) / 2;
        int settingsX = entry.rowX + entry.rowWidth - CARD_INNER_PADDING - CARD_SETTINGS_WIDTH;
        int toggleX = settingsX - CARD_GAP - TOGGLE_BUTTON_WIDTH;
        card.toggle.setPosition(toggleX, controlsY);
        card.toggle.setDimensions(TOGGLE_BUTTON_WIDTH, CONTROL_HEIGHT);
        card.settings.setPosition(settingsX, controlsY);
        card.settings.setDimensions(CARD_SETTINGS_WIDTH, CONTROL_HEIGHT);
    }
    

    
    private void createBottomButtons() {
        historyButton = ButtonWidget.builder(Text.translatable("chatrix.config.message_history"), btn -> {
            var messageSpawner = ChatrixClient.getStaticMessageSpawner();
            if (messageSpawner != null) {
                var lifecycle = messageSpawner.getLifecycleManager();
                if (this.client != null) {
                    lifecycle.warmHistoryLayouts(this.textRenderer);
                }
                ScreenNavigationCompat.open(this.client, new MessageHistoryScreen(this, lifecycle));
            }
        }).dimensions(0, 0, 1, FOOTER_BUTTON_HEIGHT).build();

        chatToggleButton = ButtonWidget.builder(
            ChatConnectToggleHelper.buttonLabel(),
            btn -> {
                ChatConnectToggleHelper.toggle();
                btn.setMessage(ChatConnectToggleHelper.buttonLabel());
            }
        ).dimensions(0, 0, 1, FOOTER_BUTTON_HEIGHT).build();

        doneButton = ButtonWidget.builder(Text.translatable("gui.done"), btn -> this.close())
            .dimensions(0, 0, 1, FOOTER_BUTTON_HEIGHT).build();

        this.addDrawableChild(historyButton);
        this.addDrawableChild(chatToggleButton);
        this.addDrawableChild(doneButton);
        layoutFooterButtons();
    }

    private void layoutFooterButtons() {
        if (historyButton == null || chatToggleButton == null || doneButton == null || this.textRenderer == null) {
            return;
        }

        Text historyLabel = Text.translatable("chatrix.config.message_history");
        Text chatLabel = ChatConnectToggleHelper.buttonLabel();
        Text doneLabel = Text.translatable("gui.done");

        int historyW = BUTTON_ICON_SIZE + BUTTON_ICON_GAP
            + this.textRenderer.getWidth(historyLabel) + FOOTER_BUTTON_PADDING * 2;
        int chatW = this.textRenderer.getWidth(chatLabel) + FOOTER_BUTTON_PADDING * 2;
        int doneW = this.textRenderer.getWidth(doneLabel) + FOOTER_BUTTON_PADDING * 2;
        int buttonY = getFooterZoneTop();

        int totalWidth = historyW + chatW + doneW + FOOTER_BUTTON_GAP * 2;
        int x = Math.max(8, (this.width - totalWidth) / 2);

        historyButton.setPosition(x, buttonY);
        historyButton.setDimensions(historyW, FOOTER_BUTTON_HEIGHT);
        x += historyW + FOOTER_BUTTON_GAP;

        chatToggleButton.setPosition(x, buttonY);
        chatToggleButton.setDimensions(chatW, FOOTER_BUTTON_HEIGHT);
        chatToggleButton.setMessage(chatLabel);
        x += chatW + FOOTER_BUTTON_GAP;

        doneButton.setPosition(x, buttonY);
        doneButton.setDimensions(doneW, FOOTER_BUTTON_HEIGHT);
    }


    private Text getHudAnchorButtonText() {
        return switch (ModConfig.getHUD_ANCHOR()) {
            case TOP_LEFT -> Text.translatable("chatrix.config.hud_anchor.top_left");
            case BOTTOM_RIGHT -> Text.translatable("chatrix.config.hud_anchor.bottom_right");
            case BOTTOM_LEFT -> Text.translatable("chatrix.config.hud_anchor.bottom_left");
            default -> Text.translatable("chatrix.config.hud_anchor.top_right");
        };
    }

    private Text getDisplayModeButtonText() {
        return isHudWidgetMode()
            ? Text.translatable("chatrix.config.display_mode.hud")
            : Text.translatable("chatrix.config.display_mode.world");
    }

    private Text getWorldPlacementButtonText() {
        return ModConfig.getMESSAGE_SPAWN_MODE() == MessageSpawnMode.AROUND_PLAYER
            ? Text.translatable("chatrix.config.around_player")
            : Text.translatable("chatrix.config.fop_only");
    }

    private Text getUnpinModeButtonText() {
        return ModConfig.getUNPIN_MODE() == UnpinMode.WHOLE_MESSAGE
            ? Text.translatable("chatrix.config.unpin_mode.whole_message")
            : Text.translatable("chatrix.config.unpin_mode.pin_icon");
    }

    private boolean hasWorldClient() {
        var client = this.client;
        return client != null && client.world != null;
    }


    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateEntryPositions();
        hoveredDescriptionKey = null;

        java.util.Set<ButtonWidget> hiddenButtons = ScreenUiHelper.hideButtons(this);
        List<Object> temporarilyHidden = new ArrayList<>();
        for (ConfigEntry entry : configEntries) {
            if (!isEntryShown(entry)) {
                continue;
            }
            if (entry.widget instanceof TextFieldWidget w && w.visible) {
                w.visible = false;
                temporarilyHidden.add(w);
            } else if (entry.widget instanceof ColorFieldRow row && row.field.visible) {
                row.field.visible = false;
                temporarilyHidden.add(row);
            } else if (entry.widget instanceof ChanceForSpawnSliderWidget w && w.visible) {
                w.visible = false;
                temporarilyHidden.add(w);
            } else if (entry.widget instanceof ConfigIntSliderWidget w && w.visible) {
                w.visible = false;
                temporarilyHidden.add(w);
            } else if (entry.widget instanceof MessageSoundVolumeSliderWidget w && w.visible) {
                w.visible = false;
                temporarilyHidden.add(w);
            }
        }

        super.render(context, mouseX, mouseY, delta);

        ScreenUiHelper.restoreButtons(hiddenButtons);
        for (Object widget : temporarilyHidden) {
            setWidgetVisible(widget, true);
        }
        
        layoutFooterButtons();
        ModUiTheme.drawTitle(context, this.textRenderer, this.title,
            Text.translatable("chatrix.branding.author"), this.width, TITLE_Y);

        int panelTop = getMainPanelTop();
        int panelBottom = getMainPanelBottom();
        int scrollTop = panelTop;
        int scrollBottom = getScrollBottom();

        ModUiTheme.drawBorderedPanel(
            context,
            CONTENT_PADDING,
            panelTop,
            this.width - CONTENT_PADDING * 2,
            panelBottom - panelTop
        );

        context.enableScissor(CONTENT_PADDING, scrollTop, this.width - CONTENT_PADDING, scrollBottom);
        renderLabels(context, mouseX, mouseY, scrollTop, scrollBottom);
        renderConfigWidgets(context, mouseX, mouseY, delta, scrollTop, scrollBottom);
        context.disableScissor();
        renderScrollbar(context, scrollTop, scrollBottom);

        drawFixedPanelFooter(context);

        Text description = hoveredDescriptionKey == null
            ? Text.translatable("chatrix.config.hint_default")
            : Text.translatable(hoveredDescriptionKey);

        int descTop = getDescriptionTop();
        context.drawTextWithShadow(
            this.textRenderer,
            description,
            CONTENT_PADDING + 10,
            descTop + (DESCRIPTION_HEIGHT - this.textRenderer.fontHeight) / 2,
            ModUiTheme.TEXT_SECONDARY
        );

        for (int i = 0; i < categoryButtons.size(); i++) {
            ButtonWidget button = categoryButtons.get(i);
            ConfigCategory category = ConfigCategory.values()[i];
            drawIconButton(
                context,
                button,
                category.getIcon(),
                category.getText(),
                isButtonHovered(button, mouseX, mouseY),
                category == currentCategory
            );
        }
        if (historyButton != null) {
            drawIconButton(
                context,
                historyButton,
                ICON_HISTORY,
                Text.translatable("chatrix.config.message_history"),
                isButtonHovered(historyButton, mouseX, mouseY),
                false
            );
            if (chatToggleButton != null) {
                boolean chatConnected = ChatConnectionManager.getInstance(ConfigManager.getInstance()).isParserEnabled();
                ModUiTheme.drawConnectionToggleButton(
                    context,
                    this.textRenderer,
                    chatToggleButton,
                    chatConnected,
                    isButtonHovered(chatToggleButton, mouseX, mouseY)
                );
            }
            if (doneButton != null) {
                ScreenUiHelper.renderButtons(
                    context,
                    mouseX,
                    mouseY,
                    java.util.List.of(doneButton),
                    null
                );
            }
        }
    }

    private static boolean isButtonHovered(ButtonWidget button, int mouseX, int mouseY) {
        return button.active && ModUiTheme.isHovered(
            mouseX,
            mouseY,
            button.getX(),
            button.getY(),
            button.getWidth(),
            button.getHeight()
        );
    }

    /** ModUiTheme button with a pixel icon to the left of its label. */
    private void drawIconButton(
        DrawContext context,
        ButtonWidget button,
        Identifier icon,
        Text label,
        boolean hovered,
        boolean selected
    ) {
        ModUiTheme.drawButton(
            context,
            this.textRenderer,
            button.getX(),
            button.getY(),
            button.getWidth(),
            button.getHeight(),
            Text.empty(),
            hovered,
            button.active,
            selected,
            true
        );
        int textWidth = this.textRenderer.getWidth(label);
        int totalWidth = BUTTON_ICON_SIZE + BUTTON_ICON_GAP + textWidth;
        int startX = button.getX() + Math.max(4, (button.getWidth() - totalWidth) / 2);
        int iconY = button.getY() + (button.getHeight() - BUTTON_ICON_SIZE) / 2;
        com.hristrix.chatrix.ui.gui.MessageEmoteGuiRenderer.drawGuiIcon(
            context, icon, startX, iconY, BUTTON_ICON_SIZE
        );
        int textColor = button.active
            ? (hovered || selected ? ModUiTheme.TEXT_PRIMARY : ModUiTheme.TEXT_SECONDARY)
            : ModUiTheme.TEXT_HINT;
        context.drawTextWithShadow(
            this.textRenderer,
            label,
            startX + BUTTON_ICON_SIZE + BUTTON_ICON_GAP,
            ModUiTheme.centeredTextY(button.getY(), button.getHeight(), this.textRenderer.fontHeight),
            textColor
        );
    }

    /** Fixed panel footer with an opaque background to hide the scrolling settings list beneath it. */
    private void drawFixedPanelFooter(DrawContext context) {
        int innerLeft = CONTENT_PADDING + 1;
        int innerRight = this.width - CONTENT_PADDING - 1;
        int fixedTop = getDescriptionTop();
        int fixedBottom = getMainPanelBottom() - 1;

        context.fill(innerLeft, fixedTop, innerRight, fixedBottom, ModUiTheme.PANEL_BG);
        context.fill(innerLeft, fixedTop, innerRight, fixedTop + 1, ModUiTheme.PANEL_BORDER);
    }

    private void renderConfigWidgets(DrawContext context, int mouseX, int mouseY, float delta, int contentTop, int contentBottom) {
        for (ConfigEntry entry : configEntries) {
            if (!isEntryShown(entry)) {
                continue;
            }

            if (entry.widget instanceof ButtonWidget widget) {
                if (widget.visible && isElementVisible(widget.getY(), contentTop, contentBottom)) {
                    boolean hovered = ModUiTheme.isHovered(
                        mouseX,
                        mouseY,
                        widget.getX(),
                        widget.getY(),
                        widget.getWidth(),
                        widget.getHeight()
                    );
                    if (entry.type == ConfigEntryType.FEATURE && widget.active) {
                        ModUiTheme.drawCompactButton(
                            context,
                            this.textRenderer,
                            widget.getX(),
                            widget.getY(),
                            widget.getWidth(),
                            widget.getHeight(),
                            widget.getMessage(),
                            hovered,
                            ModUiTheme.ButtonVariant.ACCENT
                        );
                    } else if (entry.type == ConfigEntryType.DANGER_BUTTON) {
                        ModUiTheme.drawCompactButton(
                            context,
                            this.textRenderer,
                            widget.getX(),
                            widget.getY(),
                            widget.getWidth(),
                            widget.getHeight(),
                            widget.getMessage(),
                            hovered,
                            ModUiTheme.ButtonVariant.DANGER
                        );
                    } else {
                        ModUiTheme.drawButton(
                            context,
                            this.textRenderer,
                            widget.getX(),
                            widget.getY(),
                            widget.getWidth(),
                            widget.getHeight(),
                            widget.getMessage(),
                            hovered,
                            widget.active,
                            false,
                            true
                        );
                    }
                }
            } else if (entry.widget instanceof PlatformSettingsCard card) {
                if (!card.toggle.visible || !isElementVisible(entry.rowY, CARD_HEIGHT, contentTop, contentBottom)) {
                    continue;
                }
                drawCardButton(context, card.toggle, mouseX, mouseY, card.toggle.getMessage(), null);
                drawCardButton(context, card.settings, mouseX, mouseY, Text.empty(), ICON_SETTINGS);
            } else if (entry.widget instanceof ToggleWithSettingsRow row) {
                if (!row.toggle.visible || !isElementVisible(entry.rowY, ENTRY_HEIGHT, contentTop, contentBottom)) {
                    continue;
                }
                drawCardButton(context, row.toggle, mouseX, mouseY, row.toggle.getMessage(), null);
                drawCardButton(context, row.settings, mouseX, mouseY, Text.empty(), ICON_SETTINGS);
            } else if (entry.widget instanceof ColorFieldRow row) {
                if (!row.field.visible || !isElementVisible(entry.rowY, ENTRY_HEIGHT, contentTop, contentBottom)) {
                    continue;
                }
                ModUiTheme.drawInputFrame(
                    context,
                    row.field.getX(),
                    row.field.getY(),
                    row.field.getWidth(),
                    row.field.getHeight(),
                    row.field.isFocused()
                );
                row.field.render(context, mouseX, mouseY, delta);
                drawColorPickerButton(context, row.picker, entry.colorPreview == null ? 0xFFFFFF : entry.colorPreview.getAsInt(), mouseX, mouseY);
            } else if (entry.widget instanceof TextFieldWidget widget) {
                if (widget.visible && isElementVisible(widget.getY(), contentTop, contentBottom)) {
                    ModUiTheme.drawInputFrame(
                        context,
                        widget.getX(),
                        widget.getY(),
                        widget.getWidth(),
                        widget.getHeight(),
                        widget.isFocused()
                    );
                    widget.render(context, mouseX, mouseY, delta);
                }
            } else if (entry.widget instanceof ChanceForSpawnSliderWidget widget) {
                if (widget.visible && isElementVisible(widget.getY(), contentTop, contentBottom)) {
                    widget.render(context, mouseX, mouseY, delta);
                    ModUiTheme.drawSlider(context, this.textRenderer,
                        widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                        widget.getMessage(), widget.getNormalizedValue(),
                        ModUiTheme.isHovered(mouseX, mouseY, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()),
                        widget.isFocused(), widget.active);
                }
            } else if (entry.widget instanceof ConfigIntSliderWidget widget) {
                if (widget.visible && isElementVisible(widget.getY(), contentTop, contentBottom)) {
                    widget.render(context, mouseX, mouseY, delta);
                    ModUiTheme.drawSlider(context, this.textRenderer,
                        widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                        widget.getMessage(), widget.getNormalizedValue(),
                        ModUiTheme.isHovered(mouseX, mouseY, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()),
                        widget.isFocused(), widget.active);
                }
            } else if (entry.widget instanceof MessageSoundVolumeSliderWidget widget) {
                if (widget.visible && isElementVisible(widget.getY(), contentTop, contentBottom)) {
                    widget.render(context, mouseX, mouseY, delta);
                    ModUiTheme.drawSlider(context, this.textRenderer,
                        widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                        widget.getMessage(), widget.getNormalizedValue(),
                        ModUiTheme.isHovered(mouseX, mouseY, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()),
                        widget.isFocused(), widget.active);
                }
            }
        }
    }
    
    private void renderLabels(DrawContext context, int mouseX, int mouseY, int contentTop, int contentBottom) {
        int fontHeight = this.textRenderer.fontHeight;

        for (ConfigEntry entry : configEntries) {
            if (!isEntryShown(entry)) continue;

            int rowHeight = entry.rowHeight();
            if (!isElementVisible(entry.rowY, rowHeight, contentTop, contentBottom)) {
                continue;
            }

            boolean rowHovered = mouseX >= entry.rowX && mouseX <= entry.rowX + entry.rowWidth
                && mouseY >= entry.rowY && mouseY <= entry.rowY + rowHeight;

            if (entry.widget instanceof PlatformSettingsCard card) {
                drawPlatformCard(context, card, entry, rowHovered);
            } else if (entry.type == ConfigEntryType.FEATURE) {
                drawFeatureBanner(context, entry, rowHovered);
            } else {
                ModUiTheme.drawListRow(
                    context,
                    entry.rowX,
                    entry.rowY,
                    entry.rowX + entry.rowWidth,
                    entry.rowY + rowHeight,
                    rowHovered
                );
                context.drawTextWithShadow(
                    this.textRenderer,
                    Text.translatable(entry.labelKey),
                    entry.rowX + 6,
                    entry.rowY + (rowHeight - fontHeight) / 2,
                    entry.isEnabled() ? ModUiTheme.TEXT_PRIMARY : ModUiTheme.TEXT_HINT
                );
            }

            if (rowHovered) {
                hoveredDescriptionKey = entry.descriptionKey;
            }
        }
    }

    /** Large accent card for a tab's primary feature: title, description, and button on the right. */
    private void drawFeatureBanner(DrawContext context, ConfigEntry entry, boolean hovered) {
        int left = entry.rowX;
        int top = entry.rowY;
        int right = entry.rowX + entry.rowWidth;
        int bottom = entry.rowY + FEATURE_HEIGHT;
        boolean enabled = entry.isEnabled();
        ModUiTheme.drawSurface(context, left, top, right - left, bottom - top,
            hovered && enabled ? ModUiTheme.ACCENT_BG_HOVER : ModUiTheme.ACCENT_BG,
            enabled ? ModUiTheme.ACCENT : ModUiTheme.PANEL_BORDER, 4, false);

        int textX = left + 10;
        int textWidth = Math.max(40, right - textX - CONTROL_WIDTH - 24);
        context.drawTextWithShadow(this.textRenderer, Text.translatable(entry.labelKey), textX, top + 8, ModUiTheme.TEXT_PRIMARY);
        List<OrderedText> lines = this.textRenderer.wrapLines(Text.translatable(entry.descriptionKey), textWidth);
        int lineY = top + 21;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            context.drawTextWithShadow(this.textRenderer, lines.get(i), textX, lineY, enabled ? ModUiTheme.TEXT_SECONDARY : ModUiTheme.TEXT_HINT);
            lineY += 10;
        }
    }

    /** Platform card using the same banner as the source-page header. */
    private void drawPlatformCard(DrawContext context, PlatformSettingsCard card, ConfigEntry entry, boolean hovered) {
        com.hristrix.chatrix.ui.gui.PlatformBanner.draw(
            context,
            this.textRenderer,
            card.platform,
            entry.rowX,
            entry.rowY,
            entry.rowWidth,
            CARD_HEIGHT,
            hovered
        );
    }

    /** Button within a platform card, showing a label or centered icon. */
    private void drawCardButton(
        DrawContext context,
        ButtonWidget button,
        int mouseX,
        int mouseY,
        Text label,
        @Nullable Identifier icon
    ) {
        boolean hovered = isButtonHovered(button, mouseX, mouseY);
        ModUiTheme.drawButton(
            context,
            this.textRenderer,
            button.getX(),
            button.getY(),
            button.getWidth(),
            button.getHeight(),
            label,
            hovered,
            button.active,
            false,
            true
        );
        if (icon != null) {
            com.hristrix.chatrix.ui.gui.MessageEmoteGuiRenderer.drawGuiIcon(
                context,
                icon,
                button.getX() + (button.getWidth() - BUTTON_ICON_SIZE) / 2,
                button.getY() + (button.getHeight() - BUTTON_ICON_SIZE) / 2,
                BUTTON_ICON_SIZE
            );
        }
    }

    /** Color picker button filled with the selected color. */
    private void drawColorPickerButton(DrawContext context, ButtonWidget button, int rgb, int mouseX, int mouseY) {
        boolean hovered = isButtonHovered(button, mouseX, mouseY);
        ModUiTheme.drawButton(
            context,
            this.textRenderer,
            button.getX(),
            button.getY(),
            button.getWidth(),
            button.getHeight(),
            Text.empty(),
            hovered,
            button.active,
            false,
            true
        );
        int inset = 3;
        int left = button.getX() + inset;
        int top = button.getY() + inset;
        int right = button.getX() + button.getWidth() - inset;
        int bottom = button.getY() + button.getHeight() - inset;
        int fill = button.active ? (0xFF000000 | (rgb & 0xFFFFFF)) : 0xFF555555;
        context.fill(left, top, right, bottom, fill);
        context.fill(left, top, right, top + 1, ModUiTheme.PANEL_BORDER);
        context.fill(left, bottom - 1, right, bottom, ModUiTheme.PANEL_BORDER);
        context.fill(left, top, left + 1, bottom, ModUiTheme.PANEL_BORDER);
        context.fill(right - 1, top, right, bottom, ModUiTheme.PANEL_BORDER);
    }

    private void onResetDefaultsClicked() {
        if (!resetArmed) {
            resetArmed = true;
            if (resetButton != null) {
                resetButton.setMessage(Text.translatable("chatrix.config.reset_confirm"));
            }
            return;
        }
        ConfigManager.getInstance().resetToDefaults();
        applyConnectionSettingsFromConfig();
        ScreenNavigationCompat.open(this.client, new ModConfigScreen(this.parent));
    }

    private void disarmResetButton() {
        resetArmed = false;
        if (resetButton != null) {
            resetButton.setMessage(Text.translatable("chatrix.config.reset_defaults.button"));
        }
    }
    
    private boolean isElementVisible(int elementY, int contentTop, int contentBottom) {
        return isElementVisible(elementY, ENTRY_HEIGHT, contentTop, contentBottom);
    }

    private boolean isElementVisible(int elementY, int elementHeight, int contentTop, int contentBottom) {
        return elementY + elementHeight > contentTop && elementY < contentBottom;
    }

    private int getTotalContentHeight() {
        return layoutEntries();
    }
    
    private void renderScrollbar(DrawContext context, int contentTop, int contentBottom) {
        int totalContentHeight = getTotalContentHeight();
        int visibleContentHeight = contentBottom - contentTop;
        if (totalContentHeight <= visibleContentHeight) {
            return;
        }
        GuiScrollbar.draw(context, this.width - 16, contentTop, contentBottom, scrollOffset, totalContentHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int contentTop = getMainPanelTop();
        int contentBottom = getScrollBottom();
        if (mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int totalContentHeight = getTotalContentHeight();
        int visibleContentHeight = getVisibleContentHeight();
        int maxScroll = Math.max(0, totalContentHeight - visibleContentHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(verticalAmount * 20)));
        updateEntryPositions();
        return true;
    }

    private int getCategoryButtonsBottom() {
        return CATEGORY_Y + CATEGORY_BUTTON_HEIGHT;
    }

    private int getMainPanelTop() {
        return getCategoryButtonsBottom() + CATEGORY_TO_CONTENT_GAP;
    }

    private int getMainPanelBottom() {
        return this.height - MAIN_PANEL_BOTTOM_MARGIN;
    }

    private int getFooterZoneTop() {
        return getMainPanelBottom() - FOOTER_ZONE_HEIGHT;
    }

    private int getDescriptionTop() {
        return getFooterZoneTop() - DESCRIPTION_HEIGHT - DESCRIPTION_TO_BUTTON_GAP;
    }

    private int getScrollBottom() {
        return getDescriptionTop() - SCROLL_TO_DESCRIPTION_GAP;
    }

    private int getVisibleContentHeight() {
        return getScrollBottom() - getMainPanelTop();
    }

    private void clampScrollOffset() {
        int maxScroll = Math.max(0, getTotalContentHeight() - getVisibleContentHeight());
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    private void setWidgetVisible(Object widget, boolean visible) {
        if (widget instanceof PlatformSettingsCard card) {
            card.toggle.visible = visible;
            card.settings.visible = visible;
        } else if (widget instanceof ToggleWithSettingsRow row) {
            row.toggle.visible = visible;
            row.settings.visible = visible;
        } else if (widget instanceof ColorFieldRow row) {
            row.field.visible = visible;
            row.picker.visible = visible;
        } else if (widget instanceof ButtonWidget) {
            ((ButtonWidget) widget).visible = visible;
        } else if (widget instanceof TextFieldWidget) {
            ((TextFieldWidget) widget).visible = visible;
        } else if (widget instanceof ChanceForSpawnSliderWidget) {
            ((ChanceForSpawnSliderWidget) widget).visible = visible;
        } else if (widget instanceof ConfigIntSliderWidget) {
            ((ConfigIntSliderWidget) widget).visible = visible;
        } else if (widget instanceof MessageSoundVolumeSliderWidget) {
            ((MessageSoundVolumeSliderWidget) widget).visible = visible;
        }
    }

    /** A dependent setting remains unavailable until its prerequisite is met. */
    private void setWidgetEnabled(Object widget, boolean enabled) {
        if (widget instanceof PlatformSettingsCard) {
            return;
        } else if (widget instanceof ToggleWithSettingsRow row) {
            row.toggle.active = enabled;
            row.settings.active = enabled;
        } else if (widget instanceof ColorFieldRow row) {
            row.field.setEditable(enabled);
            row.picker.active = enabled;
        } else if (widget instanceof ButtonWidget button) {
            button.active = enabled;
        } else if (widget instanceof TextFieldWidget field) {
            field.setEditable(enabled);
        } else if (widget instanceof ChanceForSpawnSliderWidget slider) {
            slider.active = enabled;
        } else if (widget instanceof ConfigIntSliderWidget slider) {
            slider.active = enabled;
        } else if (widget instanceof MessageSoundVolumeSliderWidget slider) {
            slider.active = enabled;
        }
    }

    private void setWidgetPosition(Object widget, int x, int y) {
        int centeredY = y + (ENTRY_HEIGHT - CONTROL_HEIGHT) / 2;
        if (widget instanceof ButtonWidget) {
            ((ButtonWidget) widget).setPosition(x, centeredY);
        } else if (widget instanceof TextFieldWidget) {
            ((TextFieldWidget) widget).setPosition(x, centeredY);
        } else if (widget instanceof ChanceForSpawnSliderWidget) {
            ((ChanceForSpawnSliderWidget) widget).setPosition(x, centeredY);
        } else if (widget instanceof ConfigIntSliderWidget) {
            ((ConfigIntSliderWidget) widget).setPosition(x, centeredY);
        } else if (widget instanceof MessageSoundVolumeSliderWidget) {
            ((MessageSoundVolumeSliderWidget) widget).setPosition(x, centeredY);
        }
    }

    @Override
    public void close() {
        applyConnectionSettingsFromConfig();
        ConfigManager.getInstance().saveConfig();
        if (this.parent != null) {
            ScreenNavigationCompat.open(this.client, this.parent);
        } else {
            ScreenNavigationCompat.open(this.client, null);
        }
    }

    /** Disconnects disabled sources and connects newly enabled sources if the parser is running. */
    private static void applyConnectionSettingsFromConfig() {
        ChatConnectionManager.getInstance(ConfigManager.getInstance()).reconnectChangedPlatforms();
    }
} 
